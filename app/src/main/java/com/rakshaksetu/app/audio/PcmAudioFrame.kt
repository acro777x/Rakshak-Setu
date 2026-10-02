package com.rakshaksetu.app.audio

/**
 * Encapsulates an in-memory decoded linear PCM audio frame tapped directly from RAM.
 *
 * @property data Raw 16-bit linear PCM byte payload
 * @property sampleRate Sampling rate in Hz (default: 16000 Hz)
 * @property channels Channel count (default: 1 mono)
 * @property timestampNs Monotonic system timestamp in nanoseconds
 */
data class PcmAudioFrame(
    val data: ByteArray,
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    val channels: Int = DEFAULT_CHANNELS,
    val timestampNs: Long = System.nanoTime()
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 16000
        const val DEFAULT_CHANNELS = 1
        const val BYTES_PER_SAMPLE = 2
    }

    val sampleCount: Int
        get() = data.size / (BYTES_PER_SAMPLE * channels)

    val durationMs: Double
        get() = (sampleCount.toDouble() / sampleRate) * 1000.0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PcmAudioFrame

        if (!data.contentEquals(other.data)) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (timestampNs != other.timestampNs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + timestampNs.hashCode()
        return result
    }
}
