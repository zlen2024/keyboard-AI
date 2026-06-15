package com.keyboardai.app.ai

/**
 * The single on-device model the app uses: Liquid's newest-generation
 * **LFM2.5-VL 1.6B** vision+text model. It is downloaded once from the public
 * Hugging Face GGUF repo on first launch, then runs fully offline.
 *
 * The exact GGUF file names are resolved from the repo at download time (see
 * [ModelManager]), so the names below are only a preferred hint — a repo rename
 * or requant won't break the download.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val description: String,
    /** Hugging Face repo, e.g. "LiquidAI/LFM2.5-VL-1.6B-GGUF". */
    val repo: String,
    val modelFile: String,
    /** Multimodal projector file (vision). */
    val mmprojFile: String,
    /** Approximate on-disk download size, for the UI. */
    val approxBytes: Long,
) {
    /** Folder under `assets/` that could hold pre-bundled GGUF files for this model. */
    val assetDir: String get() = "models/$id"
}

object ModelCatalog {

    /** The one and only model: LFM2.5-VL 1.6B (vision + text). */
    val MODEL = ModelSpec(
        id = "lfm2.5-vl-1.6b",
        displayName = "LFM2.5-VL 1.6B",
        description = "Liquid's newest-gen on-device vision-language model. ~0.7–1.2 GB.",
        repo = "LiquidAI/LFM2.5-VL-1.6B-GGUF",
        modelFile = "LFM2.5-VL-1.6B-Q4_0.gguf",
        mmprojFile = "mmproj-LFM2.5-VL-1.6B-Q8_0.gguf",
        approxBytes = 950_000_000L,
    )
}
