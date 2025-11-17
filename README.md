# 🚀 Real-Time Edge Detection Viewer

This project was developed as part of a technical assessment for a Web RnD Intern role. It demonstrates a minimal Android application that captures live camera frames, processes them using OpenCV in native C++ via JNI, and renders the output using OpenGL ES. Additionally, a TypeScript-based web viewer is included to simulate how native processing results can be bridged to a web interface.

---

## ✅ Features Implemented

### Android App
- 📸 Camera feed integration using Camera2 API and TextureView
- 🧠 Native frame processing via OpenCV C++ (Canny edge detection)
- 🎨 Real-time rendering using OpenGL ES 2.0
- 🕹️ Toggle between raw and processed frames (optional)
- ⏱️ FPS counter overlay (optional)

### Web Viewer
- 🌐 TypeScript + HTML page displaying a static processed frame
- 🧾 Overlay showing mock frame stats (FPS, resolution)
- 🧪 Demonstrates DOM manipulation and modular TypeScript setup

---

## 🧠 Architecture Overview
/app → Android UI and camera setup (Java/Kotlin) /jni → Native C++ code for OpenCV processing /gl → OpenGL ES renderer classes /web → TypeScript web viewer


- **Java/Kotlin** handles camera access and UI
- **C++** handles image processing using OpenCV
- **OpenGL ES** renders processed frames as textures
- **TypeScript** simulates web-side visualization

---

## ⚙️ Setup Instructions

### Android
- Install Android Studio
- Enable NDK and CMake via SDK Manager
- Clone the repo and sync Gradle
- OpenCV native libraries are included via JNI

### Web Viewer
- Navigate to `/web`
- Run `tsc` to compile TypeScript
- Open `index.html` in browser

---

## 📷 Screenshots

A sample processed image is included in `/web/sample.png` to simulate native output.

---

## 📦 Submission Notes

This repository reflects a modular and performance-conscious design. Frame processing is offloaded to native code for speed, and rendering is handled via OpenGL for smooth visuals. The web viewer demonstrates frontend integration and project structuring in TypeScript.