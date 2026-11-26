# DashFlow — Lightweight OEM Car Launcher (Android 8.0+ & 1GB–2GB RAM Optimized)

<div align="center">

[![Download Latest APK](https://img.shields.io/badge/Download-Latest%20APK-00C853?style=for-the-badge&logo=android&logoColor=white)](https://github.com/HarideepS/DashFlow/releases/latest/download/DashFlow-latest.apk)
[![Releases](https://img.shields.io/github/v/release/HarideepS/DashFlow?style=for-the-badge&label=Release&color=4D7DF0)](https://github.com/HarideepS/DashFlow/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)
[![Android CI](https://github.com/HarideepS/DashFlow/actions/workflows/android-build.yml/badge.svg)](https://github.com/HarideepS/DashFlow/actions/workflows/android-build.yml)

<br/><br/>

<img src="assets/dashboard-preview.jpg" alt="DashFlow Cockpit Dashboard Preview" width="100%" />

<br/><br/>

**DashFlow** is a lightweight, zero-bloat, OEM-styled Android Car Launcher built specifically for low-resource aftermarket Android head units (1GB–2GB RAM running Android 8.0+) as well as your tablet/phone repurposed as a car display — **just download the APK, install, and customize for your car!**

</div>

---

## 📥 Download & Run (No Android Studio Needed)

### Step 1: Download the APK
* 👉 **[Click here to download `DashFlow-latest.apk`](https://github.com/HarideepS/DashFlow/releases/latest/download/DashFlow-latest.apk)**
* *(Or browse all versions & release notes on the **[GitHub Releases](https://github.com/HarideepS/DashFlow/releases)** page.)*

### Step 2: Install on Your Head Unit or Tablet/Phone
If you downloaded directly in the browser on your head unit/tablet/phone, just tap the `.apk` notification and select **Install**. Otherwise, transfer it from your phone or PC using any method below:

* **Option A — Wireless via LocalSend (Recommended):**
  1. Install **[LocalSend](https://localsend.org/)** on both your phone/PC and your Android head unit (or tablet/phone).
  2. Connect both devices to the same Wi-Fi network or mobile hotspot.
  3. Send the downloaded `.apk` via LocalSend, then tap to **Install**.
* **Option B — Via USB Flash Drive:**
  1. Copy the `.apk` onto a standard FAT32 USB flash drive.
  2. Plug it into one of your head unit's USB ports, open **File Manager** / **ApkInstaller**, and tap **Install**.
* **Option C — Via ADB (USB or Wi-Fi):**
  ```bash
  adb install -r DashFlow-latest.apk
  ```

### Step 3: Set as Default Home Screen
1. On your head unit or tablet/phone, open **Settings**.
2. Go to **Apps & notifications** -> **Default apps** -> **Home app**.
3. Select **DashFlow**.
4. Press the Home button — your custom OEM cockpit is now live!

> **Android 13+ Sideload Tip ("App was denied access / Restricted setting"):**  
> On Android 13, 14, and 15 phones/tablets, sideloaded APKs block **Display over other apps** (Floating Mute Pill) and **Notification access** (Media Bar) by default. To unlock them in 5 seconds:  
> Open **Settings → Apps → See all apps → DashFlow → tap `⋮` (top-right) → Allow restricted settings**, then re-enable the permission. *(Android 8.1–12 head units do not have this restriction.)*

---

## 🧠 Architecture & Zero-Bloat Engineering

Most aftermarket head-unit launchers trigger Android's **Low Memory Killer (LMK)** during heavy navigation or CarPlay/Android Auto streaming because they embed WebViews, heavy animation loops, or bloated SDKs. DashFlow takes a strict constraint-first approach:

* **Pure Native Views (`ConstraintLayout` + ViewBinding + Vector Drawables)**: No Jetpack Compose runtime, no Flutter engine, no WebViews, and zero analytics or Google Play Services SDKs.
* **Memory-Safe Custom Media**: User-uploaded car photos are downsampled via `inJustDecodeBounds` / `inSampleSize` before decoding, keeping footprint lean even on 1GB RAM hardware.
* **Verify Memory Footprint Yourself**: Connect your head unit or tablet/phone via ADB and inspect resident PSS memory:
  ```bash
  adb shell dumpsys meminfo com.automotive.swiftlauncher
  ```

---

## 🚗 Key Features & Dashboard Customization

### 1. 🚘 Universal Vehicle Dashboard Customizer
Tap the car image or the vehicle badge at any time (or long-press the **Settings** card) to launch the in-cockpit Customizer:
* **Custom Vehicle Model & License Plate**: Personalize your ride's model name and optional registration plate badge directly on the dashboard.
* **Built-in Silhouette Presets**: Switch instantly between **Hatchback**, **Sport Sedan**, **Urban SUV**, **Sports GT**, and **Cyber EV**.
* **Upload Any Custom Car Photo / PNG**: Pick any photo of your own car from device storage/gallery with automatic memory-safe downsampling.
* **Accent Colors & Custom DPI Scaling**: Choose custom cockpit accent colors (`Blue`, `Cyan`, `Green`, `Red`, `Amber`), tune **Whole-Launcher DPI Scale (`70%–130%`)** in 5% increments so every screen/DPI looks crisp, and adjust independent car visual scaling (`85%` / `100%` / `115%`).

### 2. 🏎️ Raw GPS Speedometer (Zero Play Services)
* Uses native Android `LocationManager` (`GPS_PROVIDER`) with zero Google Play Services dependencies so it works smoothly on de-bloated head units.
* Displays a live digital speed pill in **KM/H** or **MPH** (tap the speed pill anytime to switch units).

### 3. 🎵 Headless Media Controller (`MediaSessionManager`)
* Reads active playback sessions from Spotify, YouTube Music, Poweramp, etc. via a lightweight `NotificationListenerService`.
* Automatically appears above the quick controls bar only while media is active, providing blind-touch-friendly **Previous**, **Play/Pause**, and **Next** buttons plus hardware media-key support (`KEYCODE_MEDIA_*`).

### 4. ⛅ Offline-First Weather & Sunrise/Sunset Auto-Dimming
* **Live Weather Pill**: Resolves any configured city name automatically via Open-Meteo's free Geocoding + Forecast APIs (no API key required), caches the last reading in `SharedPreferences`, and falls back silently when driving offline.
* **Zero-Sensor Auto-Dimming**: Optional sunrise/sunset brightness adjustment using cached solar timestamps from Open-Meteo, ideal for budget head units that lack an ambient light sensor.

### 5. 🎯 Direct Driving Cards & Cold-Boot Recovery
1. **Android Auto**: Default maps to `io.github.slashmax.openheadunit` (Wi-Fi Direct launch for your tablet/phone).
2. **Apple CarPlay**: Default maps to `com.zjinnova.zlink` (ZLink wireless/wired CarPlay).
3. **YouTube**: Maps to YouTube / ReVanced / NewPipe.
4. **Settings & All Apps**: Direct access to system settings and an instant-search app drawer.
* **Smart App Re-Binding**: Long-press any driving card to assign it to any installed app on your unit (ideal if your head unit uses custom package names like `cn.manstep.phonemirrorbox` or `com.syu.zlink`).
* **Persistent Floating Mute Pill**: Once DashFlow is set as your Default Home app (and overlay permission is granted), a small draggable Mute/Unmute pill stays visible over Android Auto, CarPlay, and YouTube so you can silence audio anytime without leaving navigation.
* **Cold-Boot Auto-Launch**: Optional `BOOT_COMPLETED` / `QUICKBOOT_POWERON` receiver lets you automatically launch your preferred CarPlay/AA or navigation app right after ignition.

---

<details>
<summary>🛠️ Building from Source (For Developers Only)</summary>

### Prerequisites
* **JDK 17+** and **Android SDK** (compileSdk 34) — Android Studio IDE is optional.

### Build via Terminal (No Android Studio Required)
```bash
git clone https://github.com/HarideepS/DashFlow.git
cd DashFlow
./gradlew assembleDebug   # or .\gradlew assembleDebug on Windows
```
The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Build via Android Studio
1. Open Android Studio and select **Open** on the cloned folder.
2. Let Gradle sync, then click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.

</details>

---

## 🤝 Contributing & License

* Contributions are welcome! Read [CONTRIBUTING.md](CONTRIBUTING.md) for architecture guidelines and good first issues.
* Licensed under the **[MIT License](LICENSE)**.
* Initial prototype & iterative development powered by Google Antigravity.
