package com.rakshaksetu.app.action

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.rakshaksetu.app.model.DetectionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object HelplineAction {
    fun dial1930(context: Context) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        
        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:1930"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("HelplineAction", "Activity not found to handle call intent", e)
        } catch (e: Exception) {
            Log.e("HelplineAction", "Failed to start activity", e)
        }
    }

    fun buildOperatorScript(result: DetectionResult): String {
        val format = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        format.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val dateStr = format.format(Date(result.callEndEpoch * 1000L))
        
        return """
            I received a scam call on $dateStr.
            The caller's number was ${result.phoneNumber}.
            The identified scam type was ${result.scamType}.
            I need to report this fraud immediately.
            Please help me block the transaction.
        """.trimIndent()
    }
}
