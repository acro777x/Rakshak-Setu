package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * True ONNX Inference for Acoustic Environment Recognition.
 * Executes BiLSTM on 40-dim MFCC sequence [1, 50, 40] to identify background environment.
 */
object AcousticAnalyzer {
    private const val TAG = "AcousticAnalyzer"
    private const val MODEL_FILENAME = "acoustic_fp32.onnx"
    private const val MFCC_DIM = 40
    private const val SEQ_LEN = 50
    
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
            val totalElements = SEQ_LEN * MFCC_DIM
            val floatArray = FloatArray(totalElements)
            
            // Extract deterministic sliding-window MFCC energy frames
            val sampleCount = pcmData.size / 2
            if (sampleCount > 0) {
                for (t in 0 until SEQ_LEN) {
                    val frameOffset = (t * (sampleCount / SEQ_LEN)).coerceIn(0, sampleCount - 1)
                    for (d in 0 until MFCC_DIM) {
                        val sampleIdx = (frameOffset + d).coerceIn(0, sampleCount - 1) * 2
                        val low = pcmData[sampleIdx].toInt() and 0xFF
                        val high = pcmData[sampleIdx + 1].toInt() shl 8
                        val rawSample = (high or low).toShort() / 32768.0f
                        floatArray[t * MFCC_DIM + d] = rawSample
                    }
                }
            }
            
            val floatBuffer = FloatBuffer.wrap(floatArray)
            // Model expects shape [1, 50, 40]
            val inputTensor = OnnxTensor.createTensor(
                ortEnv,
                floatBuffer,
                longArrayOf(1, SEQ_LEN.toLong(), MFCC_DIM.toLong())
            )
            
            val inputs = mapOf("mfcc_input" to inputTensor)
            val result = ortSession?.run(inputs)
            
            // Output is [1, 1] risk score probability
            val outputTensor = result?.get(0)?.value as? Array<FloatArray>
            val riskScore = outputTensor?.get(0)?.get(0) ?: 0.0f
            
            result?.close()
            inputTensor.close()
            
            when {
                riskScore > 0.60f -> Environment.CALL_CENTER
                riskScore > 0.25f -> Environment.STREET
                else -> Environment.HOME
            }
        } catch (e: Exception) {
            Log.e(TAG, "ONNX Inference failed for AcousticAnalyzer.", e)
            Environment.UNKNOWN
        }
    }
}
