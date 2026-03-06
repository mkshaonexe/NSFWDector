package com.nipunru.nsfwdetector

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions

const val TAG = "NSFWDetector"

object NSFWDetector {
    private const val LABEL_SFW = "nude"
    private const val LABEL_NSFW = "nonnude"
    private const val CONFIDENCE_THRESHOLD: Float = 0.7F

    // Leverage modern Google ML Kit to parse the legacy AutoML models directly and offline
    private val localModel = LocalModel.Builder()
        .setAssetManifestFilePath("automl/manifest.json")
        .build()

    private val customImageLabelerOptions = CustomImageLabelerOptions.Builder(localModel)
        .setConfidenceThreshold(0.0f) // Threshold is calculated dynamically below to match module specifications
        .setMaxResultCount(2)
        .build()

    private val labeler = ImageLabeling.getClient(customImageLabelerOptions)

    /**
     * This function returns whether the bitmap is NSFW or not
     * @param bitmap: Bitmap Image
     * @param confidenceThreshold: Float 0 to 1 (Default is 0.7)
     * @return callback with isNSFW(Boolean), confidence(Float), and image(Bitmap)
     */
    fun isNSFW(
        bitmap: Bitmap,
        confidenceThreshold: Float = CONFIDENCE_THRESHOLD,
        callback: (Boolean, Float, Bitmap) -> Unit
    ) {
        var threshold = confidenceThreshold

        if (threshold < 0 || threshold > 1) {
            threshold = CONFIDENCE_THRESHOLD
        }
        
        // Convert to modern ML Kit InputImage format
        val image = InputImage.fromBitmap(bitmap, 0)
        
        labeler.process(image).addOnSuccessListener { labels ->
            try {
                if (labels.isEmpty()) {
                    callback(false, 0.0F, bitmap)
                    return@addOnSuccessListener
                }
                
                val label = labels[0]
                when (label.text) {
                    LABEL_SFW -> {
                        if (label.confidence > threshold) {
                            callback(true, label.confidence, bitmap)
                        } else {
                            callback(false, label.confidence, bitmap)
                        }
                    }
                    LABEL_NSFW -> {
                        if (label.confidence < (1 - threshold)) {
                            callback(true, label.confidence, bitmap)
                        } else {
                            callback(false, label.confidence, bitmap)
                        }
                    }
                    else -> {
                        callback(false, 0.0F, bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, e.localizedMessage ?: "NSFW Scan Error")
                callback(false, 0.0F, bitmap)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, e.localizedMessage ?: "NSFW Scan Error")
            callback(false, 0.0F, bitmap)
        }
    }
}