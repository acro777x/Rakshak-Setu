package com.rakshaksetu.app.pipeline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hardware-accelerated decoder normalizing any call recording (AMR/M4A/AAC/MP3/OGG/WAV)
 * to 16kHz mono 16-bit PCM WAV for the AI pipeline.
 *
 * Android 14/15 correctness:
 *  - Accepts content:// URIs directly via MediaExtractor's platform datasource
 *    (never stringifies URIs into filesystem paths).
 *  - Handles decoders emitting ENCODING_PCM_FLOAT output (common on modern AAC paths).
 *  - Caps pathological inputs (>90 minutes) to bound CPU/battery cost.
 */
object AudioDecoder {
    private const val TAG = "NativeAudioDecoder"
    private const val TIMEOUT_US = 10_000L
    private const val TARGET_SAMPLE_RATE = 16000
    private const val TARGET_CHANNELS = 1
    private const val WAV_HEADER_SIZE = 44
    private const val MAX_DECODE_DURATION_MS = 5_400_000L // 90 minutes

    /** Primary entry: decode straight from a MediaStore / file URI. */
    suspend fun decodeToWav(context: Context, srcUri: Uri, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            when {
                srcUri.scheme == "content" -> decodeInternal(
                    openExtractor = { extractor ->
                        extractor.setDataSource(context, srcUri, null)
                    },
                    destPath = destPath,
                    srcLabel = srcUri.toString()
                )
                else -> decodeFromPath(srcUri.path ?: "", destPath)
            }
        }

    /** Legacy entry retained for cache-file decoding. */
    suspend fun decodeToWav(srcPath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        decodeFromPath(srcPath, destPath)
    }

    private suspend fun decodeFromPath(srcPath: String, destPath: String): Boolean {
        if (srcPath.isBlank()) return false
        return decodeInternal(
            openExtractor = { extractor -> extractor.setDataSource(srcPath) },
            destPath = destPath,
            srcLabel = srcPath
        )
    }

