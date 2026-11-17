package com.beeone.waassistant

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        val apiKey = findViewById<EditText>(R.id.apiKey)
        val model = findViewById<Spinner>(R.id.model)
        val myLang = findViewById<EditText>(R.id.myLanguage)
        val notes = findViewById<EditText>(R.id.notes)
        val personal = findViewById<MaterialSwitch>(R.id.includePersonal)
        val groups = findViewById<MaterialSwitch>(R.id.includeGroups)

        val labels = listOf(
            "Claude Opus 5.5 (best quality)",
            "Claude Sonnet 5.5 (faster)",
            "Claude Haiku 5.5 (fastest, cheapest)",
        )
        model.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        model.setSelection(Prefs.MODELS.indexOf(prefs.model).coerceAtLeast(0))

        apiKey.setText(prefs.apiKey)
        myLang.setText(prefs.myLanguage)
        notes.setText(prefs.businessNotes)
        personal.isChecked = prefs.includePersonalWhatsApp
        groups.isChecked = prefs.includeGroups

        findViewById<Button>(R.id.btnListener).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btnPostNotif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        fun save() {
            prefs.apiKey = apiKey.text.toString()
            prefs.model = Prefs.MODELS[model.selectedItemPosition.coerceIn(0, Prefs.MODELS.lastIndex)]
            prefs.myLanguage = myLang.text.toString()
            prefs.businessNotes = notes.text.toString()
            prefs.includePersonalWhatsApp = personal.isChecked
            prefs.includeGroups = groups.isChecked
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            save()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            save()
            if (prefs.apiKey.isBlank()) {
                Toast.makeText(this, "Enter your Claude API key first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Assistant.runTest(this)
            Toast.makeText(this, "Check your notifications", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val postOn = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (listenerOn && !WaListenerService.connected) {
            NotificationListenerService.requestRebind(ComponentName(this, WaListenerService::class.java))
        }
        val waInstalled = try {
            packageManager.getPackageInfo(WaListenerService.PKG_BUSINESS, 0); true
        } catch (_: PackageManager.NameNotFoundException) { false }

        findViewById<TextView>(R.id.status).text = buildString {
            append(if (listenerOn) "✅ Notification access on\n" else "❌ Notification access OFF\n")
            append(if (postOn) "✅ Pop-ups allowed\n" else "❌ Pop-ups not allowed\n")
            append(if (waInstalled) "✅ WhatsApp Business found\n" else "⚠️ WhatsApp Business not installed\n")
            append(if (prefs.apiKey.isNotBlank()) "✅ API key set" else "❌ API key missing")
        }
    }
}
