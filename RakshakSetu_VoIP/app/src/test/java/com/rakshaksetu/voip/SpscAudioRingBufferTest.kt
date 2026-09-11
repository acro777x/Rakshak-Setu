package com.rakshaksetu.voip

import com.rakshaksetu.voip.ai.SpscAudioRingBuffer
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for the lock-free Single-Producer Single-Consumer (SPSC) circular audio ring buffer.
 */
class SpscAudioRingBufferTest {

    @Test
    fun testBasicWriteAndRead() {
        val buffer = SpscAudioRingBuffer(capacity = 1024)
        val testData = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80)

        val written = buffer.write(testData)
        assertEquals(testData.size, written)
        assertEquals(testData.size, buffer.available())

        val readDest = ByteArray(8)
        val readCount = buffer.read(readDest)
        assertEquals(8, readCount)
        assertArrayEquals(testData, readDest)
        assertEquals(0, buffer.available())
    }

    @Test
    fun testCircularWrapAround() {
        val buffer = SpscAudioRingBuffer(capacity = 16)
        val chunk1 = ByteArray(10) { it.toByte() }
        val chunk2 = ByteArray(10) { (it + 50).toByte() }

        // Write 10 bytes, read 8 bytes
        buffer.write(chunk1)
        val dest1 = ByteArray(8)
        buffer.read(dest1)

        // Write 10 more bytes (crosses wrap boundary)
        buffer.write(chunk2)

        val dest2 = ByteArray(12)
        val totalRead = buffer.read(dest2)
        assertTrue(totalRead > 0)
    }

    @Test
    fun testBackpressureDropOldest() {
        // Capacity 16: if we push 25 bytes without reading, it should drop oldest without crash
        val buffer = SpscAudioRingBuffer(capacity = 16)
        val bigData = ByteArray(14) { 1 }
        buffer.write(bigData)

        val moreData = ByteArray(10) { 2 }
        buffer.write(moreData)

        // Buffer must not overflow capacity
        assertTrue(buffer.available() < 16)

        val out = ByteArray(buffer.available())
        val count = buffer.read(out)
        assertEquals(out.size, count)
        // Most recent data must be preserved
        assertEquals(2, out.last().toInt())
    }

    @Test
    fun testClear() {
        val buffer = SpscAudioRingBuffer(capacity = 100)
        buffer.write(ByteArray(50) { 5 })
        assertEquals(50, buffer.available())

        buffer.clear()
        assertEquals(0, buffer.available())
    }
}
