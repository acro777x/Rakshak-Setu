package com.rakshaksetu.app.telephony

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.rakshaksetu.app.community.NumberNormalizer
import com.rakshaksetu.app.elder.ElderModeStore

/**
 * Manages trusted caller whitelisting (Saved Contacts & Family Guardians).
 * Prevents false alarms on everyday family / trusted conversations.
 */
object TrustedContactManager {
    private const val TAG = "TrustedContactManager"

    /**
     * Checks whether the given phone number belongs to a trusted contact:
     * 1. Registered Elder Mode Family Guardian numbers
     * 2. User's saved address book contacts (if READ_CONTACTS is granted)
     */
    fun isTrustedContact(context: Context, rawNumber: String?): Boolean {
        if (rawNumber.isNullOrBlank()) return false
        val normalized = NumberNormalizer.normalize(rawNumber)

        // 1. Check Elder Mode Family Guardians
        try {
            val elderStore = ElderModeStore(context.applicationContext)
            val guardians = elderStore.guardianNumbers
            for (g in guardians) {
                if (NumberNormalizer.normalize(g) == normalized) {
                    Log.i(TAG, "Caller $normalized is a registered Elder Mode Guardian.")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Guardian check error: ${e.message}")
        }

        // 2. Check Device Address Book (if permission granted)
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(rawNumber)
                )
                val projection = arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME
                )
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(
                            cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        )
                        Log.i(TAG, "Caller $normalized matches saved contact: $name")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Address book lookup note: ${e.message}")
            }
        }

        return false
    }
}
