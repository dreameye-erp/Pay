package com.dreameye.upibridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sends the text of a payment notification to the shop server.
 * The server decides what the amount and the reference number are, and ignores anything it has already seen.
 */
object Sender {
    const val PREF = "upibridge"
    const val KEY_URL = "url"
    const val KEY_ONLY_PAY = "onlyPay"
    const val KEY_LOG = "log"
    private const val KEY_SEEN = "seen"
    private const val SEEN_MS = 10 * 60 * 1000L

    /** Apps whose notifications are worth reading. */
    val PAY_APPS = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "com.phonepe.app.preprod",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "in.amazon.mShop.android.shopping",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.bharatpe.app",
        "com.mobikwik_new",
        "com.freecharge.android",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.messaging"
    )

    fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun url(c: Context): String = prefs(c).getString(KEY_URL, "") ?: ""

    fun onlyPayApps(c: Context): Boolean = prefs(c).getBoolean(KEY_ONLY_PAY, true)

    fun fromPaymentApp(pkg: String): Boolean {
        if (PAY_APPS.contains(pkg)) return true
        val p = pkg.lowercase()
        return p.contains("bank") || p.contains("upi") || p.contains("pay")
    }

    /** True when the text reads like money that came in, not money that went out. */
    fun looksLikeMoneyIn(text: String): Boolean {
        val t = text.lowercase()
        val amount = Regex("(rs\\.?|inr|₹)\\s*[0-9]").containsMatchIn(t) ||
            Regex("[0-9][0-9,]*(\\.[0-9]{1,2})?\\s*(rupees|rs\\b|inr\\b)").containsMatchIn(t)
        if (!amount) return false
        val out = Regex("\\b(debited|debit|spent|withdrawn|paid to|sent to|sending|requesting|request)\\b")
        if (out.containsMatchIn(t)) return false
        val inn = Regex("\\b(credited|credit|received|deposit|deposited|added to)\\b")
        return inn.containsMatchIn(t)
    }

    /** Payment apps repeat the same notification, so the same text is only sent once every ten minutes. */
    @Synchronized
    fun seenRecently(c: Context, text: String): Boolean {
        val now = System.currentTimeMillis()
        val key = text.hashCode().toString()
        val kept = StringBuilder()
        var found = false
        for (part in (prefs(c).getString(KEY_SEEN, "") ?: "").split("|")) {
            if (part.isEmpty()) continue
            val bits = part.split(":")
            if (bits.size != 2) continue
            val ts = bits[1].toLongOrNull() ?: continue
            if (now - ts > SEEN_MS) continue
            if (bits[0] == key) found = true
            if (kept.isNotEmpty()) kept.append("|")
            kept.append(part)
        }
        if (!found) {
            if (kept.isNotEmpty()) kept.append("|")
            kept.append(key).append(":").append(now)
        }
        prefs(c).edit().putString(KEY_SEEN, kept.toString()).apply()
        return found
    }

    fun log(c: Context, line: String) {
        val stamp = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
        val old = (prefs(c).getString(KEY_LOG, "") ?: "").split("\n").filter { it.isNotBlank() }
        val all = (listOf("$stamp  $line") + old).take(30)
        prefs(c).edit().putString(KEY_LOG, all.joinToString("\n")).apply()
    }

    fun logText(c: Context): String {
        val s = prefs(c).getString(KEY_LOG, "") ?: ""
        return if (s.isBlank()) "Nothing sent yet." else s
    }

    /** Posts the text to the shop server. [done] is called on the main thread. */
    fun send(c: Context, text: String, done: ((String) -> Unit)? = null) {
        val target = url(c)
        val reply = { msg: String ->
            log(c, msg)
            if (done != null) Handler(Looper.getMainLooper()).post { done(msg) }
        }
        if (target.isBlank()) {
            reply("Not sent: the shop address is empty")
            return
        }
        Thread {
            try {
                val conn = URL(target).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 12000
                conn.readTimeout = 12000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                val body = "text=" + URLEncoder.encode(text, "UTF-8")
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }
                val code = conn.responseCode
                val page = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                val head = Regex("<h1>(.*?)</h1>").find(page)?.groupValues?.get(1) ?: "Server said $code"
                reply(head.replace("&#8377;", "₹"))
            } catch (e: Exception) {
                reply("Could not reach the shop: " + (e.message ?: e.toString()))
            }
        }.start()
    }
}
