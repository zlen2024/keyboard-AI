package com.keyboardai.app.ai

/**
 * The set of on-device models the user can download. Each model is one or two
 * GGUF files fetched from a public Hugging Face repo and loaded by the LEAP
 * SDK via [com.keyboardai.app.ai.LeapAiEngine].
 *
 * NOTE: the exact file names below follow Liquid's standard GGUF naming. If a
 * download 404s, correct the `modelFile` / `mmprojFile` here — this is the one
 * place URLs are defined. (Hugging Face is unreachable from the build sandbox,
 * so these are verified on-device / in CI rather than at author time.)
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val description: String,
    /** Hugging Face repo, e.g. "LiquidAI/LFM2-VL-450M-GGUF". */
    val repo: String,
    val modelFile: String,
    /** Multimodal projector file; null for text-only models. */
    val mmprojFile: String?,
    /** Approximate on-disk download size, for the UI. */
    val approxBytes: Long,
    /**
     * True when the GGUF weights are shipped inside the APK under
     * `assets/models/<id>/`. Such a model loads with zero network access;
     * [ModelManager] extracts it from assets instead of downloading.
     */
    val bundled: Boolean = false,
) {
    val isMultimodal: Boolean get() = mmprojFile != null

    /** Folder under `assets/` that holds the bundled GGUF files for this model. */
    val assetDir: String get() = "models/$id"

    private fun url(file: String) = "https://huggingface.co/$repo/resolve/main/$file?download=true"

    val modelUrl: String get() = url(modelFile)
    val mmprojUrl: String? get() = mmprojFile?.let { url(it) }
}

object ModelCatalog {

    /**
     * Default — Liquid's newest-generation LFM2.5-VL 1.6B vision+text model.
     * Downloaded once from Hugging Face on first use, then runs fully offline.
     * The exact GGUF file names are resolved from the repo at download time (see
     * [ModelManager]), so the names below are only a preferred hint — a repo
     * rename or requant won't break the download.
     */
    val LFM2_5_VL_1_6B = ModelSpec(
        id = "lfm2.5-vl-1.6b",
        displayName = "LFM2.5-VL 1.6B (quality)",
        description = "Newest-gen, ~700 MB–1.2 GB. Strongest writing & vision; needs a recent high-RAM phone.",
        repo = "LiquidAI/LFM2.5-VL-1.6B-GGUF",
        modelFile = "LFM2.5-VL-1.6B-Q4_0.gguf",
        mmprojFile = "mmproj-LFM2.5-VL-1.6B-Q8_0.gguf",
        approxBytes = 950_000_000L,
    )

    /** Lighter, faster option for low-RAM phones / quicker first-token. */
    val LFM2_VL_450M = ModelSpec(
        id = "lfm2-vl-450m",
        displayName = "LFM2-VL 450M (fast)",
        description = "Liquid's edge vision model, ~330 MB. Best speed and battery; understands images.",
        repo = "LiquidAI/LFM2-VL-450M-GGUF",
        modelFile = "LFM2-VL-450M-Q4_0.gguf",
        mmprojFile = "mmproj-LFM2-VL-450M-Q8_0.gguf",
        approxBytes = 340_000_000L,
    )

    val all = listOf(LFM2_5_VL_1_6B, LFM2_VL_450M)
    val default = LFM2_5_VL_1_6B

    fun byId(id: String?): ModelSpec = all.firstOrNull { it.id == id } ?: default
}
