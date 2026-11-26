# Contributing to DashFlow

Thank you for your interest in contributing to **DashFlow**! This launcher is built for low-spec Android head units (1GB–2GB RAM, Android 8.0+), so every PR must respect our core performance and zero-bloat principles.

---

## Core Architecture Principles

1. **Zero-Bloat Discipline**: Keep `app/build.gradle` free of heavy UI frameworks, analytics, ads, or Google Play Services dependencies.
2. **Native Views Only**: Use pure Kotlin, XML layouts, `ViewBinding`, and standard AndroidX Core APIs. Avoid Jetpack Compose, WebViews, or background polling services that trigger Android's Low Memory Killer (LMK).
3. **Memory-Safe Assets**: Any user-selected images or drawables must be downsampled before decoding (`inJustDecodeBounds` / `inSampleSize`) and kept lightweight.
4. **Landscape & Touch First**: Design for 7"–10" car displays (`1024x600`, `1280x720`, and tablets) with large, blind-touch-friendly tap targets.

---

## Getting Started

1. Fork the repository and clone your fork:
   ```bash
   git clone https://github.com/<your-username>/DashFlow.git
   ```
2. Open the project in Android Studio (Arctic Fox or newer, JDK 17+).
3. Build and test locally via `./gradlew assembleDebug` or run directly on your head unit / emulator.
4. Create a feature branch (`git checkout -b feat/your-feature`), commit clean changes, and open a Pull Request against `main`.

---

## Good First Issues / Roadmap Ideas

- [ ] Add dedicated `layout-w1280dp` / `layout-sw600dp` tuning for ultrawide 10.25"–12.3" car displays
- [ ] Add optional Split-Screen launch mode (`FLAG_ACTIVITY_LAUNCH_ADJACENT`) for compatible Android 8.1+ ROMs
- [ ] Support custom third-party icon packs in the App Drawer
- [ ] Add localization (`strings.xml`) for additional languages
- [ ] Add unit tests for `WeatherService` geocoding and solar day/night calculation
