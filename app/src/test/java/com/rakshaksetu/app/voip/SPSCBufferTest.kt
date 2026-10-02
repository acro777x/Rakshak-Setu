package com.rakshaksetu.app.voip

import com.rakshaksetu.app.audio.SPSCBuffer
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test for 256KB SPSCBuffer alias and functionality in unified app.
 */
class SPSCBufferTest {

    @Test
    fun testSpscBuffer256KbCapacity() {
        val buffer = SPSCBuffer()
        assertEquals(256 * 1024, buffer.capacity)
        assertEquals(0, buffer.available())
    }

    @Test
    fun testSpscBufferWriteAndRead() {
        val buffer = SPSCBuffer(capacity = 2048)
        val testData = ByteArray(256) { (it and 0xFF).toByte() }
        val written = buffer.write(testData)
        assertEquals(256, written)
        assertEquals(256, buffer.available())

        val readData = ByteArray(256)
        val read = buffer.read(readData)
        assertEquals(256, read)
        assertArrayEquals(testData, readData)
        assertEquals(0, buffer.available())
    }

    @Test
    fun testSpscBufferShortArrayOverload() {
        val buffer = SPSCBuffer(capacity = 1024)
        val shorts = ShortArray(100) { (it * 10).toShort() }
        val written = buffer.write(shorts)
        assertEquals(100, written)

        val outShorts = ShortArray(100)
        val read = buffer.read(outShorts)
        assertEquals(100, read)
        assertArrayEquals(shorts, outShorts)
    }
}
