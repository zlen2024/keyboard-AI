package com.keyboardai.app.ai

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Owns the single model's files on disk and the in-memory [AiEngine]. The
 * keyboard (IME), the launcher and the setup screen all talk to this one
 * object; state is exposed as a [StateFlow] so any of them can react to
 * download/load progress.
 */
object ModelManager {

    sealed interface State {
        data object Idle : State
        data class Downloading(val fraction: Float) : State
        /** Copying pre-bundled weights out of the APK into private storage (first run). */
        data class Unpacking(val fraction: Float) : State
        data object Loading : State
        data object Ready : State
        data class Error(val message: String) : State
    }

    /** The one model the app uses. */
    val spec: ModelSpec get() = ModelCatalog.MODEL

    private val engine: AiEngine = LeapAiEngine()
    private val mutex = Mutex()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prepareJob: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val isReady: Boolean get() = engine.isLoaded

    /** True once the weights are present in private storage (downloaded or unpacked). */
    fun isReadyOnDisk(context: Context): Boolean {
        val model = modelFile(context)
        val mmproj = mmprojFile(context)
        return model.exists() && model.length() > 0 && mmproj.exists() && mmproj.length() > 0
    }

    /** True when the model can be made ready with no network: already on disk, or bundled in the APK. */
    fun isAvailableOffline(context: Context): Boolean =
        isReadyOnDisk(context) || hasBundledAssets(context)

    /**
     * Kick off download + load on a long-lived app scope (survives the launcher
     * Activity being closed), so the model is ready when the keyboard needs it.
     * Idempotent: a no-op if already loaded or already in progress.
     */
    fun startPreparing(context: Context) {
        if (isReady) return
        if (prepareJob?.isActive == true) return
        val app = context.applicationContext
        prepareJob = managerScope.launch { runCatching { prepare(app) } }
    }

    /** Make the weights available (extract from assets, or download), then load. Idempotent. */
    suspend fun prepare(context: Context) {
        mutex.withLock {
            if (engine.isLoaded) {
                _state.value = State.Ready
                return
            }
            try {
                if (!isReadyOnDisk(context)) {
                    if (hasBundledAssets(context)) unpackBundled(context) else download(context)
                }
                _state.value = State.Loading
                engine.load(
                    modelPath = modelFile(context).absolutePath,
                    mmprojPath = mmprojFile(context).absolutePath,
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

    fun engine(): AiEngine = engine

    // ---- pre-bundled assets (optional offline build) ----

    /** True when the model's GGUF files are packaged under `assets/<assetDir>/`. */
    fun hasBundledAssets(context: Context): Boolean {
        val names = runCatching {
            context.applicationContext.assets.list(spec.assetDir)?.toSet().orEmpty()
        }.getOrDefault(emptySet())
        return spec.modelFile in names && spec.mmprojFile in names
    }

    private suspend fun unpackBundled(context: Context) = withContext(Dispatchers.IO) {
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

        modelDir(context).mkdirs()
        extract(spec.modelFile, modelFile(context))
        extract(spec.mmprojFile, mmprojFile(context))
    }

    // ---- download ----

    private suspend fun download(context: Context) = withContext(Dispatchers.IO) {
        val total = spec.approxBytes.coerceAtLeast(1)
        var downloaded = 0L
        _state.value = State.Downloading(0f)

        suspend fun fetch(url: String, dest: File) {
            downloaded += downloadFile(url, dest) { soFarThisFile ->
                val overall = (downloaded + soFarThisFile).toFloat() / total
                _state.value = State.Downloading(overall.coerceIn(0f, 0.99f))
            }
        }

        // Ask the repo what it actually contains rather than trusting hard-coded
        // file names (which drift when a model is requantized or renamed).
        val (modelRemote, mmprojRemote) = resolveRemoteFiles()

        modelDir(context).mkdirs()
        fetch(hfResolveUrl(spec.repo, modelRemote), modelFile(context))
        fetch(hfResolveUrl(spec.repo, mmprojRemote), mmprojFile(context))
    }

    private fun hfResolveUrl(repo: String, rfilename: String) =
        "https://huggingface.co/$repo/resolve/main/$rfilename?download=true"

    /**
     * Resolves the real `(model, mmproj)` file names in the repo via the Hugging
     * Face API, picking the preferred quant. Falls back to the declared names if
     * the listing can't be fetched.
     */
    private fun resolveRemoteFiles(): Pair<String, String> {
        val ggufs = listRepoFiles(spec.repo).filter { it.endsWith(".gguf", ignoreCase = true) }
        if (ggufs.isEmpty()) return spec.modelFile to spec.mmprojFile

        val (mmprojFiles, modelFiles) = ggufs.partition {
            it.substringAfterLast('/').startsWith("mmproj", ignoreCase = true)
        }
        val model = MODEL_QUANT_PREFERENCE.firstNotNullOfOrNull { q ->
            modelFiles.firstOrNull { it.contains(q, ignoreCase = true) }
        } ?: modelFiles.firstOrNull() ?: spec.modelFile

        val mmproj = MMPROJ_QUANT_PREFERENCE.firstNotNullOfOrNull { q ->
            mmprojFiles.firstOrNull { it.contains(q, ignoreCase = true) }
        } ?: mmprojFiles.firstOrNull() ?: spec.mmprojFile

        return model to mmproj
    }

    /** The `rfilename` of every file in a public HF repo (empty on any failure). */
    private fun listRepoFiles(repo: String): List<String> = runCatching {
        val body = httpGetText("https://huggingface.co/api/models/$repo") ?: return emptyList()
        val siblings = JSONObject(body).optJSONArray("siblings") ?: return emptyList()
        buildList {
            for (i in 0 until siblings.length()) {
                siblings.optJSONObject(i)?.optString("rfilename")
                    ?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }.getOrDefault(emptyList())

    private fun httpGetText(url: String): String? {
        var current = url
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location == null || ++redirects > 5) return null
                current = location
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return null
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
                .also { conn.disconnect() }
        }
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

    private fun modelDir(context: Context) =
        File(context.applicationContext.filesDir, "models/${spec.id}")

    private fun modelFile(context: Context) = File(modelDir(context), spec.modelFile)

    private fun mmprojFile(context: Context) = File(modelDir(context), spec.mmprojFile)

    private fun recommendedThreads(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    private const val CONTEXT_SIZE = 4096

    /** Quant preference for the main model: balance of size/quality for on-device. */
    private val MODEL_QUANT_PREFERENCE =
        listOf("Q4_K_M", "Q4_K_S", "Q4_0", "Q5_K_M", "Q5_0", "Q6_K", "Q8_0", "F16")

    /** The vision projector is small; prefer higher precision. */
    private val MMPROJ_QUANT_PREFERENCE = listOf("Q8_0", "F16", "Q6_K")
}
