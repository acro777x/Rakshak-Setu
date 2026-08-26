package com.rakshaksetu.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingCallStoreTest {

    private val validCallId = "11111111-2222-3333-4444-555555555555"

    @Test
    fun `save and get roundtrip`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        PendingCallStore.clear(context)

        val record = PendingCallStore.PendingCallRecord(
            callId = validCallId,
            phoneNumber = "+919876543210",
            durationSec = 142,
            endEpochMs = System.currentTimeMillis()
        )
        PendingCallStore.save(context, record)

        val loaded = PendingCallStore.get(context)
        assertEquals(validCallId, loaded?.callId)
        assertEquals("+919876543210", loaded?.phoneNumber)
        assertEquals(142, loaded?.durationSec)
    }

    @Test
    fun `invalid callId never loads`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val record = PendingCallStore.PendingCallRecord(
            callId = "../../etc/passwd",
            phoneNumber = "x",
            durationSec = 1,
            endEpochMs = 0L
        )
        PendingCallStore.save(context, record)
        assertNull(PendingCallStore.get(context))
    }

    @Test
    fun `attempts increment monotonically`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        PendingCallStore.clear(context)

        PendingCallStore.save(
            context,
            PendingCallStore.PendingCallRecord(validCallId, "+919000000000", 30, System.currentTimeMillis())
        )
        assertEquals(1, PendingCallStore.incrementAttempts(context))
        assertEquals(2, PendingCallStore.incrementAttempts(context))
    }

    @Test
    fun `clear removes state`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        PendingCallStore.save(
            context,
            PendingCallStore.PendingCallRecord(validCallId, "+919000000001", 10, System.currentTimeMillis())
        )
        assertTrue(PendingCallStore.get(context) != null)
        PendingCallStore.clear(context)
        assertFalse(PendingCallStore.get(context) != null)
    }
}
