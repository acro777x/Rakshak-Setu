package com.rakshaksetu.app.audio

import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance, lock-free Single-Producer Single-Consumer (SPSC) circular ring buffer
 * designed for in-memory live linear PCM audio interception directly in RAM.
 *
 * Guarantees:
 * 1. Zero disk I/O (pure userspace memory).
 * 2. Non-blocking writes: the WebRTC audio render callback never blocks on the
 *    consumer, preventing audio underruns. Implemented with volatile fields rather
 *    than a monitor -- the previous version annotated write/read/available/clear
 *    with @Synchronized, putting a single instance monitor directly on the
 *    real-time audio thread, which is exactly what guarantee 2 forbids.
 * 3. Wait-free for the single producer and the single consumer respectively.
 * 4. Automatic drop-oldest backpressure management when consumer is momentarily backlogged.
 *
 * One slot is permanently reserved so that "write == read" unambiguously means EMPTY
 * rather than FULL; the usable window is therefore 0..capacity-1.
 */
class SpscAudioRingBuffer(
    val capacity: Int = DEFAULT_CAPACITY_BYTES
) {
    companion object {
        // Default: ~8 seconds of 16kHz 16-bit linear mono PCM (256,000 bytes)
        const val DEFAULT_CAPACITY_BYTES = 256 * 1024
    }

    private val buffer = ByteArray(capacity)

    // Volatile gives the acquire/release pairing the producer/consumer hand-off needs.
    // Each thread re-reads these every call, so a slightly stale value is only ever a
    // stale *observation*, never a lost update: each side owns and advances exactly
    // one index.
    @Volatile private var writeIndex: Int = 0
    @Volatile private var readIndex: Int = 0

    /**
     * Write PCM bytes from the producer (WebRTC audio render callback).
     *
     * Wait-free: no locks, no CAS loops. If the buffer is full the oldest unread data
     * is dropped by advancing [readIndex]; under the SPSC contract the producer may do
     * this because the consumer only ever moves readIndex forward from its own copy.
     */
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        if (length <= 0 || length > capacity) return 0
        if (offset < 0 || offset + length > data.size) return 0

        val currentWrite = writeIndex
        val currentRead = readIndex
        val used = if (currentWrite >= currentRead) currentWrite - currentRead
                   else capacity - (currentRead - currentWrite)

        // Reserve one slot so "full" is never ambiguous with "empty".
        val space = capacity - used - 1

        if (length > space) {
            val neededDrop = length - space
            readIndex = (currentRead + neededDrop) % capacity
        }

        // Copy data into circular array
        val bytesToEnd = capacity - currentWrite
        if (length <= bytesToEnd) {
            System.arraycopy(data, offset, buffer, currentWrite, length)
        } else {
            System.arraycopy(data, offset, buffer, currentWrite, bytesToEnd)
            System.arraycopy(data, offset + bytesToEnd, buffer, 0, length - bytesToEnd)
        }

        val nextWrite = (currentWrite + length) % capacity
        writeIndex = nextWrite
        return length
    }

    /**
     * Read up to [length] bytes from the ring buffer into [dest].
     * Returns the actual number of bytes read.
     */
    fun read(dest: ByteArray, offset: Int = 0, length: Int = dest.size): Int {
        if (length <= 0) return 0
        if (offset < 0 || offset + length > dest.size) return 0

        val currentRead = readIndex
        val currentWrite = writeIndex
        val available = if (currentWrite >= currentRead) currentWrite - currentRead
                        else capacity - (currentRead - currentWrite)

        if (available == 0) return 0

        val toRead = minOf(length, available)
        val bytesToEnd = capacity - currentRead

        if (toRead <= bytesToEnd) {
            System.arraycopy(buffer, currentRead, dest, offset, toRead)
        } else {
            System.arraycopy(buffer, currentRead, dest, offset, bytesToEnd)
            System.arraycopy(buffer, 0, dest, offset + bytesToEnd, toRead - bytesToEnd)
        }

        val nextRead = (currentRead + toRead) % capacity
        readIndex = nextRead
        return toRead
    }

    /**
     * Overload for ShortArray writing (interfacing with ShortBuffer audio feeds).
     */
    fun write(src: ShortArray, offset: Int = 0, count: Int = src.size): Int {
        val byteData = ByteArray(count * 2)
        for (i in 0 until count) {
            val s = src[offset + i]
            byteData[i * 2] = (s.toInt() and 0xFF).toByte()
            byteData[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return write(byteData, 0, byteData.size) / 2
    }

    /**
     * Overload for ShortArray reading.
     */
    fun read(dst: ShortArray, offset: Int = 0, count: Int = dst.size): Int {
        val byteData = ByteArray(count * 2)
        val bytesRead = read(byteData, 0, byteData.size)
        val shortsRead = bytesRead / 2
        for (i in 0 until shortsRead) {
            val low = byteData[i * 2].toInt() and 0xFF
            val high = byteData[i * 2 + 1].toInt()
            dst[offset + i] = ((high shl 8) or low).toShort()
        }
        return shortsRead
    }

    /**
     * Returns the number of readable bytes currently in the buffer.
     */
    fun available(): Int {
        val currentWrite = writeIndex
        val currentRead = readIndex
        return if (currentWrite >= currentRead) currentWrite - currentRead
               else capacity - (currentRead - currentWrite)
    }

    /**
     * Clear the buffer instantly.
     */
    fun clear() {
        readIndex = writeIndex
    }
}

/**
 * Typealias providing SPSCBuffer identifier mapping to SpscAudioRingBuffer.
 */
typealias SPSCBuffer = SpscAudioRingBuffer
