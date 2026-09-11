package com.rakshaksetu.voip

import com.rakshaksetu.voip.ai.SpscAudioRingBuffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * E2E & Unit Test Suite for SpscAudioRingBuffer across Tiers 1 & 2.
 *
 * Verifies:
 * - Tier 1: Read/write happy paths, available calculation, clear, and realistic streaming chunks.
 * - Tier 2: Wraparound across boundaries, drop-oldest backpressure under overflow, empty/zero bounds,
 *   offset handling, and multi-iteration circular stress.
 */
class SpscRingBufferTest {

    private lateinit var ringBuffer: SpscAudioRingBuffer

    @Before
    fun setUp() {
        ringBuffer = SpscAudioRingBuffer()
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (HAPPY PATHS & FUNCTIONAL INVARIANTS)
    // =========================================================================

    @Test
    fun testDefaultCapacityInitialization() {
        assertEquals(SpscAudioRingBuffer.DEFAULT_CAPACITY_BYTES, ringBuffer.capacity)
        assertEquals(256 * 1024, ringBuffer.capacity)
        assertEquals(0, ringBuffer.available())
    }

    @Test
    fun testCustomCapacityInitialization() {
        val customBuffer = SpscAudioRingBuffer(capacity = 4096)
        assertEquals(4096, customBuffer.capacity)
        assertEquals(0, customBuffer.available())
    }

    @Test
    fun testSequentialWriteAndReadExact() {
        val testData = ByteArray(500) { (it % 128).toByte() }
        val written = ringBuffer.write(testData)
        assertEquals(500, written)
        assertEquals(500, ringBuffer.available())

        val readBuffer = ByteArray(500)
        val readCount = ringBuffer.read(readBuffer)
        assertEquals(500, readCount)
        assertEquals(0, ringBuffer.available())
        assertArrayEquals(testData, readBuffer)
    }

    @Test
    fun testPartialReadPreservesRemaining() {
        val testData = ByteArray(300) { (it * 3 % 256).toByte() }
        ringBuffer.write(testData)
        assertEquals(300, ringBuffer.available())

        val firstSlice = ByteArray(100)
        val firstRead = ringBuffer.read(firstSlice, 0, 100)
        assertEquals(100, firstRead)
        assertEquals(200, ringBuffer.available())
        assertArrayEquals(testData.copyOfRange(0, 100), firstSlice)

        val secondSlice = ByteArray(200)
        val secondRead = ringBuffer.read(secondSlice, 0, 200)
        assertEquals(200, secondRead)
        assertEquals(0, ringBuffer.available())
        assertArrayEquals(testData.copyOfRange(100, 300), secondSlice)
    }

    @Test
    fun testClearResetsBuffer() {
        val testData = ByteArray(1024) { 0x5A }
        ringBuffer.write(testData)
        assertEquals(1024, ringBuffer.available())

        ringBuffer.clear()
        assertEquals(0, ringBuffer.available())

        val readBuffer = ByteArray(1024)
        val readCount = ringBuffer.read(readBuffer)
        assertEquals(0, readCount)
    }

    @Test
    fun testRealisticStreamingChunks() {
        // Realistic 100ms audio chunks at 16kHz 16-bit mono linear PCM = 3200 bytes per chunk
        val chunkSize = 3200
        val totalChunks = 20

        var totalBytesRead = 0
        for (i in 0 until totalChunks) {
            val chunk = ByteArray(chunkSize) { (it + i).toByte() }
            val written = ringBuffer.write(chunk)
            assertEquals(chunkSize, written)

            val dest = ByteArray(chunkSize)
            val read = ringBuffer.read(dest)
            assertEquals(chunkSize, read)
            assertArrayEquals("Chunk $i mismatch", chunk, dest)
            totalBytesRead += read
        }

        assertEquals(chunkSize * totalChunks, totalBytesRead)
        assertEquals(0, ringBuffer.available())
    }

    // =========================================================================
    // TIER 2: BOUNDARY, CORNER CASES & STRESS INVARIANTS
    // =========================================================================

    @Test
    fun testReadFromEmptyBufferReturnsZero() {
        val dest = ByteArray(64) { 0xFF.toByte() }
        val readCount = ringBuffer.read(dest)
        assertEquals(0, readCount)
        // Verify destination buffer was untouched
        assertTrue(dest.all { it == 0xFF.toByte() })
    }

    @Test
    fun testZeroAndNegativeLengthWrite() {
        val data = ByteArray(32) { 0x12 }
        assertEquals(0, ringBuffer.write(data, 0, 0))
        assertEquals(0, ringBuffer.write(data, 0, -5))
        assertEquals(0, ringBuffer.available())
    }

    @Test
    fun testWriteExceedingTotalCapacityReturnsZero() {
        val smallBuffer = SpscAudioRingBuffer(capacity = 256)
        val oversizedData = ByteArray(300) { 0x01 }
        val written = smallBuffer.write(oversizedData)
        assertEquals(0, written)
        assertEquals(0, smallBuffer.available())
    }

    @Test
    fun testReadWithZeroOrNegativeLengthReturnsZero() {
        ringBuffer.write(ByteArray(64) { 0x33 })
        val dest = ByteArray(64)
        assertEquals(0, ringBuffer.read(dest, 0, 0))
        assertEquals(0, ringBuffer.read(dest, 0, -10))
        assertEquals(64, ringBuffer.available())
    }

    @Test
    fun testReadMoreThanAvailableReturnsAvailable() {
        val data = ByteArray(75) { (it + 1).toByte() }
        ringBuffer.write(data)
        assertEquals(75, ringBuffer.available())

        val dest = ByteArray(200)
        val readCount = ringBuffer.read(dest, 0, 200)
        assertEquals(75, readCount)
        assertEquals(0, ringBuffer.available())

        for (i in 0 until 75) {
            assertEquals((i + 1).toByte(), dest[i])
        }
    }

    @Test
    fun testWriteAndReadOffsets() {
        val source = ByteArray(100) { it.toByte() }
        // Write only 40 bytes starting at index 20 (source[20..59])
        val written = ringBuffer.write(source, offset = 20, length = 40)
        assertEquals(40, written)
        assertEquals(40, ringBuffer.available())

        val dest = ByteArray(100) { 0 }
        // Read into dest starting at index 10
        val readCount = ringBuffer.read(dest, offset = 10, length = 40)
        assertEquals(40, readCount)
        assertEquals(0, ringBuffer.available())

        for (i in 0 until 40) {
            assertEquals((20 + i).toByte(), dest[10 + i])
        }
    }

    @Test
    fun testCircularWraparoundWithoutLoss() {
        // Use a compact buffer of 100 bytes to force multiple head/tail wraparounds
        val smallBuffer = SpscAudioRingBuffer(capacity = 100)

        // Write 60 bytes and read 60 bytes -> writeIndex=60, readIndex=60
        val chunkA = ByteArray(60) { it.toByte() }
        smallBuffer.write(chunkA)
        val readA = ByteArray(60)
        smallBuffer.read(readA)
        assertArrayEquals(chunkA, readA)
        assertEquals(0, smallBuffer.available())

        // Now write 60 bytes -> wraps past 100 back to index 20
        val chunkB = ByteArray(60) { (it + 100).toByte() }
        val writtenB = smallBuffer.write(chunkB)
        assertEquals(60, writtenB)
        assertEquals(60, smallBuffer.available())

        val readB = ByteArray(60)
        val readCountB = smallBuffer.read(readB)
        assertEquals(60, readCountB)
        assertArrayEquals(chunkB, readB)
        assertEquals(0, smallBuffer.available())
    }

    @Test
    fun testDropOldestBackpressureOnOverflow() {
        // Buffer capacity = 100 bytes
        val smallBuffer = SpscAudioRingBuffer(capacity = 100)

        // Fill with 90 bytes (values 0..89)
        val initialData = ByteArray(90) { it.toByte() }
        smallBuffer.write(initialData)
        assertEquals(90, smallBuffer.available())

        // Overflow: attempt to write 30 new bytes (values 100..129)
        // Space remaining was 100 - 90 - 1 = 9 bytes.
        // Drop needed = 30 - 9 = 21 bytes.
        // Oldest 21 bytes (0..20) are dropped, advancing read index.
        val overflowData = ByteArray(30) { (100 + it).toByte() }
        val written = smallBuffer.write(overflowData)
        assertEquals(30, written)

        // Available bytes is now (90 - 21) + 30 = 69 + 30 = 99
        assertEquals(99, smallBuffer.available())

        val result = ByteArray(99)
        val readCount = smallBuffer.read(result)
        assertEquals(99, readCount)

        // The first 69 bytes must be the retained initial data (21..89)
        for (i in 0 until 69) {
            assertEquals((21 + i).toByte(), result[i])
        }
        // The last 30 bytes must be the newly written overflow data (100..129)
        for (i in 0 until 30) {
            assertEquals((100 + i).toByte(), result[69 + i])
        }
    }

    @Test
    fun testMultiIterationCircularStress() {
        val stressBuffer = SpscAudioRingBuffer(capacity = 1024)
        val random = java.util.Random(42)

        for (iteration in 0 until 2000) {
            val size = random.nextInt(256) + 1
            val data = ByteArray(size) { (it xor iteration).toByte() }

            val written = stressBuffer.write(data)
            assertEquals(size, written)

            val dest = ByteArray(size)
            val read = stressBuffer.read(dest)
            assertEquals(size, read)
            assertArrayEquals("Iteration $iteration failed integrity check", data, dest)
            assertEquals(0, stressBuffer.available())
        }
    }

    @Test
    fun testConcurrentProducerConsumerThreadSafety() {
        val buffer = SpscAudioRingBuffer(capacity = 8192)
        val numChunks = 200
        val chunkSize = 64
        val totalBytes = numChunks * chunkSize
        val producerFinished = java.util.concurrent.atomic.AtomicBoolean(false)
        val totalBytesRead = java.util.concurrent.atomic.AtomicInteger(0)

        val producer = Thread {
            val chunk = ByteArray(chunkSize) { 0x42 }
            for (i in 0 until numChunks) {
                buffer.write(chunk)
                Thread.sleep(1)
            }
            producerFinished.set(true)
        }

        val consumer = Thread {
            val readBuf = ByteArray(128)
            while (!producerFinished.get() || buffer.available() > 0) {
                val n = buffer.read(readBuf)
                totalBytesRead.addAndGet(n)
                if (n == 0) Thread.sleep(1)
            }
        }

        producer.start()
        consumer.start()
        producer.join(5000)
        consumer.join(5000)

        assertFalse("Producer thread hung", producer.isAlive)
        assertFalse("Consumer thread hung", consumer.isAlive)
        assertTrue("Expected bytes read > 0", totalBytesRead.get() > 0)
        assertTrue("Expected bytes read <= total bytes written", totalBytesRead.get() <= totalBytes)
    }
}
