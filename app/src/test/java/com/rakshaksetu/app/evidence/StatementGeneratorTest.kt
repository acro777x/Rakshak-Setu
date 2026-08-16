package com.rakshaksetu.app.evidence

import com.rakshaksetu.app.debug.FakePipelineEmitter
import org.junit.Assert.*
import org.junit.Test

class StatementGeneratorTest {

    @Test
    fun `generated statement is at least 200 chars`() {
        val result = FakePipelineEmitter.scamResult()
        val statement = StatementGenerator.generate(result)
        assertTrue("Statement is ${statement.length} chars, need >= 200", statement.length >= 200)
    }

    @Test
    fun `generated statement contains no forbidden characters`() {
        val result = FakePipelineEmitter.scamResult()
        val statement = StatementGenerator.generate(result)
        val forbidden = "#\$@^*`'~|!\\"
        for (c in forbidden) {
            assertFalse("Statement contains forbidden char: '$c'", statement.contains(c))
        }
    }

    @Test
    fun `generated statement contains phone number`() {
        val result = FakePipelineEmitter.scamResult()
        val statement = StatementGenerator.generate(result)
        // Phone number should appear but + and digits are not forbidden
        assertTrue(statement.contains("9876543210"))
    }

    @Test
    fun `generated statement contains scam type`() {
        val result = FakePipelineEmitter.scamResult()
        val statement = StatementGenerator.generate(result)
        assertTrue(statement.contains("digital_arrest") || statement.contains("digital arrest"))
    }

    @Test
    fun `sanitize removes all forbidden characters`() {
        // Test with a result that has forbidden chars injected into transcript
        val result = FakePipelineEmitter.scamResult()
        val statement = StatementGenerator.generate(result)
        val validation = StatementGenerator.validate(statement)
        assertTrue("Validation failed: ${validation.errors}", validation.isValid)
    }

    @Test
    fun `validate rejects short statement`() {
        val validation = StatementGenerator.validate("Too short")
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("200") || it.lowercase().contains("length") })
    }

    @Test
    fun `validate rejects forbidden characters`() {
        val badStatement = "A".repeat(200) + "#$@"
        val validation = StatementGenerator.validate(badStatement)
        assertFalse(validation.isValid)
    }

    @Test
    fun `validate accepts valid statement`() {
        val goodStatement = "A".repeat(250)
        val validation = StatementGenerator.validate(goodStatement)
        assertTrue(validation.isValid)
    }

    @Test
    fun `statement handles Hindi UTF-8 text in transcript`() {
        val result = FakePipelineEmitter.scamResult()
        val statement = StatementGenerator.generate(result)
        // Should not crash or produce empty output
        assertTrue(statement.isNotBlank())
        assertTrue(statement.length >= 200)
    }
}
