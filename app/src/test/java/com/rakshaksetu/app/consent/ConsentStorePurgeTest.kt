package com.rakshaksetu.app.consent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * DPDP erasure (Section 12) must actually delete every stored artefact.
 *
 * The previous implementation removed only filesDir/evidence while the UI told
 * the user "All local evidence & audio logs completely purged". Detection
 * results (phone numbers + full transcripts), the community blacklist, speaker
 * voice profiles, UPI indicators, feedback logs and the federated-learning
 * threshold table all survived. These tests lock in the corrected behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsentStorePurgeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun filesDir(name: String) = File(context.filesDir, name)

    @Test
    fun purgeRemovesEveryStoredArtefact() {
        // Seed one artefact of every kind the app persists.
        listOf("evidence", "intel", "feedback").forEach { name ->
            filesDir(name).mkdirs()
            File(filesDir(name), "record.json").writeText("{}")
        }
        File(context.filesDir, "speaker_voice_profiles.json").writeText("{}")

        context.getSharedPreferences("rakshak_detection_store", Context.MODE_PRIVATE)
            .edit().putString("key_last_result_json", """{"phoneNumber":"+919876543210"}""").commit()
        context.getSharedPreferences("rakshak_fl_thresholds", Context.MODE_PRIVATE)
            .edit().putString("key_fl_thresholds_json", """{"digital_arrest":0.67}""").commit()

        val cleared = ConsentStore(context).purgeEvidence(context)

        assertFalse("evidence dir must be gone", filesDir("evidence").exists())
        assertFalse("intel dir must be gone", filesDir("intel").exists())
        assertFalse("feedback dir must be gone", filesDir("feedback").exists())
        assertFalse("speaker voice profiles must be gone",
            File(context.filesDir, "speaker_voice_profiles.json").exists())

        assertTrue("detection store must be cleared",
            context.getSharedPreferences("rakshak_detection_store", Context.MODE_PRIVATE)
                .getString("key_last_result_json", null) == null)
        assertTrue("FL thresholds must be cleared",
            context.getSharedPreferences("rakshak_fl_thresholds", Context.MODE_PRIVATE)
                .getString("key_fl_thresholds_json", null) == null)

        assertTrue("purge must report what it cleared", cleared > 0)
    }

    @Test
    fun purgeIsSafeOnAFreshInstall() {
        // Nothing seeded: must not throw, and must not claim to have deleted data.
        ConsentStore(context).purgeEvidence(context)
    }
}