# Bundled model: LFM2.5-VL 450M

The keyboard's default assistant is **LFM2.5-VL-450M**, shipped *inside* the APK so
it powers the keyboard immediately with no download and no network access.

At build time these two GGUF files must sit next to this README:

```
LFM2.5-VL-450M-Q4_K_M.gguf      # the model weights (~330 MB)
mmproj-LFM2.5-VL-450M-Q8_0.gguf # the vision projector (reads screenshots / forms)
```

They are **not** committed to git (binaries are git-ignored — see `.gitignore`).
Instead they are fetched into this folder right before the build:

* **CI** — the `Build APK and deploy download page` workflow downloads them from
  Hugging Face into this directory, so the published APK is genuinely bundled.
* **Locally** — run, from the repo root:

  ```bash
  REPO=LiquidAI/LFM2.5-VL-450M-GGUF
  DIR=android/app/src/main/assets/models/lfm2.5-vl-450m
  curl -L -o "$DIR/LFM2.5-VL-450M-Q4_K_M.gguf" \
    "https://huggingface.co/$REPO/resolve/main/LFM2.5-VL-450M-Q4_K_M.gguf?download=true"
  curl -L -o "$DIR/mmproj-LFM2.5-VL-450M-Q8_0.gguf" \
    "https://huggingface.co/$REPO/resolve/main/mmproj-LFM2.5-VL-450M-Q8_0.gguf?download=true"
  ```

If the files are absent at build time the app still works: `ModelManager` falls
back to downloading the model on first use (the same path the optional 1.6B model
always uses). The exact file names are defined in `ModelCatalog.kt` — keep them in
sync if Liquid renames a release.
