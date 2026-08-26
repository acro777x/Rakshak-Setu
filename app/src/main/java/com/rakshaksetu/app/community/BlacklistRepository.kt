package com.rakshaksetu.app.community

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.rakshaksetu.app.community.db.BlacklistDao
import com.rakshaksetu.app.community.db.BlacklistDatabase
import com.rakshaksetu.app.community.db.BlacklistEntry
import com.rakshaksetu.app.community.db.BlacklistSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class RiskLevel { NONE, LOW, MEDIUM, HIGH }

data class RiskAssessment(
    val normalizedNumber: String,
    val risk: RiskLevel,
    val reason: String,
    val category: String?
)

/**
 * Offline-first community blacklist.
 * Room is the single source of truth; the remote sync seam ([BlacklistRemoteSync])
 * activates only when Firebase is wired (FirebaseGuard). All lookups are pure local
 * reads — pre-call warnings never touch the network.
 */
class BlacklistRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao: BlacklistDao = BlacklistDatabase.get(appContext).blacklistDao()

    // ---------------------------------------------------------------- seed

    data class SeedFile(
        val version: Int,
        @SerializedName("prefix_rules") val prefixRules: List<PrefixRule>,
        val entries: List<SeedEntry>
    ) {
        data class PrefixRule(
            val prefix: String,
            val category: String,
            val risk: String,
            val note: String
        )

        data class SeedEntry(
            val number: String,
            val category: String,
            val confidence: Float,
            val notes: String
        )
    }

    companion object {
        private const val TAG = "BlacklistRepository"
        private const val SEED_ASSET = "community_blacklist_seed.json"
        private const val STALE_CUTOFF_MS = 180L * 24 * 60 * 60 * 1000 // 180 days
        private const val HIGH_CONFIDENCE = 0.80f
        private const val COMMUNITY_EXPORT_MIN_REPORTS = 5

        /** Legitimate Indian short codes that must never be flagged as suspicious CLI. */
        private val SAFE_SHORT_CODES = setOf("100", "101", "102", "108", "112", "181", "1098", "1930", "139")

        @Volatile private var cachedSeedRules: List<SeedFile.PrefixRule>? = null
    }

    suspend fun ensureSeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (dao.count() > 0) return@withContext true
            val seed = loadSeed() ?: return@withContext false
            val now = System.currentTimeMillis()
            val entries = seed.entries.map {
                BlacklistEntry(
                    normalizedNumber = NumberNormalizer.normalize(it.number),
                    category = it.category,
                    confidence = it.confidence,
                    source = BlacklistSources.SEED,
                    reportedCount = 1,
                    lastSeenEpochMs = now,
                    notes = it.notes
                )
            }.filter { it.normalizedNumber.isNotBlank() }
            if (entries.isNotEmpty()) dao.upsertAll(entries)
            Log.i(TAG, "Seeded blacklist v${seed.version} (${entries.size} entries, ${seed.prefixRules.size} rules)")
            entries.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Seeding failed", e)
            false
        }
    }

    private fun loadSeed(): SeedFile? =
        try {
            val json = appContext.assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
            Gson().fromJson(json, SeedFile::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Seed asset unavailable: ${e.message}")
            null
        }

    fun seedPrefixRules(): List<SeedFile.PrefixRule> {
        cachedSeedRules?.let { return it }
        val rules = loadSeed()?.prefixRules ?: emptyList()
        cachedSeedRules = rules
        return rules
    }

    // ------------------------------------------------------------- lookup

    suspend fun lookup(rawNumber: String): BlacklistEntry? = withContext(Dispatchers.IO) {
        val normalized = NumberNormalizer.normalize(rawNumber)
        if (normalized.isBlank()) null else dao.getByNumber(normalized)
    }

    /**
     * Full pre-call assessment: exact blacklist match first, then deterministic
     * CLI heuristics. Never blocks; safe to run on any dispatcher via caller.
     */
    suspend fun assess(rawNumber: String): RiskAssessment = withContext(Dispatchers.IO) {
        val normalized = NumberNormalizer.normalize(rawNumber)
        if (normalized.isBlank()) {
            return@withContext RiskAssessment("", RiskLevel.NONE, "Unparseable CLI ignored.", null)
        }
        ensureSeeded()

        val entry = dao.getByNumber(normalized)
        when {
            entry != null && entry.confidence >= HIGH_CONFIDENCE ->
                RiskAssessment(normalized, RiskLevel.HIGH, "Community blacklisted: ${entry.category}", entry.category)

            entry != null ->
                RiskAssessment(normalized, RiskLevel.MEDIUM, "Reported scam pattern: ${entry.category}", entry.category)

            else -> heuristicAssessment(normalized)
        }
    }

    internal fun heuristicAssessment(normalized: String): RiskAssessment {
        // Safe short codes first
        if (normalized in SAFE_SHORT_CODES) {
            return RiskAssessment(normalized, RiskLevel.NONE, "Emergency/service short code.", null)
        }

        // International origin on an India-facing dialer — classic spoof vector
        if (NumberNormalizer.isInternational(normalized)) {
            val rule = seedPrefixRules().firstOrNull { rule ->
                val ruleDigits = NumberNormalizer.normalize(rule.prefix)
                normalized.startsWith(ruleDigits) && rule.risk != "LOW"
            }
            return if (rule != null) {
                RiskAssessment(normalized, RiskLevel.MEDIUM, "International CLI matching fraud pattern (${rule.note}).", rule.category)
            } else {
                RiskAssessment(normalized, RiskLevel.LOW, "International CLI without +91 origin.", "international_unknown")
            }
        }

        // Degenerate digit patterns (repeated-digit floods used by robocallers)
        if (normalized.length == 10) {
            val distinct = normalized.toSet().size
            if (distinct <= 3) {
                return RiskAssessment(normalized, RiskLevel.MEDIUM, "Degenerate repeated-digit CLI pattern ($distinct distinct digits).", "robocall_pattern")
            }
            if (normalized.startsWith("140")) {
                return RiskAssessment(normalized, RiskLevel.LOW, "Telemarketing series (140x) — unsolicited but regulated.", "telemarketer")
            }
        }

        return RiskAssessment(normalized, RiskLevel.NONE, "No adverse signals.", null)
    }

    // ------------------------------------------------------------- writes

    /** Records a user/community report against a number (count-bumped upsert). */
    suspend fun reportLocal(rawNumber: String, category: String, confidence: Float): Unit =
        withContext(Dispatchers.IO) {
            val normalized = NumberNormalizer.normalize(rawNumber)
            if (normalized.isBlank()) return@withContext
            val existing = dao.getByNumber(normalized)
            val next = if (existing != null) {
                existing.copy(
                    reportedCount = existing.reportedCount + 1,
                    lastSeenEpochMs = System.currentTimeMillis(),
                    confidence = maxOf(existing.confidence, confidence)
                )
            } else {
                BlacklistEntry(
                    normalizedNumber = normalized,
                    category = category,
                    confidence = confidence,
                    source = BlacklistSources.LOCAL_REPORT,
                    reportedCount = 1,
                    lastSeenEpochMs = System.currentTimeMillis()
                )
            }
            dao.upsert(next)
        }

    /** Merges a remote community batch (Room stays authoritative for conflicts). */
    suspend fun mergeRemote(entries: List<BlacklistEntry>): Int = withContext(Dispatchers.IO) {
        var merged = 0
        entries.forEach { remote ->
            val local = dao.getByNumber(remote.normalizedNumber)
            if (local == null || remote.lastSeenEpochMs > local.lastSeenEpochMs ||
                remote.reportedCount > local.reportedCount
            ) {
                dao.upsert(
                    remote.copy(
                        source = BlacklistSources.COMMUNITY,
                        reportedCount = maxOf(local?.reportedCount ?: 0, remote.reportedCount)
                    )
                )
                merged++
            }
        }
        merged
    }

    suspend fun maintenancePurge(): Int = withContext(Dispatchers.IO) {
        dao.purgeOlderThan(System.currentTimeMillis() - STALE_CUTOFF_MS)
    }

    suspend fun exportForCommunityUpload(): List<BlacklistEntry> = withContext(Dispatchers.IO) {
        dao.communityExport(
            minReports = COMMUNITY_EXPORT_MIN_REPORTS,
            sinceMs = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        )
    }

    suspend fun size(): Int = withContext(Dispatchers.IO) { dao.count() }
}
