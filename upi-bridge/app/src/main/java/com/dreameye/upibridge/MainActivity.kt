package com.dreameye.upibridge

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var address: EditText
    private lateinit var onlyPay: CheckBox
    private lateinit var access: TextView
    private lateinit var logView: TextView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        fun label(text: String, size: Float, bold: Boolean): TextView {
            val t = TextView(this)
            t.text = text
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) t.setTypeface(null, Typeface.BOLD)
            t.setPadding(0, pad / 2, 0, pad / 4)
            return t
        }

        fun button(text: String, click: () -> Unit): Button {
            val b = Button(this)
            b.text = text
            b.setOnClickListener { click() }
            return b
        }

        root.addView(label("Dhanlekh UPI Bridge", 22f, true))
        root.addView(label(
            "This phone watches for UPI payment notifications and tells the shop computer, " +
                "so the open bill fills in by itself.", 14f, false))

        root.addView(label("1. Shop address", 16f, true))
        root.addView(label(
            "In the ERP open Settings, then Data & Backup, then UPI auto-receive, " +
                "then \"Set up the shop phone\". Copy the address shown there and paste it here.", 13f, false))
        address = EditText(this)
        address.setSingleLine(false)
        address.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        address.hint = "http://192.168.1.5:3000/upi-share?t=..."
        address.setText(Sender.url(this))
        root.addView(address)

        root.addView(button("Save address") {
            val v = address.text.toString().trim()
            Sender.prefs(this).edit().putString(Sender.KEY_URL, v).apply()
            Toast.makeText(this, if (v.isBlank()) "Address cleared" else "Address saved", Toast.LENGTH_SHORT).show()
            refresh()
        })

        root.addView(label("2. Let this app read notifications", 16f, true))
        access = label("", 13f, false)
        root.addView(access)
        root.addView(button("Open notification access settings") {
            try {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
            Toast.makeText(this, "Find \"Dhanlekh UPI Bridge\" in the list and turn it on", Toast.LENGTH_LONG).show()
        })

        onlyPay = CheckBox(this)
        onlyPay.text = "Only read payment and bank apps (recommended)"
        onlyPay.isChecked = Sender.onlyPayApps(this)
        onlyPay.setOnCheckedChangeListener { _, on ->
            Sender.prefs(this).edit().putBoolean(Sender.KEY_ONLY_PAY, on).apply()
        }
        root.addView(onlyPay)

        root.addView(label("3. Check it works", 16f, true))
        root.addView(button("Send a test payment of ₹1") {
            val stamp = System.currentTimeMillis().toString().takeLast(12)
            Sender.send(this, "Rs.1.00 credited to your A/c by UPI Ref No $stamp test from the phone") { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                refresh()
            }
        })

        root.addView(label("What was sent", 16f, true))
        logView = label("", 13f, false)
        logView.setTextColor(Color.DKGRAY)
        root.addView(logView)
        root.addView(button("Clear this list") {
            Sender.prefs(this).edit().putString(Sender.KEY_LOG, "").apply()
            refresh()
        })

        val scroll = ScrollView(this)
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)

        shared(intent)
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        shared(i)
    }

    /** A payment message sent here from the Share sheet. */
    private fun shared(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        if (text.isBlank()) return
        Sender.send(this, text) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val on = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        access.text = if (on) "✓ Notification access is on." else "✗ Notification access is off. Nothing will be sent until you turn it on."
        access.setTextColor(if (on) Color.parseColor("#047857") else Color.parseColor("#b91c1c"))
        logView.text = Sender.logText(this)
    }
}
