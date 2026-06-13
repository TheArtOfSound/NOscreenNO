<div align="center">

# noscreeno

**Keep everything private on Android.** &nbsp;·&nbsp; a [Qira](https://imagineqira.com) product

A local-only encryption toolkit: encrypt text & files, store an encrypted vault,
block screenshots, and drop a privacy mask over your screen. No account. No server. No telemetry.

[![Android CI](https://github.com/TheArtOfSound/NOscreenNO/actions/workflows/android-ci.yml/badge.svg)](https://github.com/TheArtOfSound/NOscreenNO/actions/workflows/android-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-34d8f0.svg)](LICENSE)

### [⬇ Download the APK](https://github.com/TheArtOfSound/NOscreenNO/releases/latest/download/NOscreenNO.apk) &nbsp;•&nbsp; [🌐 noscreeno.imagineqira.com](https://noscreeno.imagineqira.com)

</div>

---

## What it does

| Feature | What happens |
| --- | --- |
| 🔑 **Your key** | You pick a passphrase (8+ chars). Everything is derived from it. No reset, no recovery, no backdoor. |
| 📝 **Text encryptor** | Encrypt/decrypt any text — notes, keys, seed phrases, messages — and copy or share the ciphertext. |
| 🗄️ **Local vault** | Save one encrypted note on-device. It never leaves the phone. |
| 📁 **File locker** | Encrypt/decrypt files you pick through Android's file picker into `.qev` files. |
| 🛡️ **Visual shield** | A touch-through privacy mask over the screen for shoulder-surfing / camera protection. |
| 🔒 **Secure window** | The app sets `FLAG_SECURE`, so its own screen can't be screenshotted or screen-recorded. |

## How the encryption works

- **AES-256-GCM** authenticated encryption (confidentiality + tamper detection).
- Key derived with **PBKDF2-HMAC-SHA256, 310,000 iterations**.
- A fresh random **salt (16 bytes)** and **IV (12 bytes)** are generated for every single payload, so encrypting the same text twice never produces the same output.
- Decryption with the wrong passphrase, or a payload that's been altered by even one character, fails loudly instead of returning garbage.

It is honest about its limits: it can encrypt the text and files **you choose** and lock down its own window, but it cannot silently encrypt every other app on the phone — that requires root or device-owner control, which is an Android security boundary, not a missing feature.

## Install

1. **[Download `NOscreenNO.apk`](https://github.com/TheArtOfSound/NOscreenNO/releases/latest/download/NOscreenNO.apk)** on your Android phone (Android 8.0 / API 26 or newer).
2. Open it. Android will ask to **allow installing from this source** — approve it (Settings → *Install unknown apps*).
3. Launch **noscreeno**, set a passphrase you'll remember, and start encrypting.

> Distributed outside the Play Store, so you install it yourself. The APK is built and signed automatically by GitHub Actions ([release workflow](.github/workflows/release.yml)) — you can audit exactly how every release is produced.

## Build it yourself

Requires JDK 17+ and the Android SDK (Android Studio provides both).

```bash
git clone https://github.com/TheArtOfSound/NOscreenNO.git
cd NOscreenNO
./gradlew assembleDebug        # build a debug APK
./gradlew testDebugUnitTest    # run the crypto unit tests
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

```
app/src/main/java/com/qevcrypt/app/
  MainActivity.java            # the whole UI (built programmatically)
  QevCrypto.java               # AES-GCM + PBKDF2 encryption engine
  PrivacyOverlayService.java   # the visual privacy shield (foreground service)
app/src/test/java/...          # JVM unit tests for the encryption engine
site/                          # the one-page website (deployed to GitHub Pages)
.github/workflows/             # CI, signed-release, and Pages-deploy automation
```

## License

[MIT](LICENSE) © 2026 Qira LLC · [imagineqira.com](https://imagineqira.com)
