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
    /** Hugging Face repo, e.g. "LiquidAI/LFM2.5-VL-450M-GGUF". */
    val repo: String,
    val modelFile: String,
    /** Multimodal projector file; null for text-only models. */
    val mmprojFile: String?,
    /** Approximate on-disk download size, for the UI. */
    val approxBytes: Long,
) {
    val isMultimodal: Boolean get() = mmprojFile != null

    private fun url(file: String) = "https://huggingface.co/$repo/resolve/main/$file?download=true"

    val modelUrl: String get() = url(modelFile)
    val mmprojUrl: String? get() = mmprojFile?.let { url(it) }
}

object ModelCatalog {

    /** Fast, light default — newest-generation 450M vision+text model. */
    val LFM2_5_VL_450M = ModelSpec(
        id = "lfm2.5-vl-450m",
        displayName = "LFM2.5-VL 450M (fast)",
        description = "Newest-gen, ~330 MB. Best speed and battery; understands images.",
        repo = "LiquidAI/LFM2.5-VL-450M-GGUF",
        modelFile = "LFM2.5-VL-450M-Q4_K_M.gguf",
        mmprojFile = "mmproj-LFM2.5-VL-450M-Q8_0.gguf",
        approxBytes = 350_000_000L,
    )

    /** Heavier, higher-quality option for stronger long-form drafting. */
    val LFM2_5_VL_1_6B = ModelSpec(
        id = "lfm2.5-vl-1.6b",
        displayName = "LFM2.5-VL 1.6B (quality)",
        description = "~1 GB. Stronger writing; needs a recent high-RAM phone.",
        repo = "LiquidAI/LFM2.5-VL-1.6B-GGUF",
        modelFile = "LFM2.5-VL-1.6B-Q4_K_M.gguf",
        mmprojFile = "mmproj-LFM2.5-VL-1.6B-Q8_0.gguf",
        approxBytes = 1_100_000_000L,
    )

    val all = listOf(LFM2_5_VL_450M, LFM2_5_VL_1_6B)
    val default = LFM2_5_VL_450M

    fun byId(id: String?): ModelSpec = all.firstOrNull { it.id == id } ?: default
}
