package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private const val TAG = "ScamPhraseLibrary"
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
                val jsonString = reader.readText()
                
                // First attempt: direct JSON Map format
                try {
                    val mapType = object : TypeToken<Map<String, List<String>>>() {}.type
                    val map: Map<String, List<String>> = Gson().fromJson(jsonString, mapType)
                    val categories = map.map { (catId, phrases) ->
                        ScamCategory(
                            id = catId,
                            label = catId.replace("_", " ").uppercase(),
                            phrases = phrases
                        )
                    }
                    corpus = ScamPhraseCorpus(version = 2, categories = categories)
                    Log.i(TAG, "Loaded ${categories.size} scam categories from JSON map.")
                    return true
                } catch (ignored: Exception) {}

                // Second attempt: full ScamPhraseCorpus format
                corpus = Gson().fromJson(jsonString, ScamPhraseCorpus::class.java)
                Log.i(TAG, "Loaded ScamPhraseCorpus version ${corpus?.version}.")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load scam phrases corpus", e)
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
