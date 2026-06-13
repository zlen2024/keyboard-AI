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

    fun isDownloaded(context: Context, spec: ModelSpec): Boolean {
        val model = modelFile(context, spec)
        val mmproj = mmprojFile(context, spec)
        return model.exists() && model.length() > 0 &&
            (mmproj == null || (mmproj.exists() && mmproj.length() > 0))
    }

    /** Download (if needed) and load the model into memory. Idempotent. */
    suspend fun prepare(context: Context, spec: ModelSpec = selectedSpec(context)) {
        mutex.withLock {
            if (engine.isLoaded) return
            try {
                if (!isDownloaded(context, spec)) {
                    download(context, spec)
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

    // ---- download ----

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
