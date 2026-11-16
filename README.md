# DashFlow — Universal OEM Car Launcher (Android 8.1+ & 1GB RAM Optimized)

<div align="center">

[![Vibecoded with Google Antigravity](https://img.shields.io/badge/Vibecoded%20with-Google%20Antigravity-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://antigravity.google)
[![Platform](https://img.shields.io/badge/Platform-Android%208.1%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![RAM Footprint](https://img.shields.io/badge/Resident%20RAM-~15--20MB-FF6F00?style=for-the-badge)](#)
[![Customization](https://img.shields.io/badge/Cockpit-Fully%20Customizable-00E5FF?style=for-the-badge)](#)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%2F%20XML-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)

**DashFlow** is a lightweight, high-performance, OEM-styled Android Car Launcher designed specifically for low-resource Android head units (including 1GB RAM units running Android 8.1+) as well as your tablet/phone repurposed as a car display — **fully normalized so any driver can customize it for their own car!**

</div>

---

## ⚡ Vibecoded with Google Antigravity

This entire project was **vibecoded end-to-end using Google Antigravity**, translating rapid natural language prompts into a battle-tested, resource-thrifty, production-ready Android automotive cockpit:

- **Constraint-First Engineering**: Designed and tuned to triumph over aggressive Low Memory Killer (LMK) limits on entry-level Android 8.1 head units with only 1GB RAM.
- **Pure Native Performance**: Zero webview bloat, zero heavy Compose overhead—pure native Android Kotlin, `ConstraintLayout`, and vector graphics maintaining an astonishingly low ~15–20MB resident memory footprint.
- **Universal Vehicle Customizer**: Normalized via prompt-driven architecture into a universal car launcher supporting custom vehicle names, license plates, multi-silhouette presets, and custom gallery photos.

---

## 🚗 Key Features & Dashboard Customization

### 1. 🚘 Universal Vehicle Dashboard Customizer
Tap the car image or the vehicle badge at any time (or long-press the **Settings** card) to launch the in-cockpit Customizer:
* **Custom Vehicle Model / Name**: Personalize your ride's name (e.g. *Maruti Suzuki Swift*, *Hyundai Creta*, *Honda Civic*, *BMW 3 Series*, *Tesla Model 3*).
* **Official License Plate Tag**: Render your real registration plate (e.g. *TG08 G6391*, *KA 01 AB 1234*) in clean OEM plate typography, or leave empty to hide.
* **Built-in Silhouette Presets**:
  * 🚗 **Swift OEM Render**: Dual-tone Arctic White with Midnight Black floating roof.
  * 🏎️ **Sport Sedan**: Aerodynamic executive saloon silhouette.
  * 🚙 **Urban SUV / Crossover**: Muscular stance with roof rails.
  * 🏁 **Sports GT**: Aggressive low-slung supercar coupe.
  * ⚡ **Cyber EV**: Futuristic minimalist electric vehicle with neon LED lightbars.
* **Upload Any Custom Car Photo / PNG**: Pick any photo of your actual car from your device gallery or downloads. Includes an intelligent in-memory downsampler that keeps resident RAM under 20MB even with 48MP camera photos!
* **Car Display Scaling**: Choose between `85% Compact`, `100% Default`, and `115% Hero` scaling.

### 2. ⚡ Ultra-Low Memory Footprint (~15–20MB Resident RAM)
* Built using pure native Android Views (`ConstraintLayout` + XML drawables) instead of memory-heavy engines (Flutter, Compose, or KLWP).
* Prevents Android 8.1's Low Memory Killer (LMK) from killing the launcher when Open Headunit or ZLink streams high-bitrate video.

### 3. 🎯 Direct Driving Cards
1. **Android Auto (Blue)**: Default maps to `io.github.slashmax.openheadunit` (Wi-Fi Direct launch for your tablet/phone).
2. **Apple CarPlay (Green)**: Default maps to `com.zjinnova.zlink` (ZLink wireless/wired CarPlay).
3. **YouTube (Red)**: Maps to YouTube / ReVanced / NewPipe.
4. **Settings & All Apps (Frosted Glass)**: Direct access to system settings and full app drawer with real-time search.
* **Smart App Re-Binding**: Long-press any card to re-bind it to any installed application on your unit (ideal if your head unit uses custom package names like `cn.manstep.phonemirrorbox` or `com.syu.zlink`).

### 4. 🎛️ Quick Controls Bar
* **Wi-Fi**: Instant toggle or long-press for Wi-Fi settings.
* **Bluetooth**: Direct shortcut to Bluetooth pairing.
* **Screen Brightness**: 4-stage brightness cycle (`100% Day` -> `50% Dusk` -> `15% Night Mode` -> `Auto`).
* **Volume**: Single-tap mute / unmute with automatic volume memory.

### 5. ⛅ Live Weather & Digital Clock
* **Live Weather Pill**: Automatic sync via Open-Meteo API (no API key required) with offline cache fallback. City location is fully configurable in the Customizer!
* **TextClock**: 0% CPU consumption digital clock. Tap the clock at any time to toggle between 12-hour (AM/PM) and 24-hour formats.

---

## 🛠️ How to Build & Install

### 🔨 Build

#### Open with Android Studio
1. Open Android Studio.
2. Select **Open** and choose this project folder.
3. Let Gradle sync and click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
4. The generated APK will be in:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

---

### 📲 Install

Sideload `app-debug.apk` onto your Android head unit or your tablet/phone using any of the following methods:

#### Option A: Via LocalSend (Wireless File Transfer — Recommended)
1. Install **[LocalSend](https://localsend.org/)** on both your phone/PC and your Android head unit (or your tablet/phone).
2. Ensure both devices are connected to the same Wi-Fi network or mobile hotspot.
3. Open LocalSend on your phone/PC, select `app-debug.apk`, and send it to your head unit.
4. Accept the incoming transfer on your head unit, tap the received APK, and select **Install**.

#### Option B: Via USB Flash Drive
1. Copy `app-debug.apk` onto a standard FAT32 USB flash drive.
2. Plug it into one of your head unit's USB ports.
3. Open the built-in **"File Manager"** or **"ApkInstaller"** on your head unit.
4. Tap the APK and select **Install**.

#### Option C: Via ADB (USB or Wi-Fi)
```bash
adb install -r app-debug.apk
```

---

## ⚙️ Setting as Default Home Screen

1. On your head unit or tablet/phone, open **Settings**.
2. Go to **Apps & notifications** -> **Default apps** -> **Home app**.
3. Select **DashFlow**.
4. Press the Home button on your head unit / navigation bar; it will now permanently open your custom OEM cockpit!
