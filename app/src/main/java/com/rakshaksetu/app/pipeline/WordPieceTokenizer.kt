package com.rakshaksetu.app.pipeline

import android.content.Context
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

    init {
        loadVocab(context, vocabFileName)
        unkId = vocab[unkToken] ?: 100L
        clsId = vocab[clsToken] ?: 101L
        sepId = vocab[sepToken] ?: 102L
        padId = vocab[padToken] ?: 0L
    }

    private fun loadVocab(context: Context, vocabFileName: String) {
        try {
            val inputStream = context.assets.open(vocabFileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var index = 0L
            reader.useLines { lines ->
                lines.forEach { line ->
                    val token = line.trim()
                    if (token.isNotEmpty()) {
                        vocab[token] = index
                    }
                    index++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
