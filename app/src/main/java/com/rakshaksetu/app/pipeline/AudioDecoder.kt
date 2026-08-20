package com.rakshaksetu.app.pipeline

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {
    private const val TAG = "NativeAudioDecoder"
    private const val TIMEOUT_US = 5000L
    private const val TARGET_SAMPLE_RATE = 16000
    private const val TARGET_CHANNELS = 1

    /**
     * Decodes any audio file (AAC, M4A, MP3, AMR, OGG, WAV) to 16kHz, mono, 16-bit PCM WAV.
     * Uses Android's native hardware-accelerated MediaExtractor + MediaCodec pipeline.
     */
    suspend fun decodeToWav(srcPath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        val srcFile = File(srcPath)
        if (!srcFile.exists() || srcFile.length() == 0L) {
            Log.e(TAG, "Source audio file does not exist or is empty: $srcPath")
            return@withContext false
        }

        val destFile = File(destPath)
        if (destFile.exists()) destFile.delete()
        destFile.parentFile?.mkdirs()

        Log.i(TAG, "Decoding $srcPath -> $destPath using hardware MediaCodec...")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var fos: FileOutputStream? = null

        try {
            extractor.setDataSource(srcPath)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = f
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                Log.e(TAG, "No audio track found in $srcPath")
                return@withContext false
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext false
            val srcSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 16000
            val srcChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            fos = FileOutputStream(destFile)
            // Write placeholder 44-byte WAV header
            val headerPlaceholder = ByteArray(44)
            fos.write(headerPlaceholder)

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var totalPcmBytesWritten = 0L

            var actualSampleRate = srcSampleRate
            var actualChannels = srcChannels

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufIndex >= 0) {
                        val inputBuf = codec.getInputBuffer(inputBufIndex) ?: continue
                        inputBuf.clear()
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufIndex >= 0) {
                    val outputBuf = codec.getOutputBuffer(outputBufIndex)
                    if (outputBuf != null && bufferInfo.size > 0) {
                        outputBuf.position(bufferInfo.offset)
                        outputBuf.limit(bufferInfo.offset + bufferInfo.size)

                        val chunk = ByteArray(bufferInfo.size)
                        outputBuf.get(chunk)

                        // Process PCM: convert to 16kHz mono if needed
                        val processedPcm = convertPcmTo16kMono(
                            rawPcm = chunk,
                            inSampleRate = actualSampleRate,
                            inChannels = actualChannels
                        )

                        if (processedPcm.isNotEmpty()) {
                            fos.write(processedPcm)
                            totalPcmBytesWritten += processedPcm.size
                        }
                    }

                    codec.releaseOutputBuffer(outputBufIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        actualSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        actualChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    Log.d(TAG, "Decoder output format changed: $actualSampleRate Hz, $actualChannels channels")
                }
            }

            fos.flush()
            fos.close()
            fos = null

            // Write final WAV Header with accurate byte counts
            writeWavHeader(destFile, totalPcmBytesWritten, TARGET_SAMPLE_RATE, TARGET_CHANNELS)
            Log.i(TAG, "Audio successfully decoded: ${destFile.length()} bytes written ($totalPcmBytesWritten PCM bytes)")
            return@withContext totalPcmBytesWritten > 0

        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec decoding error for $srcPath", e)
            return@withContext false
        } finally {
            try { fos?.close() } catch (ignored: Exception) {}
            try { codec?.stop() } catch (ignored: Exception) {}
            try { codec?.release() } catch (ignored: Exception) {}
            try { extractor.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * Converts 16-bit PCM buffer from source channels/rate to 16kHz Mono 16-bit PCM.
     */
    private fun convertPcmTo16kMono(rawPcm: ByteArray, inSampleRate: Int, inChannels: Int): ByteArray {
        if (rawPcm.isEmpty()) return ByteArray(0)

        // Step 1: Downmix channels to Mono (16-bit Little Endian)
        val shortCount = rawPcm.size / 2
        val srcShorts = ShortArray(shortCount)
        ByteBuffer.wrap(rawPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(srcShorts)

        val monoShorts: ShortArray
        if (inChannels > 1) {
            val frameCount = shortCount / inChannels
            monoShorts = ShortArray(frameCount)
            for (i in 0 until frameCount) {
                var sum = 0
                for (c in 0 until inChannels) {
                    sum += srcShorts[i * inChannels + c]
                }
                monoShorts[i] = (sum / inChannels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        } else {
            monoShorts = srcShorts
        }

        // Step 2: Resample to 16kHz if needed (Linear Interpolation)
        val finalShorts: ShortArray
        if (inSampleRate != TARGET_SAMPLE_RATE && inSampleRate > 0) {
            val ratio = inSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
            val outCount = (monoShorts.size / ratio).toInt()
            finalShorts = ShortArray(outCount)
            for (i in 0 until outCount) {
                val srcIdx = i * ratio
                val i0 = srcIdx.toInt().coerceIn(0, monoShorts.size - 1)
                val i1 = (i0 + 1).coerceIn(0, monoShorts.size - 1)
                val frac = srcIdx - i0
                val interp = (monoShorts[i0] * (1.0 - frac) + monoShorts[i1] * frac).toInt()
                finalShorts[i] = interp.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        } else {
            finalShorts = monoShorts
        }

        // Convert ShortArray back to Little Endian Byte Array
        val outBytes = ByteArray(finalShorts.size * 2)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(finalShorts)
        return outBytes
    }

    /**
     * Overwrites 44-byte RIFF header in place at the start of the WAV file.
     */
    private fun writeWavHeader(file: File, pcmDataLength: Long, sampleRate: Int, channels: Int) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * 2 // 16-bit = 2 bytes per sample

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.writeBytes("RIFF")
            raf.writeInt(Integer.reverseBytes(totalDataLen.toInt()))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeInt(Integer.reverseBytes(16)) // Subchunk1Size for PCM
            raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // AudioFormat 1 = PCM
            raf.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
            raf.writeInt(Integer.reverseBytes(sampleRate))
            raf.writeInt(Integer.reverseBytes(byteRate))
            raf.writeShort(java.lang.Short.reverseBytes((channels * 2).toShort()).toInt()) // BlockAlign
            raf.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt()) // BitsPerSample
            raf.writeBytes("data")
            raf.writeInt(Integer.reverseBytes(pcmDataLength.toInt()))
        }
    }
}
