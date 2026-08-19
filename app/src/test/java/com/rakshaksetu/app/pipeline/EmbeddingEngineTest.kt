package com.rakshaksetu.app.pipeline

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream

class EmbeddingEngineTest {

    private lateinit var mockContext: Context
    private lateinit var mockAssetManager: android.content.res.AssetManager

    @Before
    fun setup() {
        mockContext = mockk()
        mockAssetManager = mockk()
        every { mockContext.assets } returns mockAssetManager

        // Mock vocab.txt reading with a few basic tokens for testing
        val dummyVocab = "[PAD]\n[UNK]\n[CLS]\n[SEP]\nhello\nworld\nscam\notp\n".byteInputStream()
        every { mockAssetManager.open("vocab.txt") } returns dummyVocab
    }

    @Test
    fun testWordPieceTokenizer() {
        val tokenizer = WordPieceTokenizer(mockContext, "vocab.txt")
        val tokens = tokenizer.tokenize("hello scam otp", 10)
        
        // Expected: [CLS] hello scam otp [SEP] [PAD] ...
        // [CLS] -> 101, [SEP] -> 102, hello -> 4, scam -> 6, otp -> 7 (based on 0-index dummy string above)
        // Actually: 
        // [PAD] = 0, [UNK] = 1, [CLS] = 2, [SEP] = 3
        // hello = 4, world = 5, scam = 6, otp = 7
        
        assertEquals(2L, tokens[0]) // CLS
        assertEquals(4L, tokens[1]) // hello
        assertEquals(6L, tokens[2]) // scam
        assertEquals(7L, tokens[3]) // otp
        assertEquals(3L, tokens[4]) // SEP
        assertEquals(0L, tokens[5]) // PAD
    }

    @Test
    fun testEmbeddingGenerationInitialization() {
        // Without ONNX environment loaded in JUnit, we can't test real generation
        // But we can test it handles failure gracefully
        EmbeddingEngine.init(mockContext, "dummy.onnx")
        val embedding = EmbeddingEngine.generateEmbedding("hello world")
        assertEquals(384, embedding.size)
        // Should return all zeros when ONNX is missing
        assertTrue(embedding.all { it == 0f })
    }
}
