# ملټي کیبورډ (Multi Keyboard)

A modern multilingual Android keyboard (IME) inspired by classic multilingual
keyboards — built from scratch in Kotlin for current Android versions
(Android 5.0 – 15, arm/arm64/x86 — pure Kotlin, no native libs).

## ژبې / Languages

- پښتو (Pashto) — full layout with ټ ډ ړ ږ ښ ګ ڼ څ ځ ې ۍ and diacritics
- دري / فارسی (Dari/Farsi)
- العربية (Arabic)
- اردو (Urdu)
- English (QWERTY)

## Features

- Shift layer per language (the small hint character on each key)
- Long-press popups with slide-to-select alternates and diacritics
- Swipe the space bar left/right to switch language; long-press for the menu
- Arrow-keys row (▲ ▼ ◀ ▶) — toggleable
- Word suggestions that learn as you type (fully on-device)
- Double-space → period, auto-caps for English
- 3 themes (Dark / Light / AMOLED), adjustable key height & font size
- Localized digits (۰۱۲۳ / ٠١٢٣ / 0123) and symbols pages
- Key preview, vibration, sound — all configurable
- **No INTERNET permission** — completely offline and private

## Building

CI builds the APK on every push (see `.github/workflows/build-apk.yml`) and
commits it to `.build-outputs/app-debug.apk`. To build locally:

```
base64 -d debug.keystore.base64 > debug.keystore
./gradlew :app:assembleDebug
```

Requires JDK 17 and the Android SDK (compileSdk 35).
