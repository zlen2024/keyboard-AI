# Keyboard AI

A custom keyboard for **Android and iOS**. Once installed and enabled, it replaces the
system keyboard and works inside any app on the device.

## Why native (not React Native / Flutter)?

A system-wide keyboard is not a normal app — it runs as an OS *extension*:

- **Android** requires an [`InputMethodService`](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method) (IME).
- **iOS** requires a [Keyboard Extension](https://developer.apple.com/documentation/uikit/keyboards_and_input/creating_a_custom_keyboard) target.

iOS keyboard extensions run under a strict memory budget (~60–70 MB) that the React
Native and Flutter runtimes exceed, and Android IMEs can't reliably host their view
trees. Every major third-party keyboard (Gboard, SwiftKey, Grammarly) ships native
keyboard code, which is what this repo does:

| Platform | Directory | Language | Keyboard mechanism |
|----------|-----------|----------|--------------------|
| Android  | `android/` | Kotlin   | `InputMethodService` + custom canvas-drawn view |
| iOS      | `ios/`     | Swift    | `UIInputViewController` keyboard extension |

Both implementations share the same design: QWERTY letters, two symbol pages,
shift with double-tap caps lock, hold-to-repeat delete, auto-capitalization,
context-aware enter key, and a globe key to switch keyboards.

## Features

- 🔤 QWERTY layout with shift / double-tap caps lock
- 🔢 Symbols layer with a second page (`=\<` / `?123`)
- ⌫ Hold-to-repeat backspace
- ✨ Auto-capitalization at sentence starts (respects each text field's settings)
- ⏎ Enter key adapts to the field (Search / Send / Go / Next / Done)
- 🌐 Globe key to switch back to other keyboards
- 🔊 Key click feedback (haptic on Android, audio on iOS)
- 🌙 Dark theme
- 🔒 No network access, no data collection (iOS "Allow Full Access" is **not** requested)

## Android

### Build & install

```bash
cd android
./gradlew assembleDebug          # or open in Android Studio
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Enable the keyboard

1. Open the **Keyboard AI** app.
2. Tap **Open keyboard settings** and enable *Keyboard AI*.
3. Tap **Switch keyboard** and select *Keyboard AI*.
4. Type in the test field — or any other app.

## iOS

### Build & install

1. Open `ios/KeyboardAI.xcodeproj` in Xcode.
2. Select your development team under *Signing & Capabilities* for **both** targets
   (`KeyboardAI` and `KeyboardExtension`).
3. Run the **KeyboardAI** scheme on a device or simulator.

### Enable the keyboard

1. Go to **Settings → General → Keyboard → Keyboards → Add New Keyboard…**
2. Select **Keyboard AI**.
3. In any app, tap a text field, then long-press the 🌐 globe key and pick *Keyboard AI*.

## Download page (`web/`)

A small static web app that serves a one-click download of the Android APK.
The APK lives at `web/assets/keyboard-ai.apk`.

The **Build APK and deploy download page** GitHub Actions workflow runs
whenever the Android or web code changes (or manually from the Actions tab) and:

1. builds the debug APK and commits it to `web/assets/keyboard-ai.apk`, then
2. deploys `web/` (with the fresh APK) to **GitHub Pages**.

Note: on free GitHub plans, Pages only serves **public** repositories.

To run it locally instead:

```bash
cd web
node server.js        # http://localhost:3000
```

It is plain HTML/CSS/JS, so it also deploys as-is to any other static host
(Netlify, Vercel…) — `server.js` is just a convenience for serving it
locally with the correct APK MIME type.

## Project structure

```
android/
  app/src/main/java/com/keyboardai/app/
    MainActivity.kt                  # setup / onboarding screen
    ime/KeyboardAIService.kt         # the IME (input logic)
    ime/KeyboardView.kt              # canvas-drawn keyboard UI
    ime/KeyboardLayouts.kt           # key layout definitions
ios/
  KeyboardAI/                        # container app (setup instructions)
  KeyboardExtension/
    KeyboardViewController.swift     # the extension (input logic)
    KeyboardView.swift               # button-based keyboard UI
    KeyboardLayout.swift             # key layout definitions
```

## Roadmap ideas

- Word suggestions / autocorrect
- Long-press accent popups (é, ü, …)
- Themes & key-height settings shared via the container app
- AI-assisted text rewriting (would require network access / Full Access on iOS)
