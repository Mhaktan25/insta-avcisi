package com.example.instadmlogger

import android.app.Activity
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("InstaLoggerPrefs", Context.MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val infoText = TextView(this).apply {
            text = "Insta DM Avcısı\n\n1. Token ve Chat ID girip Kaydet'e basın.\n2. Bildirim İzni Ver butonuna basıp izin verin."
            textSize = 16f
            setPadding(0, 0, 0, 30)
        }

        val tokenInput = EditText(this).apply {
            hint = "Telegram Bot Token"
            setText(prefs.getString("BOT_TOKEN", ""))
        }

        val chatIdInput = EditText(this).apply {
            hint = "Telegram Chat ID"
            setText(prefs.getString("CHAT_ID", ""))
        }

        val onlyDeletedSwitch = Switch(this).apply {
            text = "Sadece Silinen / Geri Çekilenleri Gönder"
            isChecked = prefs.getBoolean("ONLY_DELETED", true)
            setPadding(0, 30, 0, 30)
        }

        val saveButton = Button(this).apply {
            text = "Ayarları Kaydet"
            setOnClickListener {
                prefs.edit()
                    .putString("BOT_TOKEN", tokenInput.text.toString().trim())
                    .putString("CHAT_ID", chatIdInput.text.toString().trim())
                    .putBoolean("ONLY_DELETED", onlyDeletedSwitch.isChecked)
                    .apply()
                Toast.makeText(this@MainActivity, "Kaydedildi!", Toast.LENGTH_SHORT).show()
            }
        }

        val permButton = Button(this).apply {
            text = "Bildirim Okuma İzni Ver"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        layout.addView(infoText)
        layout.addView(tokenInput)
        layout.addView(chatIdInput)
        layout.addView(onlyDeletedSwitch)
        layout.addView(saveButton)
        layout.addView(permButton)
        setContentView(layout)
    }
}

class InstaNotificationService : NotificationListenerService() {
    private val messageCache = HashMap<String, String>()
    private var lastSentMessage = ""

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.packageName != "com.instagram.android") return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: sbn.notification.tickerText?.toString() ?: return

        val lowerText = text.lowercase()
        if (lowerText.contains("geri çekti") || lowerText.contains("unsent")) return

        val fullMessage = "👤 $title\n💬 $text"
        messageCache[sbn.key] = fullMessage

        val prefs = getSharedPreferences("InstaLoggerPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("ONLY_DELETED", true)) {
            if (fullMessage != lastSentMessage) {
                lastSentMessage = fullMessage
                sendToTelegram("📩 Yeni Mesaj\n$fullMessage")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null || sbn.packageName != "com.instagram.android") return

        val prefs = getSharedPreferences("InstaLoggerPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("ONLY_DELETED", true)) {
            val cachedMessage = messageCache.remove(sbn.key) ?: run {
                val extras = sbn.notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE) ?: return
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
                "👤 $title\n💬 $text"
            }
            sendToTelegram("🚨 Geri Çekilen Mesaj Yakalandı!\n$cachedMessage")
        }
    }

    private fun sendToTelegram(message: String) {
        val prefs = getSharedPreferences("InstaLoggerPrefs", Context.MODE_PRIVATE)
        val botToken = prefs.getString("BOT_TOKEN", "") ?: ""
        val chatId = prefs.getString("CHAT_ID", "") ?: ""
        if (botToken.isEmpty() || chatId.isEmpty()) return

        thread {
            try {
                val encodedText = URLEncoder.encode(message, "UTF-8")
                val url = URL("https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encodedText")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
