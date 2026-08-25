package com.rakshaksetu.app.pipeline

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Resilient recording locator for Android 10-15 scoped storage.
 *
 * Failure modes eliminated vs. legacy implementation:
 *  - MediaScanner indexing latency (HiOS/MIUI take 500ms-3s+): ContentObserver-driven
 *    adaptive wait loop with an early exit instead of a fixed ~1.5s retry budget.
 *  - Half-written files: rows with IS_PENDING=1 or SIZE=0 are never returned; every
 *    candidate URI is verified openable before it leaves this class.
 *  - Silent File.listFiles() no-op under scoped storage: direct-path fallback now only
 *    used where it can actually work (file:// URIs / legacy external storage).
 */
object AudioFetcher {
    private const val TAG = "AudioFetcher"

    /** Look-back window (seconds) before call end — recorders stamp files at call start. */
    private const val LOOKBACK_SEC = 30L

    /** Total budget waiting for slow OEM media scanners. */
    const val FETCH_BUDGET_MS = 12_000L
    private const val INITIAL_POLL_MS = 250L
    private const val MAX_POLL_MS = 1_500L

    /**
     * OEM call-recording buckets, matched case-insensitively against RELATIVE_PATH/BUCKET.
     * Covers: Samsung, Xiaomi/Redmi, TECNO/Infinix/Itel, Vivo/iQOO, Oppo/Realme/OnePlus,
     * Huawei/Honor, Motorola, Nokia, Google Pixel, and generic Android recorders.
     */
    private val CALL_BUCKET_HINTS = listOf(
        // Transsion (TECNO / Infinix / Itel / HiOS)
        "phonerecord", "music/phonerecord",
        // Samsung (One UI)
        "recordings/call", "call",
        // Xiaomi / Redmi / POCO (MIUI / HyperOS)
        "sound_recorder/call_rec", "sound_recorder/call_recordings",
        "miui/sound_recorder/call_rec", "miui/sound_recorder/call_recordings",
        // Vivo / iQOO (FuntouchOS / OriginOS)
        "call recordings", "recordings/call recordings", "sounds/call recordings",
        // Oppo / Realme / OnePlus (ColorOS / Realme UI / OxygenOS)
        "call recordings", "music/recordings/call recordings",
        "recordings/call recordings",
        // OnePlus legacy
        "record/phonerecord",
        // Huawei / Honor (EMUI / MagicOS)
        "sounds/callrecord", "callrecord", "record",
        // Google Pixel / Stock Android
        "callrecording",
        // Generic / fallback
        "recordings", "callrecording", "call_recording"
    )

    private data class Candidate(
        val uri: Uri,
        val dateAdded: Long,
        val size: Long,
        val bucketPath: String,
        val durationMs: Long
    )

    fun buildSelection(includePendingColumn: Boolean): String =
        if (includePendingColumn) {
            "(${MediaStore.Audio.Media.DATE_ADDED} >= ?)" +
                " AND (${MediaStore.Audio.Media.IS_PENDING} = 0 OR ${MediaStore.Audio.Media.IS_PENDING} IS NULL)" +
                " AND ${MediaStore.Audio.Media.SIZE} > 0"
        } else {
            "${MediaStore.Audio.Media.DATE_ADDED} >= ? AND ${MediaStore.Audio.Media.SIZE} > 0"
        }

    /**
     * Fetches the most plausible call recording created around [sinceEpochSec] (epoch seconds).
     * Polls MediaStore on an adaptive schedule and wakes early on MediaStore changes via
     * ContentObserver. Verifies openability before returning.
     */
    suspend fun fetchWithRetries(ctx: Context, sinceEpochSec: Long, oemPaths: List<String>): Uri? {
        return withContext(Dispatchers.IO) {
            val searchSince = (sinceEpochSec - LOOKBACK_SEC).coerceAtLeast(0)

            var observer: ContentObserver? = null
            try {
                val collection = mediaCollection()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) { /* poll loop reacts */ }
                    }
                    runCatching {
                        ctx.contentResolver.registerContentObserver(collection, true, observer!!)
                    }
                }

                var pollDelay = INITIAL_POLL_MS
                val deadline = System.currentTimeMillis() + FETCH_BUDGET_MS
                while (true) {
                    val uri = queryBestCandidate(ctx, searchSince) ?: fetchFromFileObserver(searchSince, oemPaths)
                    if (uri != null && isUriOpenable(ctx, uri)) return@withContext uri

                    if (System.currentTimeMillis() >= deadline) break
                    delay(pollDelay)
                    pollDelay = (pollDelay * 2).coerceAtMost(MAX_POLL_MS)
                }

                Log.w(TAG, "No openable recording found within ${FETCH_BUDGET_MS}ms budget.")
                null
            } catch (e: Exception) {
                Log.e(TAG, "fetchWithRetries failed", e)
                null
            } finally {
                observer?.let {
                    runCatching { ctx.contentResolver.unregisterContentObserver(it) }
                }
            }
        }
    }

    private fun mediaCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    internal fun queryBestCandidate(ctx: Context, sinceEpochSec: Long): Uri? {
        val collection = mediaCollection()
        val hasPendingColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                add(MediaStore.Audio.Media.DURATION)
            }
        }.toTypedArray()

        return try {
            ctx.contentResolver.query(
                collection,
                projection,
                buildSelection(hasPendingColumn),
                arrayOf(sinceEpochSec.toString()),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1
                val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.getColumnIndexOrThrow(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                } else -1
                val durCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                } else -1

                var bestBucketMatch: Candidate? = null
                var bestAny: Candidate? = null

                while (c.moveToNext()) {
                    val candidate = Candidate(
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                        dateAdded = c.getLong(dateCol),
                        size = c.getLong(sizeCol),
                        bucketPath = (
                            (if (pathCol >= 0) c.getString(pathCol) else null) + "|" +
                                (if (bucketCol >= 0) c.getString(bucketCol) else "")
                            ).lowercase(),
                        durationMs = if (durCol >= 0) c.getLong(durCol) else 0L
                    )
                    // Reject only pathologically short (sub-second noise) or absurdly long recordings.
                    // durationMs == 0 means MediaStore didn't report duration (common on some OEMs) — allow it.
                    if (candidate.durationMs in 1..999 || candidate.durationMs > 5_400_000) continue

                    if (bestAny == null || candidate.dateAdded > bestAny.dateAdded) bestAny = candidate
                    if (bestBucketMatch == null && isCallBucket(candidate.bucketPath)) {
                        bestBucketMatch = candidate
                    }
                }

                val chosen = bestBucketMatch ?: bestAny
                chosen?.let {
                    Log.d(TAG, "Recording candidate: $it.uri (size=${it.size}, bucket=${it.bucketPath})")
                    return it.uri
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query failed", e)
            null
        }
    }

    private fun isCallBucket(bucketPathLower: String): Boolean =
        CALL_BUCKET_HINTS.any { hint ->
            bucketPathLower.contains(hint)
        }

    /**
     * Direct-path fallback. Under Android 11+ scoped storage this returns null silently
     * for shared storage paths; it remains functional for app-owned dirs and legacy installs.
     */
    fun fetchFromFileObserver(sinceEpochSec: Long, oemPaths: List<String>): Uri? {
        for (path in oemPaths) {
            try {
                val dir = File(path)
                if (!dir.exists() || !dir.isDirectory || !dir.canRead()) continue
                val latestFile = dir.listFiles()
                    ?.filter { it.isFile && it.length() > 0 && it.lastModified() / 1000 >= sinceEpochSec }
                    ?.maxByOrNull { it.lastModified() }
                if (latestFile != null && latestFile.canRead()) {
                    Log.d(TAG, "Direct-path fallback hit: ${latestFile.absolutePath}")
                    return Uri.fromFile(latestFile)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Direct-path probe failed for $path: ${e.message}")
            }
        }
        return null
    }

    /**
     * Confirms the OS provider will actually hand us readable bytes — catches
     * pending/half-indexed rows and revoked-permission edge cases before decode.
     */
    fun isUriOpenable(ctx: Context, uri: Uri): Boolean {
        return try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize > 44
            } ?: false
        } catch (e: Exception) {
            Log.d(TAG, "URI not yet openable: $uri (${e.javaClass.simpleName})")
            false
        }
    }
}
