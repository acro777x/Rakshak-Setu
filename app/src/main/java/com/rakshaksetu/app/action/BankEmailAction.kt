package com.rakshaksetu.app.action

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.rakshaksetu.app.evidence.StatementGenerator
import com.rakshaksetu.app.model.DetectionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class BankInfo(val name: String, val fraudEmail: String, val nodalEmail: String?)

object BankEmailAction {
    fun getBanks(): List<BankInfo> {
        return listOf(
            BankInfo("SBI", "fraud@sbi.co.in", null),
            BankInfo("HDFC", "grievance.redressal@hdfcbank.com", null),
            BankInfo("ICICI", "fraud.complaints@icicibank.com", null),
            BankInfo("Axis", "cyber.frauds@axisbank.com", null),
            BankInfo("PNB", "fraud.monitoring@pnb.co.in", null),
            BankInfo("Bank of Baroda", "fraud.risk@bankofbaroda.com", null),
            BankInfo("Kotak", "service.kotak@kotak.com", null),
            BankInfo("Yes Bank", "fraud.complaints@yesbank.in", null),
            BankInfo("IDBI", "fraud.monitoring@idbi.co.in", null),
            BankInfo("Canara", "fraud.monitoring@canarabank.com", null),
            BankInfo("Union Bank", "fraud.section@unionbankofindia.bank", null),
            BankInfo("IOB", "complaints@iob.in", null),
            BankInfo("Central Bank", "fraud.monitoring@centralbankofindia.co.in", null),
            BankInfo("Bank of India", "complaint@bankofindia.co.in", null),
            BankInfo("IndusInd", "customer.care@indusind.com", null),
            BankInfo("Federal Bank", "fraud.monitoring@federalbank.co.in", null),
            BankInfo("RBL", "customerservice@rblbank.com", null),
            BankInfo("AU Small Finance", "customer.care@aubank.in", null),
            BankInfo("Karnataka Bank", "fraud@ktkbank.com", null),
            BankInfo("Bank of Maharashtra", "fraud@mahabank.in", null)
        )
    }

    fun buildEmailIntent(result: DetectionResult, bank: BankInfo): Intent {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val epochMillis = if (result.callEndEpoch > 100_000_000_000L) result.callEndEpoch else result.callEndEpoch * 1000L
        val dateStr = dateFormat.format(Date(epochMillis))
        
        val subject = "URGENT: Suspected Fraud — Request u/s RBI Zero Liability, dt $dateStr"
        val statement = StatementGenerator.generate(result)
        val body = "$statement\n\nUTR/Transaction ID: [PASTE YOUR UTR HERE]\n\nPlease freeze the beneficiary account immediately under the golden-hour process."
        
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:")
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(bank.fraudEmail))
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, body)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    fun sendEmail(context: Context, result: DetectionResult, bank: BankInfo) {
        val intent = buildEmailIntent(result, bank)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("BankEmailAction", "No email client found on device", e)
        } catch (e: Exception) {
            Log.e("BankEmailAction", "Error starting email intent", e)
        }
    }

    fun findBankByName(name: String): BankInfo? {
        return getBanks().find { it.name.equals(name, ignoreCase = true) }
    }
}
