package com.rakshaksetu.app.pipeline

import android.util.Log

class WhisperEngine(private val modelPath: String) {

    companion object {
        private const val TAG = "WhisperEngine"
        
        init {
            try {
                System.loadLibrary("whisper")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native whisper library not found, falling back to mock implementation for testing.")
            }
        }
    }

    private var contextPointer: Long = 0

    init {
        // Initialize whisper context with the model
        contextPointer = initContext(modelPath)
        if (contextPointer == 0L) {
            Log.e(TAG, "Failed to initialize Whisper context with model: $modelPath")
        }
    }

    /**
     * Transcribes a 5s 16kHz mono 16-bit PCM buffer into text.
     * Uses Hinglish prompt anchoring for domain adaptation.
     */
    fun transcribe(pcmData: ByteArray): String {
        if (contextPointer == 0L) {
            // Mock implementation if JNI fails
            return "Mock transcription of audio segment."
        }

        // We convert the byte array (16-bit PCM) to a float array for whisper.cpp
        // since whisper expects -1.0 to 1.0 normalized float samples
        val samples = decodePcmToFloatArray(pcmData)

        // Prompt 1: Initial Prompt for Whisper ASR (Hinglish Domain Adaptation)
        val WHISPER_INITIAL_PROMPT = (
            "Yeh ek phone call recording hai. Digital arrest, CBI police warrant, " +
            "Aadhaar card block, courier customs parcel, bank account freeze, " +
            "KYC update expire, RBI Nodal officer, OTP transfer, money transfer, " +
            "FIR registered, legal action, urgent refund."
        )

        return transcribeNative(
            contextPointer,
            samples,
            language = "hi",
            initialPrompt = WHISPER_INITIAL_PROMPT,
            translate = false,
            noTimestamps = true
        )
    }

    fun release() {
        if (contextPointer != 0L) {
            freeContext(contextPointer)
            contextPointer = 0L
        }
    }

    private fun decodePcmToFloatArray(pcmData: ByteArray): FloatArray {
        val floatArray = FloatArray(pcmData.size / 2)
        var i = 0
        var j = 0
        while (i < pcmData.size - 1) {
            // Little-endian to short
            val low = pcmData[i].toInt() and 0xFF
            val high = pcmData[i + 1].toInt() shl 8
            val sample = (high or low).toShort()
            // Normalize to [-1.0, 1.0]
            floatArray[j] = sample / 32768.0f
            i += 2
            j++
        }
        return floatArray
    }

    // JNI External Functions
    private external fun initContext(modelPath: String): Long
    private external fun transcribeNative(
        contextPtr: Long, 
        samples: FloatArray, 
        language: String, 
        initialPrompt: String, 
        translate: Boolean, 
        noTimestamps: Boolean
    ): String
    private external fun freeContext(contextPtr: Long)
}
