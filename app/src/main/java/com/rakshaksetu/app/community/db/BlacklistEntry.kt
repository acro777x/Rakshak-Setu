package com.rakshaksetu.app.community.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A community-curated high-risk phone number.
 * [normalizedNumber] is the 10-digit Indian NSN (or full international digit string).
 */
@Entity(tableName = "blacklist_entries")
data class BlacklistEntry(
    @PrimaryKey val normalizedNumber: String,
    val category: String,
    val confidence: Float,
    val source: String, // "seed" | "community" | "local_report"
    val reportedCount: Int,
    val lastSeenEpochMs: Long,
    val notes: String = ""
)

object BlacklistSources {
    const val SEED = "seed"
    const val COMMUNITY = "community"
    const val LOCAL_REPORT = "local_report"
}
