# Optional: pre-bundle the model

By default the app **downloads** `LFM2.5-VL-1.6B` from Hugging Face on first
launch, then runs fully offline. The exact GGUF file names are resolved from the
repo at download time (see `ModelManager.resolveRemoteFiles`), so a requant or
rename on Hugging Face won't break the download.

Bundling the ~1 GB weights in the APK is **not** done by default (GitHub blocks
pushed files >100 MB and Pages won't serve files >100 MB). This folder is an
**optional, advanced** escape hatch: drop the two GGUF files here and
`ModelManager` extracts them on first run and **skips the download** (for
fully-offline side-loaded builds distributed via GitHub Releases or
`adb install`). The names must match `ModelCatalog.kt`:

```
LFM2.5-VL-1.6B-Q4_0.gguf          # model weights (~0.7 GB)
mmproj-LFM2.5-VL-1.6B-Q8_0.gguf   # vision projector
```

They are **git-ignored** (see `.gitignore`) — never commit them. To fetch them
locally, from the repo root:

```bash
REPO=LiquidAI/LFM2.5-VL-1.6B-GGUF
DIR=android/app/src/main/assets/models/lfm2.5-vl-1.6b
curl -L -o "$DIR/LFM2.5-VL-1.6B-Q4_0.gguf" \
  "https://huggingface.co/$REPO/resolve/main/LFM2.5-VL-1.6B-Q4_0.gguf?download=true"
curl -L -o "$DIR/mmproj-LFM2.5-VL-1.6B-Q8_0.gguf" \
  "https://huggingface.co/$REPO/resolve/main/mmproj-LFM2.5-VL-1.6B-Q8_0.gguf?download=true"
```
