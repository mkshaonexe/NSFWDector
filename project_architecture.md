# NSFW Detector Android - Project Architecture & Workflow

This document explains exactly how the `NSFWDetector` Android project is structured and how it processes an image to determine if it contains NSFW (Not Safe For Work) content.

## 1. Project Overview

The application is built using modern Android technologies (Kotlin and Jetpack Compose) combined with **Firebase ML Kit (AutoML Vision Edge)**. It runs offline, meaning all image processing and inference happen directly on the user's device without sending data to a cloud server.

### Key Components:
- **`MainActivity.kt`**: The Jetpack Compose User Interface (UI).
- **`NSFWDetector.kt`**: The singleton object handling the Machine Learning (ML) logic.
- **`/assets/automl/`**: The folder containing the core AI brain (the trained model and labels).

---

## 2. The AI Brain (The Assets)

Before looking at the code, it's crucial to understand what powers the detection. The `/app/src/main/assets/automl/` folder contains three files:

1. **`NSFW.tflite`**: This is a compiled **TensorFlow Lite** neural network model. It's roughly 5.8MB and has been pre-trained on thousands of images to recognize patterns associated with nudity.
2. **`dict.txt`**: A simple text file containing the exact output labels the model understands: `nude` and `nonnude`.
3. **`manifest.json`**: A configuration file used by Firebase ML Kit to know *how* to load the `.tflite` model and *which* dictionary file to map to its outputs.

*Note: In `build.gradle.kts`, we added `aaptOptions { noCompress("tflite") }`. This prevents Android from compressing the model into the APK, allowing the C++ ML interpreter to memory-map the file directly from disk for blazing-fast loading without crashing the app's RAM.*

---

## 3. The User Interface (`MainActivity.kt`)

The user interacts with the app entirely through `MainActivity.kt`, which is built using Jetpack Compose.

### Step-by-Step UI Flow:
1. **Firebase Initialization**: When the app starts (`onCreate`), it immediately calls `FirebaseApp.initializeApp(this)`. This is strictly required before using any local ML Kit models.
2. **State Management**: The `NSFWScannerScreen` composable holds various states:
   - `imageUri` & `bitmap`: Holds the currently selected image.
   - `scanResult`: Holds the text to display (e.g., "Safe Content" or "NSFW Detected").
   - `isScanning`: A boolean that shows a loading spinner while the model is thinking.
3. **The Gallery Picker**: We use Jetpack Compose's `rememberLauncherForActivityResult` to open the native Android photo gallery.
4. **Bitmap Decoding**: Once the user picks an image (returning a `Uri`), the app converts it into an Android `Bitmap` using `ImageDecoder` (for modern Android versions) or `MediaStore` (for older versions).
5. **Triggering Inference**: The decoded `Bitmap` is then passed directly into `NSFWDetector.isNSFW()`.

---

## 4. The Detection Logic (`NSFWDetector.kt`)

This is the bridge between the Android app and the raw TensorFlow Lite model.

### 1. Model Setup
When `NSFWDetector` is first accessed, it statically loads the model into memory:
```kotlin
private val localModel = FirebaseAutoMLLocalModel.Builder()
    .setAssetFilePath("automl/manifest.json")
    .build()

private val options = FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder(localModel).build()
private val interpreter = FirebaseVision.getInstance().getOnDeviceAutoMLImageLabeler(options)
```
This tells Firebase: *"Look in the assets folder for `manifest.json`, load the local AutoML model based on those instructions, and give me an interpreter I can use to process images."*

### 2. Processing the Image
When `isNSFW(bitmap)` is called:
1. **Image Conversion**: The standard Android `Bitmap` is converted into a `FirebaseVisionImage`, the format required by the ML Kit interpreter.
2. **Inference Execution**: `interpreter.processImage(image)` is called. This runs asynchronously (on a background thread) so it doesn't freeze the UI.

### 3. Evaluating the Results (The Threshold Logic)
When the model finishes looking at the image, it returns a list of labels (`nude` or `nonnude`) along with a **confidence score** (a float from `0.0` to `1.0`).

The code uses a strict mathematical evaluation against a default threshold of **`0.7` (70%)** to be safe:
- If the model predicts **`nude`** (NSFW):
  - Does the model have **> 70%** confidence? If yes -> Return `isNSFW = true`.
- If the model predicts **`nonnude`** (Safe):
  - Is the confidence **< 30%** (`1 - threshold`)? (Meaning the model thinks it's strictly safe, but isn't very sure). To be cautious, it will flag it as suspicious -> Return `isNSFW = true`.
  - Is it highly confident (> 30%) it's safe? -> Return `isNSFW = false`.

### 4. Returning to the UI
The result (Boolean `isNSFW` and Float `confidence`) is passed back to `MainActivity` via a Lambda callback, which instantly updates the Compose State holding the `scanResult` text, turning the text Red if it's unsafe, or Green if it's safe.

---

## Conclusion
The application heavily relies on the synergy between **Jetpack Compose** for a reactive, state-driven UI, and **Firebase ML Kit On-Device AutoML** for lightning-fast, offline inference powered by a highly optimized **TensorFlow Lite** neural network.
