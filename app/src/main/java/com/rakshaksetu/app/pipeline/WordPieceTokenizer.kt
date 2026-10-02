package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class WordPieceTokenizer(context: Context, vocabFileName: String = "vocab.txt") {
    
    private val vocab = mutableMapOf<String, Long>()
    private val unkToken = "[UNK]"
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    private val padToken = "[PAD]"

    private val unkId: Long
    private val clsId: Long
    private val sepId: Long
    private val padId: Long

    /**
     * Size of the loaded vocabulary. Exposed so callers can assert that the
     * tokenizer matches the encoder they actually attached (see
     * [matchesEncoderVocabSize]).
     */
    var vocabSize: Int = 0
        private set

    /** True when a vocabulary file was successfully parsed. */
    val isLoaded: Boolean get() = vocabSize > 0

    init {
        loadVocab(context, vocabFileName)
        unkId = vocab[unkToken] ?: 100L
        clsId = vocab[clsToken] ?: 101L
        sepId = vocab[sepToken] ?: 102L
        padId = vocab[padToken] ?: 0L
    }

    /**
     * True when this tokenizer's vocabulary size is within tolerance of the
     * encoder's expected embedding-matrix rows.
     *
     * WHY THIS MATTERS: the shipped assets/vocab.txt is a 119,547-token BERT
     * WordPiece vocabulary (distilbert-base-multilingual-cased), but the encoder
     * downloaded at runtime is paraphrase-multilingual-MiniLM-L12-v2, a
     * 250,037-token XLM-R SentencePiece model. Feeding wordpiece IDs into an
     * XLM-R embedding table does not degrade gracefully -- it silently produces
     * meaningless vectors, so "semantic mode" would report confident nonsense.
     * A size mismatch is therefore treated as a hard failure: the caller must
     * fall back to the lexical matcher rather than run with mismatched inputs.
     */
    fun matchesEncoderVocabSize(expected: Int, tolerance: Int = 2): Boolean =
        expected > 0 && kotlin.math.abs(vocabSize - expected) <= tolerance

    private fun loadVocab(context: Context, vocabFileName: String) {
        try {
            context.assets.open(vocabFileName).use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                var index = 0L
                var lineCount = 0L
                var skipped = 0L
                reader.useLines { lines ->
                    lines.forEach { line ->
                        lineCount++
                        val token = line.trim()
                        if (token.isEmpty()) {
                            skipped++
                        } else {
                            // First occurrence wins, so a duplicated line can never
                            // silently re-point a token id at a later index.
                            vocab.getOrPut(token) { index }
                            index++
                        }
                    }
                }
                vocabSize = vocab.size
                Log.i(
                    "WordPieceTokenizer",
                    "Loaded ${vocabSize} tokens from $vocabFileName " +
                        "(lines=$lineCount, blankSkipped=$skipped, nextId=$index)."
                )
                if (vocabSize.toLong() != lineCount) {
                    Log.w(
                        "WordPieceTokenizer",
                        "vocab.txt read $lineCount lines but produced $vocabSize ids " +
                            "($skipped blank). A BERT vocabulary must have one id per line; " +
                            "a mismatch means ids are misaligned with the encoder's embedding table."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("WordPieceTokenizer", "Failed to load vocabulary '$vocabFileName'", e)
            vocabSize = 0
        }
    }

    fun tokenize(text: String, maxLength: Int = 128): LongArray {
        val tokens = mutableListOf<Long>()
        tokens.add(clsId)

        // Simple whitespace and punctuation split (for production, a proper Regex is recommended)
        val words = text.lowercase().split(Regex("\\s+|(?=\\p{Punct})|(?<=\\p{Punct})")).filter { it.isNotBlank() }

        for (word in words) {
            var start = 0
            while (start < word.length) {
                var end = word.length
                var matchedTokenId: Long? = null
                var matchedString = ""
                
                while (start < end) {
                    val subStr = if (start == 0) word.substring(start, end) else "##" + word.substring(start, end)
                    if (vocab.containsKey(subStr)) {
                        matchedTokenId = vocab[subStr]
                        matchedString = subStr
                        break
                    }
                    end--
                }

                if (matchedTokenId == null) {
                    tokens.add(unkId)
                    break // Unk entire word
                } else {
                    tokens.add(matchedTokenId)
                    start += matchedString.replace("##", "").length
                }

                if (tokens.size >= maxLength - 1) break
            }
            if (tokens.size >= maxLength - 1) break
        }

        tokens.add(sepId)

        // Pad to max length
        val result = LongArray(maxLength) { padId }
        for (i in tokens.indices) {
            result[i] = tokens[i]
        }

        return result
    }
}
