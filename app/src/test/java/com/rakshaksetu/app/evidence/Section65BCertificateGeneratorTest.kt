package com.rakshaksetu.app.evidence

import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.PipelineMetrics
import org.junit.Assert.*
import org.junit.Test

class Section65BCertificateGeneratorTest {

    @Test
    fun generateCertificate_validDetection_producesSection65BStatutoryText() {
        val result = DetectionResult(
            callId = "CALL-TEST-9988",
            phoneNumber = "+919876543210",
            durationSec = 145,
            isScam = true,
            confidence = 0.94f,
            scamType = "CBI Digital Arrest",
            fullTranscript = "Main CBI se bol raha hoon aap digital arrest ho gaye hain paise transfer karo",
            audioUri = "content://media/external/audio/media/1234",
            callEndEpoch = 1718000000L,
            flaggedSegments = emptyList(),
            pipelineMs = PipelineMetrics(50, 40, 180, 45, 15)
        )

        val cert = Section65BCertificateGenerator.generateCertificate(
            result = result,
            complainantName = "S.P. Oswal",
            complainantAddress = "Ludhiana, Punjab",
            deviceImei = "867530912345678",
            extractedUpiIds = listOf("cbi.officer@sbi"),
            extractedBankAccounts = listOf("9876543210123")
        )

        assertNotNull(cert)
        assertTrue(cert.certificateText.contains("SECTION 65B OF THE INDIAN EVIDENCE ACT"))
        assertTrue(cert.certificateText.contains("BHARATIYA SAKSHYA ADHINIYAM, 2023"))
        assertTrue(cert.certificateText.contains("S.P. Oswal"))
        assertTrue(cert.certificateText.contains("CALL-TEST-9988"))
        assertTrue(cert.certificateText.contains("cbi.officer@sbi"))
        assertEquals(64, cert.sha256Checksum.length) // Valid SHA-256 length
    }
}
