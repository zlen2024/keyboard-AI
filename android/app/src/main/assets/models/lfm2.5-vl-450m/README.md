# Optional: pre-bundle the 450M model

By default the app **downloads** `LFM2.5-VL-450M` from Hugging Face the first time
the assistant is used, then runs fully offline. This keeps the APK small enough to
push to git and serve from the GitHub Pages download page (GitHub blocks pushed
files >100 MB and Pages won't serve files >100 MB, and the weights are ~330 MB).

This folder is an **optional, advanced** escape hatch: if you drop the two GGUF
files here, `ModelManager` extracts them on first run and **skips the download**
(useful for fully-offline side-loaded builds distributed via GitHub Releases or
`adb install`, not via the Pages page).

```
LFM2.5-VL-450M-Q4_K_M.gguf      # model weights (~330 MB)
mmproj-LFM2.5-VL-450M-Q8_0.gguf # vision projector (reads screenshots / forms)
```

They are **git-ignored** (see `.gitignore`) — never commit them. To fetch them
locally, from the repo root:

```bash
REPO=LiquidAI/LFM2.5-VL-450M-GGUF
DIR=android/app/src/main/assets/models/lfm2.5-vl-450m
curl -L -o "$DIR/LFM2.5-VL-450M-Q4_K_M.gguf" \
  "https://huggingface.co/$REPO/resolve/main/LFM2.5-VL-450M-Q4_K_M.gguf?download=true"
curl -L -o "$DIR/mmproj-LFM2.5-VL-450M-Q8_0.gguf" \
  "https://huggingface.co/$REPO/resolve/main/mmproj-LFM2.5-VL-450M-Q8_0.gguf?download=true"
```

The exact file names are defined in `ModelCatalog.kt` — keep them in sync if
Liquid renames a release.
</content>
