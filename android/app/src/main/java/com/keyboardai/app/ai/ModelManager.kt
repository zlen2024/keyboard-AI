package com.keyboardai.app.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Owns model files on disk and the single in-memory [AiEngine]. Both the
 * keyboard (IME) and the setup app talk to this one object; state is exposed as
 * a [StateFlow] so either side can react to download/load progress.
 */
object ModelManager {

    sealed interface State {
        data object Idle : State
        data class Downloading(val fraction: Float) : State
        /** Copying bundled weights out of the APK into private storage (first run). */
        data class Unpacking(val fraction: Float) : State
        data object Loading : State
        data object Ready : State
        data class Error(val message: String) : State
    }

    private const val PREFS = "ai_settings"
    private const val KEY_SELECTED = "selected_model"

    private val engine: AiEngine = LeapAiEngine()
    private val mutex = Mutex()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val isReady: Boolean get() = engine.isLoaded

    fun selectedSpec(context: Context): ModelSpec =
        ModelCatalog.byId(prefs(context).getString(KEY_SELECTED, null))

    fun setSelectedSpec(context: Context, spec: ModelSpec) {
        prefs(context).edit().putString(KEY_SELECTED, spec.id).apply()
    }

    /** True once the weights are present in private storage (extracted or downloaded). */
    fun isReadyOnDisk(context: Context, spec: ModelSpec): Boolean {
        val model = modelFile(context, spec)
        val mmproj = mmprojFile(context, spec)
        return model.exists() && model.length() > 0 &&
            (mmproj == null || (mmproj.exists() && mmproj.length() > 0))
    }

    /** True when this model can be made ready with no network: already on disk, or bundled in the APK. */
    fun isAvailableOffline(context: Context, spec: ModelSpec): Boolean =
        isReadyOnDisk(context, spec) || hasBundledAssets(context, spec)

    /** Make the weights available (extract from assets, or download), then load. Idempotent. */
    suspend fun prepare(context: Context, spec: ModelSpec = selectedSpec(context)) {
        mutex.withLock {
            if (engine.isLoaded) return
            try {
                if (!isReadyOnDisk(context, spec)) {
                    if (hasBundledAssets(context, spec)) {
                        unpackBundled(context, spec)
                    } else {
                        download(context, spec)
                    }
                }
                _state.value = State.Loading
                engine.load(
                    modelPath = modelFile(context, spec).absolutePath,
                    mmprojPath = mmprojFile(context, spec)?.absolutePath,
                    cpuThreads = recommendedThreads(),
                    contextSize = CONTEXT_SIZE,
                )
                _state.value = State.Ready
            } catch (t: Throwable) {
                _state.value = State.Error(t.message ?: "Failed to prepare model")
                throw t
            }
        }
    }

    /** Switch to a different model: unload the old one and load the new. */
    suspend fun switchTo(context: Context, spec: ModelSpec) {
        setSelectedSpec(context, spec)
        mutex.withLock { engine.unload() }
        prepare(context, spec)
    }

    fun engine(): AiEngine = engine

    // ---- bundled assets (offline, shipped in the APK) ----

    /** True when this model's GGUF files are packaged under `assets/<assetDir>/`. */
    fun hasBundledAssets(context: Context, spec: ModelSpec): Boolean {
        val names = runCatching {
            context.applicationContext.assets.list(spec.assetDir)?.toSet().orEmpty()
        }.getOrDefault(emptySet())
        return spec.modelFile in names && (spec.mmprojFile == null || spec.mmprojFile in names)
    }

    /** Copy bundled weights out of the (compressed-free) APK into private storage once. */
    private suspend fun unpackBundled(context: Context, spec: ModelSpec) = withContext(Dispatchers.IO) {
        val total = spec.approxBytes.coerceAtLeast(1)
        var copied = 0L
        _state.value = State.Unpacking(0f)

        fun extract(assetName: String, dest: File) {
            context.applicationContext.assets.open("${spec.assetDir}/$assetName").use { input ->
                val tmp = File(dest.absolutePath + ".part")
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        _state.value = State.Unpacking((copied.toFloat() / total).coerceIn(0f, 0.99f))
                    }
                }
                if (!tmp.renameTo(dest)) error("Could not finalize ${dest.name}")
            }
        }

        modelDir(context, spec).mkdirs()
        extract(spec.modelFile, modelFile(context, spec))
        val mmproj = mmprojFile(context, spec)
        if (spec.mmprojFile != null && mmproj != null) extract(spec.mmprojFile, mmproj)
    }

    // ---- download (optional models not shipped in the APK) ----

    private suspend fun download(context: Context, spec: ModelSpec) = withContext(Dispatchers.IO) {
        val total = spec.approxBytes.coerceAtLeast(1)
        var downloaded = 0L
        _state.value = State.Downloading(0f)

        suspend fun fetch(url: String, dest: File) {
            downloaded += downloadFile(url, dest) { soFarThisFile ->
                val overall = (downloaded + soFarThisFile).toFloat() / total
                _state.value = State.Downloading(overall.coerceIn(0f, 0.99f))
            }
        }

        modelDir(context, spec).mkdirs()
        fetch(spec.modelUrl, modelFile(context, spec))
        val mmprojUrl = spec.mmprojUrl
        val mmproj = mmprojFile(context, spec)
        if (mmprojUrl != null && mmproj != null) fetch(mmprojUrl, mmproj)
    }

    /** Streams [url] to [dest], following HF's cross-host redirects. Returns bytes written. */
    private inline fun downloadFile(url: String, dest: File, onProgress: (Long) -> Unit): Long {
        var current = url
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 60_000
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location == null || ++redirects > 5) error("Too many redirects for $url")
                current = location
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                error("Download failed ($code) for $url")
            }
            val tmp = File(dest.absolutePath + ".part")
            var written = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written)
                    }
                }
            }
            conn.disconnect()
            if (!tmp.renameTo(dest)) error("Could not finalize ${dest.name}")
            return written
        }
    }

    // ---- paths & helpers ----

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun modelDir(context: Context, spec: ModelSpec) =
        File(context.applicationContext.filesDir, "models/${spec.id}")

    private fun modelFile(context: Context, spec: ModelSpec) =
        File(modelDir(context, spec), spec.modelFile)

    private fun mmprojFile(context: Context, spec: ModelSpec): File? =
        spec.mmprojFile?.let { File(modelDir(context, spec), it) }

    private fun recommendedThreads(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    private const val CONTEXT_SIZE = 4096
}
