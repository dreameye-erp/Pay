package com.dreameye.upibridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Reads payment notifications and hands them to the shop server. */
class UpiListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        try {
            if (Sender.onlyPayApps(this) && !Sender.fromPaymentApp(n.packageName)) return
            val x = n.notification?.extras ?: return
            val parts = listOf("android.title", "android.text", "android.bigText", "android.subText")
                .mapNotNull { x.getCharSequence(it)?.toString() }
                .filter { it.isNotBlank() }
            if (parts.isEmpty()) return
            val text = parts.joinToString(" ").trim()
            if (!Sender.looksLikeMoneyIn(text)) return
            if (Sender.seenRecently(this, text)) return
            Sender.send(this, text)
        } catch (e: Exception) {
            Sender.log(this, "Could not read a notification: " + (e.message ?: ""))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
