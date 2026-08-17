package com.rakshaksetu.app.pipeline

import android.content.Context
import com.google.gson.Gson
import java.io.InputStreamReader

data class ScamCategory(
    val id: String,
    val label: String,
    val phrases: List<String>
)

data class ScamPhraseCorpus(
    val version: Int,
    val categories: List<ScamCategory>
)

object ScamPhraseLibrary {
    private const val CORPUS_ASSET_NAME = "scam_phrases.json"
    
    var corpus: ScamPhraseCorpus? = null
        private set

    /**
     * Loads the scam phrases corpus from the app's assets.
     */
    fun loadFromAssets(context: Context): Boolean {
        return try {
            context.assets.open(CORPUS_ASSET_NAME).use { inputStream ->
                val reader = InputStreamReader(inputStream)
                corpus = Gson().fromJson(reader, ScamPhraseCorpus::class.java)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Extract a flat list of all phrases combined with their category ID for easy embedding comparison.
     */
    fun getFlattenedPhrases(): List<Pair<String, ScamCategory>> {
        val flattened = mutableListOf<Pair<String, ScamCategory>>()
        corpus?.categories?.forEach { category ->
            category.phrases.forEach { phrase ->
                flattened.add(Pair(phrase, category))
            }
        }
        return flattened
    }

    /**
     * Apply a global update to the phrase library (Federated Learning).
     * This will merge new scam phrases sent from the cloud into our local definitions.
     */
    fun applyGlobalPhrasesUpdate(newCorpus: ScamPhraseCorpus) {
        val currentCorpus = corpus
        if (currentCorpus == null) {
            corpus = newCorpus
            return
        }

        // Only apply if the remote version is newer
        if (newCorpus.version > currentCorpus.version) {
            val mergedCategories = mutableListOf<ScamCategory>()
            
            // Map existing categories by ID for easy lookup
            val currentCategoryMap = currentCorpus.categories.associateBy { it.id }.toMutableMap()
            
            newCorpus.categories.forEach { remoteCategory ->
                val localCategory = currentCategoryMap[remoteCategory.id]
                if (localCategory != null) {
                    // Merge phrases, avoiding duplicates
                    val mergedPhrases = (localCategory.phrases + remoteCategory.phrases).distinct()
                    currentCategoryMap[remoteCategory.id] = localCategory.copy(phrases = mergedPhrases)
                } else {
                    // It's a completely new category
                    currentCategoryMap[remoteCategory.id] = remoteCategory
                }
            }
            
            corpus = ScamPhraseCorpus(
                version = newCorpus.version,
                categories = currentCategoryMap.values.toList()
            )
            Log.i("ScamPhraseLibrary", "Merged remote corpus version ${newCorpus.version} successfully.")
        }
    }
}
