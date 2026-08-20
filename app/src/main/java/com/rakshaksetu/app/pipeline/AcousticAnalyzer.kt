package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * True ONNX Inference for Acoustic Environment Recognition.
 * True ONNX Runtime inference for on-device classification.
 */
object AcousticAnalyzer {
    private const val TAG = "AcousticAnalyzer"
    private const val MODEL_FILENAME = "acoustic_fp32.onnx"
    
    enum class Environment {
        HOME, STREET, CALL_CENTER, UNKNOWN
    }

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
            Log.i(TAG, "True ONNX Acoustic Model Loaded Successfully!")
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load true ONNX Acoustic model.", e)
        }
    }

    fun analyze(pcmData: ByteArray): Environment {
        if (!isInitialized || ortEnv == null || ortSession == null) {
            Log.w(TAG, "Acoustic model not initialized.")
            return Environment.UNKNOWN
        }
        
        Log.d(TAG, "Running actual ONNX inference for Acoustic Environment...")
        return try {
            // Basic deterministic feature extraction for ONNX (In production, use JTransforms for exact MFCCs)
            val mfccArray = FloatArray(40) { index ->
                if (index < pcmData.size) (pcmData[index].toInt() and 0xFF) / 255.0f else 0.0f
            }
            
            val floatBuffer = FloatBuffer.wrap(mfccArray)
            // Model expects shape [1, 40]
            val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 40))
            
            val inputs = mapOf("mfcc_input" to inputTensor)
            val result = ortSession?.run(inputs)
            
            // Output is [1, 3] softmax probabilities
            val outputTensor = result?.get(0)?.value as? Array<FloatArray>
            val probs = outputTensor?.get(0)
            
            result?.close()
            inputTensor.close()
            
            if (probs == null || probs.size != 3) return Environment.UNKNOWN
            
            // Class 0: Home, 1: Street, 2: Call Center
            val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: -1
            when (maxIdx) {
                0 -> Environment.HOME
                1 -> Environment.STREET
                2 -> Environment.CALL_CENTER
                else -> Environment.UNKNOWN
            }
        } catch (e: Exception) {
            Log.e(TAG, "ONNX Inference failed.", e)
            Environment.UNKNOWN
        }
    }
}
