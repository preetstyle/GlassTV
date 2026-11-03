# GlassTV (`v1.0.0`)

> 📦 **Download Latest APK (`v1.0.0`):** **[GitHub Releases (`GlassTV.apk`)](https://github.com/preetstyle/GlassTV/releases/latest)**

![GlassTV Home Screen](screenshots_en/Home.png)

**GlassTV** is an ultra-fast, lightweight, **Apple tvOS 26 Liquid Glass** home screen and system manager for Android TV & Google TV — built in pure Java with zero third-party runtime dependencies (**~30 MB private RAM / `0.0%` background CPU**).

Designed to stay buttery-smooth on every TV box while bringing a real-time **S-Curve Meniscus Liquid Glass Dock (`50%–150%`)**, a **Top-Right Time + Control Panel Glass Pill**, a **Live 4K UHD `r/wallpapers` Browser (`Custom 1..5`)**, **One-Click Push Icons (Circular App Icon Pack)**, **Dock App Locking**, **App Icon Changer & Interactive Icon Editor**, a **built-in PC-free CleanYourTV Debloater (341 TV models + Manual System App Debloater + Stock Launcher Disabler)**, a **Universal Liquid Glass Control Panel (`Quick Settings`) overlay**, **Recent Apps Switcher**, **Multi-Tap (`1x / 2x / 3x`) Remote Button Remapping**, and **One-Tap Local ADB Setup**.

---

## ✨ What's New in GlassTV `v1.0.0`

- **Apple tvOS 26 S-Curve Meniscus Liquid Glass Dock**:
  - True optical meniscus lens refraction along the rounded capsule border with smooth Gaussian-softened center glass, specular rim lighting, and chromatic dispersion.
  - Dynamically scales from **`50%` to `150%`** (`100%` default) in *Settings → Appearance and scenes*, with automatic overflow into the **Other apps (`...`)** multi-row glass folder.
- **Clean Wallpaper Canvas + Top-Right Time & Control Panel Glass Pill**:
  - Distraction-free 4K wallpaper showcase with no giant center clock or greeting clutter.
  - Top-right **Time + Control Panel Glass Pill** displays the live clock alongside the Quick Settings icon with smooth focus illumination.
- **Built-in 4K UHD `r/wallpapers` Live Preview Browser**:
  - Browse, preview in full-screen 4K UHD (`3840×2160`), and apply curated photography from `r/wallpapers` directly inside *Settings → Appearance and scenes* with instant disk caching.
- **One-Click Push Icons & Lock Dock Apps**:
  - **Push Icons**: Apply curated circular TV app icons across your installed apps in a single tap from *Settings → Apps*, or hold **OK** on any app to customize its icon individually.
  - **Lock Apps in Dock**: Prevent accidental reordering or removal of your favorite dock apps.

---

## 📸 Screenshots

### Home Screen & Liquid Glass Dock

| Autumn Birch 4K & Time + Control Panel Pill | Dolomites 4K & Liquid Glass Dock |
| :---: | :---: |
| ![GlassTV Home Screen](screenshots_en/Home.png) | ![GlassTV Dolomites Home](screenshots_en/Home_Dolomites.png) |

| S-Curve Meniscus Liquid Glass Refraction (Close-Up) |
| :---: |
| ![Liquid Glass Dock Close-Up](screenshots_en/Dock_Closeup.png) |

---

### Liquid Glass Control Panel, 4K Wallpaper Browser & Recent Apps

| Liquid Glass Control Panel (`Quick Settings`) | 4K UHD `r/wallpapers` Live Preview Browser |
| :---: | :---: |
| ![Control Panel](screenshots_en/controlpanel.png) | ![4K Wallpaper Browser](screenshots_en/Wallpapers_4K.png) |

| Recent Apps Switcher & RAM Cleaner |
| :---: |
| ![Recent Apps Switcher](screenshots_en/Recent%20apps.png) |

---

### Home Settings & System Tools

| Appearance & Scenes (`Dock Size 50%–150%`) | Control Panel (`Quick Settings`) |
| :---: | :---: |
| ![Settings - Appearance and Scenes](screenshots_en/Setting1.png) | ![Settings - Control Panel](screenshots_en/Setting2.png) |

| Button Mapper (`1x / 2x / 3x Tap` & Home Button Fix) | Advanced, ADB Status & Permission Check (`5 / 5`) |
| :---: | :---: |
| ![Settings - Button Mapper](screenshots_en/Setting3.png) | ![Settings - Advanced & ADB](screenshots_en/Setting4.png) |

| CleanYourTV — Disable System Launchers | Automatic & Manual GitHub Updates |
| :---: | :---: |
| ![Settings - Disable System Launchers](screenshots_en/Setting5.png) | ![Settings - Updates](screenshots_en/Setting6.png) |

---

## 🔥 Core Features

- **App Icon Changer, Push Icons & Interactive Icon Editor (`JPG / PNG / ICO`)**
  - Hold **OK** on any app in the dock or grid and choose **Change app icon**, or use **Push Icons** in Settings to apply bundled circular icons automatically.
  - Built-in **App Icon Editor** with live preview, **14 background color options**, **Zoom In / Out**, and **Left / Right / Up / Down** positioning.
- **Custom App Renaming, Other Apps & Hidden Apps Vault**
  - Hold **OK** on any app in the dock to **Rename**, **Change app icon**, **Move**, **Move to Other Apps**, **Hide**, view **App Info**, **Uninstall**, or **Reset Layout**.
- **CleanYourTV (Beta) — Built-In PC-Free Android TV Debloater (`Settings → Advanced`)**
  - Runs **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** debloat profiles directly on your TV over Local ADB (`127.0.0.1:5555`) — **no PC required**.
  - **341 Bundled TV & Box Profiles** (*TCL, Sony, Xiaomi, Hisense, Philips, Skyworth, Nvidia Shield, Chromecast with Google TV*, and more).
  - **One-Tap Stock Launcher Disabler & Manual Debloater** for disabling *Google TV Home / LauncherX, Android TV Home*, and OEM bloatware.
- **Universal Liquid Glass Control Panel (`Quick Settings`) Overlay**
  - Accessible from the home screen pill or over **any running app** via the Button Mapper accessibility service with real-time background blur.
  - Includes **Wi-Fi**, **Bluetooth**, **RAM / Recent Apps**, **Do Not Disturb**, **Audio / Sound**, **Sleep Timer**, **Ambient Mode**, **Power / Screen Off**, **Inputs**, **Button Mapper**, **Settings**, and **Search**.
- **Multi-Tap Remote Button Mapper (`1x` / `2x` / `3x` Tap), Home Button Fix & Volume Hold Fix**
  - Assign independent actions for **Single Tap (1x)**, **Double Tap (2x)**, and **Triple Tap (3x)** on any remote button.
- **Easy Setup via Local ADB & Keep Alive Auto-Repair**
  - Automatically unlocks `ACCESS_RESTRICTED_SETTINGS` (Android 13/14+) and grants Default Home, Accessibility Service, Device Admin, Notification Listener, Usage Stats, and `WRITE_SECURE_SETTINGS` in one click.
- **13 Supported Languages**
  - English, Turkish, Swedish, Hindi, Spanish, French, German, Italian, Russian, Portuguese, Arabic, Japanese, and Chinese.

---

## 🚀 Quick Start (Build & Install)

### Download Pre-Built APK
Download **`GlassTV.apk`** directly from **[GitHub Releases](https://github.com/preetstyle/GlassTV/releases/latest)** and install it on your Android TV / Google TV.

### Build from Source

#### Prerequisites
- **JDK 17+** (`javac`, `jar`)
- **Android SDK** with `build-tools;34.0.0` and `platforms;android-34`

#### Build on macOS / Linux
```sh
./build.sh
# Output APK -> build/GlassTV.apk
```

#### Build on Windows (PowerShell)
```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
# Output APK -> build\GlassTV.apk
```

#### Install via ADB
```sh
adb connect <tv-ip>:5555
adb install -r build/GlassTV.apk
adb shell am start -n com.techgenius.tvoslauncher/.MainActivity
```

---

## 📋 Requirements

- **Android TV / Google TV 7.0+ (API 24+)**

---

## 🙌 Credits & Acknowledgements

- **[tvOS Launcher](https://github.com/techgenius1/tvOS-Launcher)** by [@techgenius1](https://github.com/techgenius1)
- **[Hume Launcher](https://github.com/petrobot-admin/hume-launcher)** by [@petrobot-admin](https://github.com/petrobot-admin)
- **[Couch TV](https://github.com/Seagey/couch-tv)** by [@Seagey](https://github.com/Seagey)
- **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** by [@Gathin23](https://github.com/Gathin23)
- **Inter Typeface** (SIL Open Font License 1.1) & **Project Nayuki QR Code Generator** (MIT License).

---

## 📄 License

Licensed under the **Apache License 2.0** — see [LICENSE](LICENSE) and [NOTICE](NOTICE) for details.
