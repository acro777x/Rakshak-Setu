package com.rakshaksetu.app.pipeline

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioFetcherSelectionTest {

    @Test
    fun `selection excludes pending rows on Q plus`() {
        val selection = AudioFetcher.buildSelection(includePendingColumn = true)
        assertTrue(selection.contains("is_pending"))
        assertTrue(selection.contains("size > 0", ignoreCase = true))
        assertTrue(selection.contains("date_added >= ?", ignoreCase = true))
    }

    @Test
    fun `selection below Q omits pending column`() {
        val selection = AudioFetcher.buildSelection(includePendingColumn = false)
        assertFalse(selection.contains("IS_PENDING"))
        assertEquals(
            "date_added >= ? and _size > 0",
            selection.lowercase()
        )
    }

    @Test
    fun `openable check rejects unreadable uris without crashing`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val fakeUri = Uri.parse("content://com.android.externalstorage/nonexistent/999")
        // No provider registered for this URI -> openFileDescriptor throws -> must return false
        val openable = AudioFetcher.isUriOpenable(context, fakeUri)
        assertFalse(openable)
    }
}
