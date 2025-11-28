package com.example.boton_emergencia

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.boton_emergencia.db.DbHelper
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmergencyActivity : AppCompatActivity() {
    private var controlNumber: String? = null
    private var pendingReason: String? = null
    private lateinit var db: DbHelper

    companion object {
        private const val REQUEST_CODE_CONTACTO = 1001
        private const val ENFERMERIA_WHATSAPP = "+524493935203"
        private const val TUTOR_WHATSAPP = "+524651130447"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency)

        db = DbHelper(this)
        controlNumber = intent.getStringExtra("CONTROL_NUMBER")

        // --- Widget Handling ---
        if (intent.hasExtra("WIDGET_ACTION")) {
            val widgetAction = intent.getStringExtra("WIDGET_ACTION")
            handleWidgetAction(widgetAction)
            // Finish the activity after a short delay to allow WhatsApp to open
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
            return // Skip normal button setup
        }

        val selectedContactId = intent.getLongExtra("selectedContactId", -1L)
        if (selectedContactId > 0) {
            try {
                val c = db.getContactById(selectedContactId)
                if (c != null && c.moveToFirst()) {
                    val contactId = c.getLong(c.getColumnIndexOrThrow("contact_id"))
                    val phone = c.getString(c.getColumnIndexOrThrow("phone"))
                    c.close()
                    sendWhatsAppMessage("ayuda a mi contacto cercano", phone, contactId)
                } else {
                    c?.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val enfermeriaButton = findViewById<Button>(R.id.enfermeriaButton)
        val contactoButton = findViewById<Button>(R.id.contactoButton)
        val contactoEditButton = findViewById<android.widget.ImageButton>(R.id.contactoEditButton)
        val tutorButton = findViewById<Button>(R.id.tutorButton)

        val listener = View.OnClickListener { v ->
            val reason = when (v.id) {
                R.id.enfermeriaButton -> "atención médica urgente en enfermería"
                R.id.contactoButton -> "ayuda a mi contacto cercano"
                R.id.tutorButton -> "ayuda a mi tutor"
                else -> ""
            }

            if (v.id == R.id.contactoButton) {
                handleContactButtonClick(reason)
            } else {
                val targetNumber = when (v.id) {
                    R.id.enfermeriaButton -> ENFERMERIA_WHATSAPP
                    R.id.tutorButton -> TUTOR_WHATSAPP
                    else -> "" // Should not happen
                }
                sendWhatsAppMessage(reason, targetNumber)
            }
        }

        enfermeriaButton.setOnClickListener(listener)
        contactoButton.setOnClickListener(listener)
        contactoEditButton.setOnClickListener {
            val i = Intent(this, ContactListActivity::class.java)
            i.putExtra(ContactListActivity.EXTRA_CONTROL, controlNumber)
            startActivity(i)
        }
        tutorButton.setOnClickListener(listener)
    }

    private fun handleWidgetAction(action: String?) {
        val reason: String
        val targetNumber: String?

        when (action) {
            WidgetReceiver.TYPE_ENFERMERIA -> {
                reason = "atención médica urgente en enfermería"
                targetNumber = ENFERMERIA_WHATSAPP
                sendWhatsAppMessage(reason, targetNumber)
            }
            WidgetReceiver.TYPE_TUTOR -> {
                reason = "ayuda a mi tutor"
                targetNumber = TUTOR_WHATSAPP
                sendWhatsAppMessage(reason, targetNumber)
            }
            WidgetReceiver.TYPE_CONTACTO -> {
                reason = "ayuda a mi contacto cercano"
                handleContactAction(reason)
            }
        }
    }

    private fun handleContactAction(reason: String) {
        val cursor = db.getContactsForUser(controlNumber ?: "")
        if (cursor == null || !cursor.moveToFirst()) {
            Toast.makeText(this, "No tienes un contacto cercano guardado", Toast.LENGTH_LONG).show()
            // Optionally, open the app to add a contact
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        } else {
            val contactId = cursor.getLong(cursor.getColumnIndexOrThrow("contact_id"))
            val phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"))
            cursor.close()
            sendWhatsAppMessage(reason, phone, contactId)
        }
    }

    private fun handleContactButtonClick(reason: String) {
        val cursor = db.getContactsForUser(controlNumber ?: "")
        if (cursor == null || !cursor.moveToFirst()) {
            pendingReason = reason
            val intent = Intent(this, ContactoActivity::class.java)
            intent.putExtra(ContactoActivity.EXTRA_CONTROL_NUMBER, controlNumber)
            startActivityForResult(intent, REQUEST_CODE_CONTACTO)
            cursor?.close()
        } else {
            val contactId = cursor.getLong(cursor.getColumnIndexOrThrow("contact_id"))
            val phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"))
            cursor.close()
            sendWhatsAppMessage(reason, phone, contactId)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CONTACTO && resultCode == Activity.RESULT_OK) {
            val cursor = db.getContactsForUser(controlNumber ?: "")
            if (cursor != null && cursor.moveToFirst() && !pendingReason.isNullOrEmpty()) {
                val contactId = cursor.getLong(cursor.getColumnIndexOrThrow("contact_id"))
                val phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"))
                cursor.close()
                sendWhatsAppMessage(pendingReason, phone, contactId)
            }
            pendingReason = null
        }
    }

    private fun sendWhatsAppMessage(reason: String?, phoneNumber: String, contactIdForLog: Long? = null) {
        val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        val formattedNumber = PhoneUtils.formatPhoneNumberForWhatsApp(phoneNumber)
        val message = buildAlertMessage(reason, currentTime)

        try {
            val url = "https://api.whatsapp.com/send?phone=$formattedNumber&text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
            }

            if (packageManager.resolveActivity(intent, 0) != null) {
                db.addAlert(controlNumber ?: "", contactIdForLog, message)
                startActivity(intent)
            } else {
                Toast.makeText(this, "WhatsApp no está instalado.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al enviar el mensaje.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildAlertMessage(reason: String?, currentTime: String): String {
        val controlDisplay = controlNumber?.takeIf { it.isNotBlank() } ?: "SIN_NUMERO_CONTROL"
        val header = "EMERGENCIA - $controlDisplay - Por favor comparte tu ubicación AHORA"
        val details = mutableListOf<String>()
        if (!reason.isNullOrBlank()) {
            details.add("Motivo: $reason")
        }
        details.add("Hora: $currentTime")

        return buildString {
            append(header)
            if (details.isNotEmpty()) {
                append("\n\n")
                append(details.joinToString("\n"))
            }
        }
    }
}