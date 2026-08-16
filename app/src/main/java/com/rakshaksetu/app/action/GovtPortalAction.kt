package com.rakshaksetu.app.action

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.rakshaksetu.app.evidence.StatementGenerator
import com.rakshaksetu.app.model.DetectionResult

object GovtPortalAction {
    fun openNcrp(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivitySafe(context, intent)
    }

    fun openChakshu(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sancharsaathi.gov.in/sfc/"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivitySafe(context, intent)
    }

    private fun startActivitySafe(context: Context, intent: Intent) {
        if (intent.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("GovtPortalAction", "Browser not found", e)
            } catch (e: Exception) {
                Log.e("GovtPortalAction", "Failed to start activity", e)
            }
        } else {
            Log.e("GovtPortalAction", "No browser installed to handle URL")
        }
    }

    fun prepareClipboardData(context: Context, result: DetectionResult) {
        try {
            val statement = StatementGenerator.generate(result)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Statement", statement)
            clipboard.setPrimaryClip(clip)
            
            // Verify clipboard was set by reading back
            val currentClip = clipboard.primaryClip
            if (currentClip != null && currentClip.itemCount > 0 && currentClip.getItemAt(0).text == statement) {
                Toast.makeText(context, "Statement copied", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("GovtPortalAction", "Clipboard verification failed")
                Toast.makeText(context, "Failed to copy statement", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("GovtPortalAction", "Error interacting with clipboard", e)
        }
    }
}
