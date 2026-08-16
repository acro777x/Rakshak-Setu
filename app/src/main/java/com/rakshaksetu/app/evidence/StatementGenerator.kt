package com.rakshaksetu.app.evidence

import com.rakshaksetu.app.model.DetectionResult
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class StatementValidationException(message: String) : Exception(message)

data class ValidationResult(val isValid: Boolean, val errors: List<String>)

object StatementGenerator {
    fun generate(result: DetectionResult): String {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        dateFormat.timeZone = tz
        timeFormat.timeZone = tz
        
        val dateStr = dateFormat.format(Date(result.callEndEpoch * 1000L))
        val timeStr = timeFormat.format(Date(result.callEndEpoch * 1000L))
        val segmentsStr = result.flaggedSegments.joinToString(" | ") { it.text }
        
        val rawTemplate = "I received a suspected fraud call from ${result.phoneNumber} on $dateStr at $timeStr for ${result.durationSec} seconds. The caller used a \"${result.scamType}\" script. Key statements from the call transcript: $segmentsStr. I am filing this complaint to request investigation and, if any payment was made, urgent freezing of the beneficiary account under the golden-hour process."
        
        val sanitized = sanitize(rawTemplate)
        val finalStatement = padToMinLength(sanitized, 200, result)
        
        val validation = validate(finalStatement)
        if (!validation.isValid) {
            throw StatementValidationException(validation.errors.joinToString(", "))
        }
        
        return finalStatement
    }
    
    private fun sanitize(text: String): String {
        val regex = Regex("[#\$@\\^\\*`'~|!\\\\]")
        return text.replace(regex, "")
    }
    
    private fun padToMinLength(text: String, minLen: Int, result: DetectionResult): String {
        if (text.length >= minLen) return text
        return text + " This is an auto-generated complaint from Rakshak Setu app for call reference ${result.callId}. Additional context: call duration was ${result.durationSec} seconds, detection confidence was ${result.confidence}%."
    }
    
    fun validate(statement: String): ValidationResult {
        val errors = mutableListOf<String>()
        if (statement.length < 200) {
            errors.add("Length is less than 200 characters.")
        }
        val regex = Regex("[#\$@\\^\\*`'~|!\\\\]")
        if (regex.containsMatchIn(statement)) {
            errors.add("Contains forbidden characters.")
        }
        return ValidationResult(errors.isEmpty(), errors)
    }

    fun writeToFileWithUtf8(statement: String, file: File) {
        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                writer.write(statement)
            }
        }
    }
}