    private fun decodeInternal(
        openExtractor: (MediaExtractor) -> Unit,
        destPath: String,
        srcLabel: String
    ): Boolean {
        val srcFile = File(destPath)
        if (destPath.isBlank()) return false

        val destFile = File(destPath)
        if (destFile.exists()) destFile.delete()
        destFile.parentFile?.mkdirs()

        Log.i(TAG, "Decoding $srcLabel -> $destPath using hardware MediaCodec...")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var fos: FileOutputStream? = null

        try {
            openExtractor(extractor)

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
                Log.e(TAG, "No audio track found in $srcLabel")
                return false
            }

            val trackDurationMs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION) / 1000L
            } else 0L
            if (trackDurationMs > MAX_DECODE_DURATION_MS) {
                Log.w(TAG, "Recording too long (${trackDurationMs}ms). Skipping decode.")
                return false
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            fos = FileOutputStream(destFile)
            fos.write(ByteArray(WAV_HEADER_SIZE))

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var totalPcmBytesWritten = 0L

            var actualSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else TARGET_SAMPLE_RATE
            var actualChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1
            var pcmEncoding = if (inputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                inputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else android.media.AudioFormat.ENCODING_PCM_16BIT

            var consecutiveTimeouts = 0

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufIndex >= 0) {
                        val inputBuf = codec.getInputBuffer(inputBufIndex)
                        if (inputBuf == null) {
                            continue
                        }
                        inputBuf.clear()
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                        consecutiveTimeouts = 0
                    }
                }

                val outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufIndex >= 0 -> {
                        consecutiveTimeouts = 0
                        if (bufferInfo.size > 0) {
                            val outputBuf = codec.getOutputBuffer(outputBufIndex)
                            if (outputBuf != null) {
                                outputBuf.position(bufferInfo.offset)
                                outputBuf.limit(bufferInfo.offset + bufferInfo.size)

                                val processedPcm = normalizeTo16kMono16bit(
                                    rawPcm = outputBuf,
                                    inSampleRate = actualSampleRate,
                                    inChannels = actualChannels,
                                    encoding = pcmEncoding
                                )

                                if (processedPcm.isNotEmpty()) {
                                    fos.write(processedPcm)
                                    totalPcmBytesWritten += processedPcm.size
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputBufIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }
                    }
                    outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            actualSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            actualChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        Log.d(TAG, "Decoder output format: $actualSampleRate Hz, $actualChannels ch, enc=$pcmEncoding")
                    }
                    outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        consecutiveTimeouts++
                        if (consecutiveTimeouts > 2000) {
                            Log.e(TAG, "Decoder stalled (2000 timeouts). Aborting.")
                            break
                        }
                    }
                }
            }

            fos.flush()
            fos.close()
            fos = null

            writeWavHeader(destFile, totalPcmBytesWritten, TARGET_SAMPLE_RATE, TARGET_CHANNELS)
            Log.i(TAG, "Decoded OK: ${destFile.length()} bytes ($totalPcmBytesWritten PCM bytes)")
            return totalPcmBytesWritten > 0
        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec decoding error for $srcLabel", e)
            return false
        } finally {
            try { fos?.close() } catch (ignored: Exception) {}
            try { codec?.stop() } catch (ignored: Exception) {}
            try { codec?.release() } catch (ignored: Exception) {}
            try { extractor.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * Downmixes + resamples any supported PCM encoding to 16kHz mono 16-bit.
     */
    internal fun normalizeTo16kMono16bit(
        rawPcm: ByteBuffer,
        inSampleRate: Int,
        inChannels: Int,
        encoding: Int
    ): ByteArray {
        val shorts: ShortArray = when (encoding) {
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                val floatCount = rawPcm.remaining() / 4
                val fb = rawPcm.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                val out = ShortArray(floatCount)
                for (i in 0 until floatCount) {
                    out[i] = ((fb.get(i).coerceIn(-1f, 1f)) * Short.MAX_VALUE).toInt().toShort()
                }
                out
            }
            android.media.AudioFormat.ENCODING_PCM_8BIT -> {
                val count = rawPcm.remaining()
                val out = ShortArray(count)
                for (i in 0 until count) {
                    out[i] = (((rawPcm.get(i).toInt() and 0xFF) - 128) * 257).toShort()
                }
                out
            }
            else -> {
                val shortCount = rawPcm.remaining() / 2
                val out = ShortArray(shortCount)
                rawPcm.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
                out
            }
        }

        return resampleAndDownmix(shorts, inSampleRate, inChannels)
    }

    private fun resampleAndDownmix(srcShorts: ShortArray, inSampleRate: Int, inChannels: Int): ByteArray {
        if (srcShorts.isEmpty()) return ByteArray(0)

        val monoShorts: ShortArray = if (inChannels > 1) {
            val frameCount = srcShorts.size / inChannels
            ShortArray(frameCount).also { mono ->
                for (i in 0 until frameCount) {
                    var sum = 0
                    for (c in 0 until inChannels) sum += srcShorts[i * inChannels + c]
                    mono[i] = (sum / inChannels)
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        } else {
            srcShorts
        }

        val finalShorts: ShortArray = if (inSampleRate != TARGET_SAMPLE_RATE && inSampleRate > 0) {
            val ratio = inSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
            val outCount = (monoShorts.size / ratio).toInt()
            ShortArray(outCount).also { finalS ->
                for (i in 0 until outCount) {
                    val srcIdx = i * ratio
                    val i0 = srcIdx.toInt().coerceIn(0, monoShorts.size - 1)
                    val i1 = (i0 + 1).coerceIn(0, monoShorts.size - 1)
                    val frac = srcIdx - i0
                    val interp = (monoShorts[i0] * (1.0 - frac) + monoShorts[i1] * frac).toInt()
                    finalS[i] = interp.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        } else {
            monoShorts
        }

        val outBytes = ByteArray(finalShorts.size * 2)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(finalShorts)
        return outBytes
    }

    /**
     * Overwrites the RIFF header in place with accurate byte counts.
     */
    private fun writeWavHeader(file: File, pcmDataLength: Long, sampleRate: Int, channels: Int) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * 2

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.writeBytes("RIFF")
            raf.writeInt(Integer.reverseBytes(totalDataLen.toInt()))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeInt(Integer.reverseBytes(16))
            raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
            raf.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
            raf.writeInt(Integer.reverseBytes(sampleRate))
            raf.writeInt(Integer.reverseBytes(byteRate))
            raf.writeShort(java.lang.Short.reverseBytes((channels * 2).toShort()).toInt())
            raf.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt())
            raf.writeBytes("data")
            raf.writeInt(Integer.reverseBytes(pcmDataLength.toInt()))
        }
    }
}
