# 🌾 AgriServe Mobile

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Version-v1.0.0-2E7D32?style=for-the-badge" alt="Version 1.0.0" />
  <img src="https://img.shields.io/badge/Release-Stable-brightgreen?style=for-the-badge" alt="Release" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License" />
</p>

<p align="center">
  <b>Official Android application empowering local farmers and Municipal Agriculture Office (MAO) personnel in Ragay, Camarines Sur.</b>
</p>

---

## 📲 Direct APK Download

Download and install the latest signed Android APK directly onto your Android device:

<p align="center">
  <a href="https://github.com/MarvinAsanez/AgriServe1/releases/latest/download/AgriServe.apk">
    <img src="https://img.shields.io/badge/DOWNLOAD_LATEST_APK-AgriServe.apk-2E7D32?style=for-the-badge&logo=android&logoColor=white" height="50" alt="Download APK" />
  </a>
</p>

### 🔗 Alternative Download Links:
* **Direct Latest APK**: [AgriServe.apk](https://github.com/MarvinAsanez/AgriServe1/releases/latest/download/AgriServe.apk) *(~102 MB, signed & ready)*
* **Release v1.0.0 APK**: [AgriServe-v1.0.0.apk](https://github.com/MarvinAsanez/AgriServe1/releases/download/v1.0.0/AgriServe-v1.0.0.apk)
* **GitHub Releases Page**: [View All Releases & Assets](https://github.com/MarvinAsanez/AgriServe1/releases)

---

## 🛠️ How to Install on Android

1. **Download the APK**: Tap the green button above on your Android phone or tablet.
2. **Enable Unknown Apps** *(first time only)*:
   * When opening the downloaded file, if prompted with *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings**.
   * Toggle **Allow from this source** to ON, then press the back button.
3. **Install & Launch**: Tap **Install** and open **AgriServe**!

---

## ✨ Key Features

* 🌐 **Tri-Language Support**:
  * **Central Bikol** (*Bikol Sentral / Ragay*: *Paraoma, Sakahan, Kahagadan, Paisi, Patanid sa Kalamidad*)
  * **Tagalog / Filipino** (*Magsasaka, Sakahan, Kahilingan, Ulat Panahon*)
  * **English** (*Native*)
  * Instant in-app language switching under Profile settings.

* 🤖 **Gemini AI Customer Service Agent**:
  * In-app conversational assistant powered by Google Gemini and Firebase Cloud Functions v2.
  * Live tool-calling for real-time seed/fertilizer stock lookups, request tracking, and DA staff escalation.

* 🚨 **Real-Time Calamity & Disaster Alerts**:
  * Urgent alert siren banner and PAGASA storm signal advisories broadcasted directly to farmers.
  * Direct emergency response hotline shortcuts.

* 🗺️ **Unified Farm Mapping & GPS Tagging**:
  * Satellite-mapped farm boundaries with GPS precision.
  * Barangay-level farm overview and crop coverage.

* 📦 **DA Subsidized Aid & Inventory**:
  * Request hybrid/inbred palay seeds, urea, complete fertilizers, and machinery.
  * Real-time verification and distribution progress tracking.

---

## 🏗️ Technical Stack

* **Platform**: Android 8.0+ (API 26+)
* **Architecture**: MVVM, Jetpack Compose, Material 3
* **Backend**: Google Cloud Firestore, Firebase Authentication, Cloud Functions (Node.js 22)
* **AI Engine**: Google Gen AI SDK (`gemini-1.5-flash`)
* **Location & Maps**: Google Play Services Location & MapLibre/OSM

