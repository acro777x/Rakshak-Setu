package com.rakshaksetu.app.security

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.EnumMap

/**
 * ZXing-backed [ImageAnalysis.Analyzer] that actually decodes QR symbols.
 *
 * WHY AN ANALYZER AND NOT A BUTTON
 * -------------------------------
 * The old QR screen drew a static rectangle with a "Decode QR Code" button that
 * did not touch the camera. Decoding is continuous here: every frame the
 * camera delivers is fed to ZXing, and the first successful decode wins.
 *
 * WHY THE YUV PLANE IS USED RATHER THAN A BITMAP
 * ----------------------------------------------
 * [ImageProxy] from CameraX is YUV_420_888 on every device. The luminance (Y)
 * plane is a single-channel byte array of exactly width*height, which is the
 * format ZXing wants -- so there is no colour conversion, no Bitmap
 * allocation, and no GC churn on the analysis thread. Converting to an ARGB
 * Bitmap first would allocate several megabytes per frame and could stall the
 * preview on a mid-range device.
 *
 * The Y plane can be row-strided beyond width, which is why the source is built
 * with an explicit dataSize and rowStride instead of assuming width*height.
 */
class QrCodeAnalyzer(
    /** Called off the main thread with the decoded text. Guard against re-entry. */
    private val onDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = QRCodeReader()
    private val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(com.google.zxing.BarcodeFormat.QR_CODE))
        put(DecodeHintType.TRY_HARDER, true)
        put(DecodeHintType.CHARACTER_SET, "UTF-8")
    }

    @Volatile
    private var delivered = false

    fun reset() {
        delivered = false
    }

    override fun analyze(image: ImageProxy) {
        if (delivered) {
            image.close()
            return
        }
        try {
            decode(image)?.let { text ->
                delivered = true
                onDecoded(text)
            }
        } catch (_: Exception) {
            // A frame that does not contain a QR is the normal case, not an error.
        } finally {
            image.close()
        }
    }

    private fun decode(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height

        // Trim any trailing padding so ZXing sees exactly width*height bytes.
        val dataWidth = width
        val dataHeight = height
        val data = ByteArray(dataWidth * dataHeight)
        buffer.rewind()
        if (rowStride == dataWidth) {
            buffer.get(data, 0, data.size.coerceAtMost(buffer.remaining()))
        } else {
            val row = ByteArray(rowStride)
            for (y in 0 until dataHeight) {
                if (buffer.remaining() < rowStride) break
                buffer.get(row, 0, rowStride)
                System.arraycopy(row, 0, data, y * dataWidth, dataWidth)
            }
        }

        val source = PlanarYUVLuminanceSource(
            data, dataWidth, dataHeight, 0, 0, dataWidth, dataHeight, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decode(bitmap, hints)
        return result?.text?.takeIf { it.isNotBlank() }
    }
}
