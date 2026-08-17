package com.rakshaksetu.app.pipeline

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.delay
import java.io.File

object AudioFetcher {
    private const val TAG = "AudioFetcher"

    /**
     * Primary fetcher using MediaStore to find the latest recording added after the call started.
     */
    fun fetchLatestRecording(ctx: Context, sinceEpochSec: Long): Uri? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATE_ADDED)
        val selection = "${MediaStore.Audio.Media.DATE_ADDED} >= ?"
        val args = arrayOf(sinceEpochSec.toString())
        val sort = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            ctx.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
                if (c.moveToFirst()) {
                    val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val id = c.getLong(idColumn)
                    return ContentUris.withAppendedId(collection, id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query failed", e)
        }
        return null
    }

    /**
     * Fallback fetcher checking known OEM directories.
     */
    fun fetchFromFileObserver(sinceEpochSec: Long, oemPaths: List<String>): Uri? {
        // This is a simplified fallback that checks standard directories for recent files
        for (path in oemPaths) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                val latestFile = dir.listFiles()
                    ?.filter { it.isFile && it.lastModified() / 1000 >= sinceEpochSec }
                    ?.maxByOrNull { it.lastModified() }
                
                if (latestFile != null) {
                    return Uri.fromFile(latestFile)
                }
            }
        }
        return null
    }

    /**
     * Coordinated fetch trying MediaStore first, with retries for slow OEM writers.
     */
    suspend fun fetchWithRetries(ctx: Context, sinceEpochSec: Long, oemPaths: List<String>): Uri? {
        // Initial attempt
        var uri = fetchLatestRecording(ctx, sinceEpochSec) ?: fetchFromFileObserver(sinceEpochSec, oemPaths)
        if (uri != null) return uri

        // Retry at +2s
        delay(2000)
        uri = fetchLatestRecording(ctx, sinceEpochSec) ?: fetchFromFileObserver(sinceEpochSec, oemPaths)
        if (uri != null) return uri

        // Retry at +5s
        delay(3000)
        return fetchLatestRecording(ctx, sinceEpochSec) ?: fetchFromFileObserver(sinceEpochSec, oemPaths)
    }
}
