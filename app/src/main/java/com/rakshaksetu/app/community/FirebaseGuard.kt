package com.rakshaksetu.app.community

import android.content.Context
import android.util.Log

/**
 * FIX 1: Defensive Firebase Guard.
 * Protects the app from crashing on start if google-services.json is missing
 * or Firebase is uninitialized.
 * Any community/cloud code MUST check FirebaseGuard.isAvailable(ctx) before calling Firestore/Auth.
 */
object FirebaseGuard {
    private const val TAG = "FirebaseGuard"

    fun isAvailable(context: Context): Boolean {
        return try {
            val firebaseAppClass = Class.forName("com.google.firebase.FirebaseApp")
            val getAppsMethod = firebaseAppClass.getMethod("getApps", Context::class.java)
            val apps = getAppsMethod.invoke(null, context) as? List<*>
            val available = apps != null && apps.isNotEmpty()
            if (!available) {
                Log.d(TAG, "Firebase not initialized. Running in Local-Only / Offline mode.")
            }
            available
        } catch (e: Throwable) {
            Log.d(TAG, "Firebase SDK not active (${e.javaClass.simpleName}). Gracefully running in Local-Only mode.")
            false
        }
    }
}
