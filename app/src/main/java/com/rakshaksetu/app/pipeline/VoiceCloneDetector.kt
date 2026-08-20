package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * True ONNX Inference for Deepfake / Voice Clone Detection.
 * True ONNX Runtime inference for on-device classification.
 */
object VoiceCloneDetector {
    private const val TAG = "VoiceCloneDetector"
    private const val MODEL_FILENAME = "deepfake_fp32.onnx"

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val assetManager = context.assets
            val modelBytes = assetManager.open(MODEL_FILENAME).readBytes()
            ortSession = ortEnv?.createSession(modelBytes, OrtSession.SessionOptions())
            Log.i(TAG, "True ONNX Deepfake Model Loaded Successfully!")
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load true ONNX Deepfake model.", e)
        }
    }

    fun analyze(pcmData: ByteArray): Float {
        if (!isInitialized || ortEnv == null || ortSession == null) {
            Log.w(TAG, "Deepfake model not initialized. Falling back to 0.0")
            return 0.0f
        }
        
        Log.d(TAG, "Running actual ONNX inference for Deepfake artifacts...")
        return try {
            // Convert PCM bytes to Float Array (simulating 16000 samples for the CNN)
            val floatArray = FloatArray(16000)
            for (i in 0 until minOf(pcmData.size / 2, 16000)) {
                val low = pcmData[i * 2].toInt() and 0xFF
                val high = pcmData[i * 2 + 1].toInt() shl 8
                floatArray[i] = (high or low).toShort() / 32768.0f
            }
            
            val floatBuffer = FloatBuffer.wrap(floatArray)
            // Model expects shape [1, 1, 16000]
            val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 1, 16000))
            
            val inputs = mapOf("audio_input" to inputTensor)
            val result = ortSession?.run(inputs)
            
            // Output is [1, 1] sigmoid probability
            val outputTensor = result?.get(0)?.value as? Array<FloatArray>
            val isFakeProb = outputTensor?.get(0)?.get(0) ?: 0.0f
            
            result?.close()
            inputTensor.close()
            
            isFakeProb
        } catch (e: Exception) {
            Log.e(TAG, "ONNX Inference failed.", e)
            0.0f
        }
    }
}
