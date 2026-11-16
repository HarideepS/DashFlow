# Swift OEM Car Launcher (Android 8.1+ & 1GB RAM Optimized)

<div align="center">

[![Vibecoded with Google Antigravity](https://img.shields.io/badge/Vibecoded%20with-Google%20Antigravity-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://antigravity.google)
[![Platform](https://img.shields.io/badge/Platform-Android%208.1%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![RAM Footprint](https://img.shields.io/badge/Resident%20RAM-~15--20MB-FF6F00?style=for-the-badge)](#)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%2F%20XML-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)

A lightweight, high-performance, OEM-styled Android Car Launcher designed specifically for low-resource Android head units (such as the 1GB RAM Topway TS7 running Android 8.1) as well as modern Samsung Galaxy devices (M30) repurposed as car displays.

</div>

---

## ⚡ Vibecoded with Google Antigravity

This entire project was **vibecoded end-to-end using Google Antigravity**, transforming natural language vision into a battle-tested, ultra-optimized Android automotive cockpit:

- **Constraint-First Engineering**: Designed and tuned to triumph over aggressive Low Memory Killer (LMK) limits on entry-level Android 8.1 head units with only 1GB RAM.
- **Pure Native Performance**: Zero webview bloat, zero heavy Compose overhead—pure native Android Kotlin, `ConstraintLayout`, and vector graphics maintaining an astonishingly low ~15–20MB resident memory footprint.
- **Iterative AI Polish**: From custom OEM UI theming (Arctic White / Midnight Black Swift dual-tone) to resilient offline weather caching (Open-Meteo) and 4-tier brightness cycles, every layer was vibe-crafted collaboratively with Antigravity.

---

## 🚗 Key Design & Performance Highlights

* **Ultra-Low Memory Footprint (~15–20MB Resident RAM)**: 
  * Built using pure native Android Views (`ConstraintLayout` + XML drawables) instead of memory-heavy engines (Flutter, Compose, or KLWP).
  * Prevents Android 8.1's Low Memory Killer (LMK) from killing the launcher when Open Headunit or ZLink streams video.
* **Pixel-Accurate OEM Layout**:
  * Clean, distraction-free cockpit aesthetic.
  * Live digital clock (`TextClock`) with 0% CPU consumption.
  * Live weather pill with automatic background sync (Open-Meteo API, no API key required) and offline cache fallback.
  * Center hero render of the Maruti Suzuki Swift (dual-tone Arctic White & Midnight Black roof).
* **Direct Driving Cards**:
  1. **Android Auto (Blue)**: Default maps to `io.github.slashmax.openheadunit` (Wi-Fi Direct launch for Pixel 6a).
  2. **Apple CarPlay (Green)**: Default maps to `com.zjinnova.zlink` (ZLink wireless/wired CarPlay).
  3. **YouTube (Red)**: Maps to YouTube / ReVanced / NewPipe.
  4. **All Apps (Dark Glass)**: Opens a sleek in-app grid drawer with instantaneous search.
* **Smart App Re-Binding**:
  * **Long-press** any of the 4 cards at any time to open the built-in app picker and assign it to any installed application on your unit (ideal if your TS7 uses a custom ZLink package name like `cn.manstep.phonemirrorbox` or `com.syu.zlink`).
* **Quick Controls Bar**:
  * **Wi-Fi**: Instant state toggle or long-press for Wi-Fi settings.
  * **Bluetooth**: Instant shortcut to BT pairing.
  * **Screen Brightness**: 4-stage brightness cycle (`100% Day` -> `50% Dusk` -> `15% Night Mode` -> `Auto`).
  * **Settings**: Direct launch of Android Settings.

---

## 🛠️ How to Build & Install

### Option A: Open with Android Studio
1. Open Android Studio.
2. Select **Open** and choose this project folder.
3. Let Gradle sync and click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
4. The generated APK will be in:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Option B: Sideloading onto Samsung M30 or TS7 Head Unit
1. **Via ADB (USB or Wi-Fi)**:
   ```bash
   adb install -r app-debug.apk
   ```
2. **Via USB Flash Drive (TS7 Head Unit)**:
   * Copy `app-debug.apk` onto a standard FAT32 USB flash drive.
   * Plug it into one of the TS7's rear USB ports.
   * Open the built-in **"File Manager"** or **"ApkInstaller"** on the TS7.
   * Tap the APK and select **Install**.

---

## ⚙️ Setting as Default Home Screen

1. On your M30 or TS7, open **Settings**.
2. Go to **Apps & notifications** -> **Default apps** -> **Home app**.
3. Select **Swift Car Launcher**.
4. Press the Home button on your head unit / navigation bar; it will now permanently open your custom OEM cockpit!
