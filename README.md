# Scan

<h3 align="center">Modern, Privacy-First Android Document Scanner, Library & On-Device Converter.</h3>

<p align="center">
Scan physical documents, detect corners automatically, apply crisp filters, and convert between PDF, Word (.docx), TXT, and images 100% on-device.
</p>

<img width="2730" height="1536" alt="Scan" src="https://github.com/user-attachments/assets/bc55c121-054e-42b0-a5fc-0c88cf6052ad" />

## Overview

**Scan** is a modern, high-performance, and privacy-focused Android application built to digitize documents cleanly and process files entirely on your device.

Unlike cloud-dependent scanner apps filled with ads and trackers, Scan operates **100% on-device** for camera edge detection, perspective correction, filter rendering, library organization, and PDF/Word/TXT conversions without transmitting your documents anywhere.

Scan is built following **Modern Android Development (MAD)** standards with Kotlin and Jetpack Compose for maximum responsiveness, Material Design 3 styling with Dynamic Color support, and native localization across English, Arabic (full RTL), and French.



## Key Features & Capabilities

| Feature | Engine / API Used | Execution | Privacy & Safety | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Smart Camera Scanner** | `CameraX` & `DocEdgeDetector` | **Real-Time** | **100% On-Device** | Live document quadrilateral detection, quick shutter capture, zoom slider, torch control, and camera flip support. |
| **Smooth Corner Adjustment** | `AdjustCornersView` & Perspective Warp | **Interactive** | **100% On-Device** | Precision corner dragging, rotate/crop tools, automatic crop, and uncropped original framing. |
| **Document Filters & Multi-Page Review** | `DocImageProcessor` & `DocReviewView` | **Fast** | **100% On-Device** | Clean document enhancement filters, page reordering, gallery image import, and multi-page PDF import. |
| **PDF & Image Export** | `DocPdfExporter` & `SaveToFilesView` | **Fast** | **100% On-Device** | Export multi-page scans as PDF or save pages as images directly to `Documents/Scanned` and Gallery. |
| **Document Library** | `DocLibraryManager` | **Instant** | **Locally Stored** | Browse, search, filter by format, sort, preview, rename, share, and delete saved documents. |
| **Convert Documents Suite** | `PdfBox-Android`, `PdfTextEngine`, `DocxWriter`, `PdfImageExtractEngine` | **Offline** | **100% On-Device** | Extract text from PDFs to TXT/Word/PDF, convert PDFs to editable `.docx` keeping text and images, convert TXT to PDF/Word, and extract embedded images or render PDF pages to JPG/PNG/ZIP. |
| **In-App Auto Updater** | `UpdateUtil`, `GitHub Releases API` & `FileProvider` | **Background** | **Direct GitHub Fetch** | Checks GitHub Releases (`hamzabellouch/scan`) for new versions with download progress notifications. |
| **Multilingual & RTL Design** | `LanguageManager` & Compose RTL | **Native** | **N/A** | Full localization in English, Arabic (with Arabic text shaping for PDFs), and French, plus optional community language packs. |
| **Zero Telemetry** | Pure Local Processing | **N/A** | **Complete Privacy** | Zero analytics SDKs, zero advertising libraries, and no personal data collection. |



## Tech Stack & Architecture

- **Architecture & UI:** Single-activity Jetpack Compose app (`MainActivity`, `MainScreen`), Material Design 3 with Dynamic Color, adaptive Light/Dark/High-Contrast themes, and floating/classic navigation bars.
- **Language & Tooling:** Kotlin + Jetpack Compose + Gradle Version Catalog (`libs.versions.toml`). Target & Compile SDK: Android API 37 | Minimum SDK: API 24.
- **Imaging & Camera:** AndroidX `CameraX` (`camera2`, `lifecycle`, `view`) + custom multi-scale edge detection (`DocEdgeDetectorCore`) and perspective rectification (`DocImageProcessor`).
- **Document & PDF Processing:** `PdfBox-Android`, Android `PdfRenderer` & `PdfDocument`, custom `.docx` generator (`DocxWriter`), and Arabic presentation-form text shaper (`ArabicTextShaper`).
- **Networking (Updater & Language Packs):** `OkHttp` + `Kotlinx Serialization JSON`.
- **Testing:** JUnit 4 unit tests (`DocEdgeDetectorTest`, `DocxWriterTest`, `ArabicTextShaperTest`, `DocLibraryManagerTest`).



## Installation

1. Go to the Releases page:
   https://github.com/hamzabellouch/scan/releases

2. Download the latest `.apk` file.

3. Install the application on your Android device.

4. Make sure that `Install from unknown sources` is enabled in your Android settings.

## Building from Source

To build Scan locally, make sure you have the latest version of Android Studio installed.

1. Clone the repository: `git clone https://github.com/hamzabellouch/scan.git`
2. Open the project in Android Studio.
3. Sync Gradle dependencies.
4. Build and run the application on your device or emulator.



> [!WARNING]
> There is always a possibility of error, so we assume no responsibility for any inaccuracies.


### <a name="Copyright©2026"></a> Copyright © 2026

Thank you for checking out Scan. If you have any feedback or suggestions, feel free to contact us:
hamzabellouchcontact@gmail.com

Stay connected and follow us on:  
[WhatsApp](https://whatsapp.com/channel/0029Vb7MArw0LKZMpjjqOk2P) | [Facebook](https://facebook.com/hamzabellouch0) | [Instagram](https://instagram.com/hamzabellouch0) | [X](https://x.com/hamzabellouch0) | [Telegram](https://t.me/hamzabellouch) | [LinkedIn](https://www.linkedin.com/in/hamzabellouch)
