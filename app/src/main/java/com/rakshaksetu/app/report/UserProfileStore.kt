package com.rakshaksetu.app.report

import android.content.Context
import android.content.SharedPreferences

/**
 * Complainant profile used to pre-fill government reporting portals (NCRP/Chakshu).
 * DPDP: stored exclusively in app-private SharedPreferences; only fields the user
 * explicitly enters are persisted; never leaves the device except into the portal
 * WebView the user drives.
 */
class UserProfileStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rakshak_user_profile", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NAME = "key_name"
        private const val KEY_PHONE = "key_phone"
        private const val KEY_ALT_PHONE = "key_alt_phone"
        private const val KEY_EMAIL = "key_email"
        private const val KEY_STATE = "key_state"
        private const val KEY_CITY = "key_city"
        private const val KEY_ADDRESS = "key_address"
        private const val KEY_AGE_DECLARED = "key_age_declared"
    }

    var fullName: String
        get() = prefs.getString(KEY_NAME, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_NAME, v.trim()).apply()

    var phone: String
        get() = prefs.getString(KEY_PHONE, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_PHONE, v.filter { c -> c.isDigit() || c == '+' }.trim()).apply()

    var alternatePhone: String
        get() = prefs.getString(KEY_ALT_PHONE, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_ALT_PHONE, v.filter { c -> c.isDigit() || c == '+' }.trim()).apply()

    var email: String
        get() = prefs.getString(KEY_EMAIL, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_EMAIL, v.trim().lowercase()).apply()

    var state: String
        get() = prefs.getString(KEY_STATE, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_STATE, v.trim()).apply()

    var city: String
        get() = prefs.getString(KEY_CITY, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_CITY, v.trim()).apply()

    var address: String
        get() = prefs.getString(KEY_ADDRESS, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_ADDRESS, v.trim()).apply()

    var ageDeclared: String
        get() = prefs.getString(KEY_AGE_DECLARED, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_AGE_DECLARED, v.filter { it.isDigit() }.take(3)).apply()

    fun isCompleteForFiling(): Boolean =
        fullName.isNotBlank() && phone.isNotBlank() && email.isNotBlank()

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
