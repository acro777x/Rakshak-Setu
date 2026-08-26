package com.rakshaksetu.app.pipeline

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Real spectral audio feature extraction for voice clone / deepfake detection.
 * Extracts LFCC (Linear Frequency Cepstral Coefficients) and Mel Spectrogram
 * from 16-bit PCM audio using proper FFT computation.
 *
 * Replaces the fake AcousticAnalyzer that read raw bytes as "MFCCs".
 */
object SpectralFeatureExtractor {
    private const val TAG = "SpectralFeatures"
    private const val SAMPLE_RATE = 16000
    private const val FFT_SIZE = 512
    private const val HOP_SIZE = 160       // 10ms hop
    private const val NUM_LFCC = 60        // AASIST expects 60 LFCC coefficients
    private const val NUM_MEL_BINS = 80
    private const val NUM_LINEAR_BINS = 60

    /**
     * Extract LFCC features from PCM audio for AASIST deepfake detection.
     * Returns [numFrames x NUM_LFCC] feature matrix as flat FloatArray.
     */
    fun extractLFCC(pcmData: ByteArray): FloatArray {
        val samples = pcmToFloat(pcmData)
        if (samples.size < FFT_SIZE) {
            Log.w(TAG, "Audio too short for LFCC extraction: ${samples.size} samples")
            return FloatArray(NUM_LFCC) // single zero frame
        }

        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE + 1
        val features = FloatArray(numFrames * NUM_LFCC)

        for (frame in 0 until numFrames) {
            val startIdx = frame * HOP_SIZE
            val windowed = hammingWindow(samples, startIdx, FFT_SIZE)
            val spectrum = computePowerSpectrum(windowed)
            val linearFilterbank = applyLinearFilterbank(spectrum)
            val logEnergy = linearFilterbank.map { ln(it.coerceAtLeast(1e-10f)) }
            val lfcc = dct(logEnergy, NUM_LFCC)

            System.arraycopy(lfcc, 0, features, frame * NUM_LFCC, NUM_LFCC)
        }

        return features
    }

    /**
     * Extract raw audio features suitable for RawNet-style models.
     * Returns normalized float samples directly (model handles its own features).
     */
    fun extractRawWaveform(pcmData: ByteArray, targetLength: Int = 16000): FloatArray {
        val samples = pcmToFloat(pcmData)
        val result = FloatArray(targetLength)
        val copyLen = minOf(samples.size, targetLength)
        System.arraycopy(samples, 0, result, 0, copyLen)
        return result
    }

    /**
     * Compute speech rate estimate (syllables per second).
     * Scammers typically speak faster (>4 syllables/s) than normal conversation.
     */
    fun estimateSpeechRate(pcmData: ByteArray): Float {
        val samples = pcmToFloat(pcmData)
        if (samples.isEmpty()) return 0f

        // Energy envelope smoothing
        val frameSize = SAMPLE_RATE / 100 // 10ms frames
        val numFrames = samples.size / frameSize
        if (numFrames < 2) return 0f

        val energy = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            var sum = 0.0f
            for (j in 0 until frameSize) {
                val idx = i * frameSize + j
                if (idx < samples.size) {
                    sum += samples[idx] * samples[idx]
                }
            }
            energy[i] = sqrt(sum / frameSize)
        }

        // Count energy peaks (proxy for syllable nuclei)
        val threshold = energy.average().toFloat() * 0.5f
        var peaks = 0
        var wasAbove = false
        for (e in energy) {
            val isAbove = e > threshold
            if (isAbove && !wasAbove) peaks++
            wasAbove = isAbove
        }

        val durationSec = samples.size.toFloat() / SAMPLE_RATE
        return if (durationSec > 0) peaks / durationSec else 0f
    }

    /**
     * Compute silence ratio of audio segment.
     * Scam calls typically have <15% silence (scripted monologue).
     */
    fun computeSilenceRatio(pcmData: ByteArray): Float {
        val samples = pcmToFloat(pcmData)
        if (samples.isEmpty()) return 1f

        val threshold = 0.02f // -34dB silence threshold
        val silentSamples = samples.count { abs(it) < threshold }
        return silentSamples.toFloat() / samples.size
    }

    // ========== Internal FFT / Feature Computation ==========

    private fun pcmToFloat(pcmData: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        val shorts = buffer.asShortBuffer()
        val result = FloatArray(shorts.capacity())
        for (i in 0 until shorts.capacity()) {
            result[i] = shorts.get(i) / 32768.0f
        }
        return result
    }

    private fun hammingWindow(samples: FloatArray, offset: Int, size: Int): FloatArray {
        val windowed = FloatArray(size)
        for (i in 0 until size) {
            val idx = offset + i
            val sample = if (idx < samples.size) samples[idx] else 0f
            windowed[i] = sample * (0.54f - 0.46f * cos(2.0f * PI.toFloat() * i / (size - 1)))
        }
        return windowed
    }

    /**
     * Compute power spectrum using radix-2 FFT.
     * Returns |FFT|^2 for first FFT_SIZE/2+1 bins.
     */
    private fun computePowerSpectrum(frame: FloatArray): FloatArray {
        val n = frame.size
        val real = frame.copyOf(n)
        val imag = FloatArray(n)

        // Radix-2 Cooley-Tukey FFT
        fftInPlace(real, imag)

        val specSize = n / 2 + 1
        val spectrum = FloatArray(specSize)
        for (i in 0 until specSize) {
            spectrum[i] = real[i] * real[i] + imag[i] * imag[i]
        }
        return spectrum
    }

    private fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        // Bit reversal
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp
            }
        }

        // FFT butterfly
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * PI / len
            for (i in 0 until n step len) {
                for (k in 0 until halfLen) {
                    val thetaK = angle * k
                    val cosK = cos(thetaK).toFloat()
                    val sinK = sin(thetaK).toFloat()
                    val tReal = real[i + k + halfLen] * cosK - imag[i + k + halfLen] * sinK
                    val tImag = real[i + k + halfLen] * sinK + imag[i + k + halfLen] * cosK
                    real[i + k + halfLen] = real[i + k] - tReal
                    imag[i + k + halfLen] = imag[i + k] - tImag
                    real[i + k] += tReal
                    imag[i + k] += tImag
                }
            }
            len = len shl 1
        }
    }

    /**
     * Linear filterbank for LFCC (uniformly spaced in Hz, not Mel-scaled).
     */
    private fun applyLinearFilterbank(spectrum: FloatArray): FloatArray {
        val numBins = spectrum.size
        val result = FloatArray(NUM_LINEAR_BINS)
        val binWidth = numBins.toFloat() / NUM_LINEAR_BINS

        for (i in 0 until NUM_LINEAR_BINS) {
            val startBin = (i * binWidth).toInt()
            val endBin = minOf(((i + 1) * binWidth).toInt(), numBins)
            var sum = 0f
            for (b in startBin until endBin) {
                sum += spectrum[b]
            }
            result[i] = sum / maxOf(1, endBin - startBin)
        }
        return result
    }

    /**
     * Type-II Discrete Cosine Transform for cepstral coefficients.
     */
    private fun dct(input: List<Float>, numCoeffs: Int): FloatArray {
        val n = input.size
        val result = FloatArray(numCoeffs)
        for (k in 0 until numCoeffs) {
            var sum = 0.0f
            for (i in 0 until n) {
                sum += input[i] * cos(PI.toFloat() * k * (2 * i + 1) / (2 * n))
            }
            result[k] = sum
        }
        return result
    }
}
