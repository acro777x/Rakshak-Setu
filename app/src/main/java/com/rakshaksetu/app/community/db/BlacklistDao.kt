package com.rakshaksetu.app.community.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface BlacklistDao {

    @Upsert
    suspend fun upsertAll(entries: List<BlacklistEntry>)

    @Upsert
    suspend fun upsert(entry: BlacklistEntry)

    @Query("SELECT * FROM blacklist_entries WHERE normalizedNumber = :number LIMIT 1")
    suspend fun getByNumber(number: String): BlacklistEntry?

    @Query("SELECT * FROM blacklist_entries WHERE normalizedNumber IN (:numbers)")
    suspend fun getByNumbers(numbers: List<String>): List<BlacklistEntry>

    @Query("SELECT COUNT(*) FROM blacklist_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM blacklist_entries ORDER BY lastSeenEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<BlacklistEntry>

    @Query("SELECT * FROM blacklist_entries WHERE source = :source")
    suspend fun getBySource(source: String): List<BlacklistEntry>

    @Query("DELETE FROM blacklist_entries WHERE source = :source")
    suspend fun deleteBySource(source: String): Int

    @Query("DELETE FROM blacklist_entries WHERE lastSeenEpochMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long): Int

    @Query("SELECT MAX(lastSeenEpochMs) FROM blacklist_entries")
    suspend fun newestSeenEpochMs(): Long?

    @Query("SELECT * FROM blacklist_entries WHERE reportedCount >= :minReports AND lastSeenEpochMs >= :sinceMs")
    suspend fun communityExport(minReports: Int, sinceMs: Long): List<BlacklistEntry>
}
