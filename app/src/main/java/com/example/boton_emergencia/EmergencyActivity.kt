package com.example.boton_emergencia

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
        private const val REQUEST_CODE_SELECT_CONTACT = 1002
        private const val ENFERMERIA_WHATSAPP = "+524493935203"
        private const val TUTOR_WHATSAPP = "+524651012895"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency)

        db = DbHelper(this)
        controlNumber = intent.getStringExtra("CONTROL_NUMBER")

        if (intent.hasExtra("WIDGET_ACTION")) {
            val widgetAction = intent.getStringExtra("WIDGET_ACTION")
            handleWidgetAction(widgetAction)
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
            return
        }

        val selectedContactId = intent.getLongExtra("selectedContactId", -1L)
        if (selectedContactId > 0) {
            sendToContact(selectedContactId, "ayuda a mi contacto cercano")
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
                handleContactAction(reason)
            } else {
                val targetNumber = when (v.id) {
                    R.id.enfermeriaButton -> ENFERMERIA_WHATSAPP
                    R.id.tutorButton -> TUTOR_WHATSAPP
                    else -> ""
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
        when (action) {
            WidgetReceiver.TYPE_ENFERMERIA -> sendWhatsAppMessage("atención médica urgente en enfermería", ENFERMERIA_WHATSAPP)
            WidgetReceiver.TYPE_TUTOR -> sendWhatsAppMessage("ayuda a mi tutor", TUTOR_WHATSAPP)
            WidgetReceiver.TYPE_CONTACTO -> handleContactAction("ayuda a mi contacto cercano")
        }
    }

    private fun handleContactAction(reason: String) {
        val prefs = getSharedPreferences("contact_prefs", MODE_PRIVATE)
        val key = "selected_${controlNumber}"
        val selectedId = prefs.getLong(key, -1L)

        if (selectedId > 0) {
            sendToContact(selectedId, reason)
            return
        }

        val cursor = db.getContactsForUser(controlNumber ?: "")
        when (cursor?.count) {
            0, null -> {
                // No contacts, ask to add one
                pendingReason = reason
                val intent = Intent(this, ContactoActivity::class.java)
                intent.putExtra(ContactoActivity.EXTRA_CONTROL_NUMBER, controlNumber)
                startActivityForResult(intent, REQUEST_CODE_CONTACTO)
            }
            1 -> {
                // Only one contact, use it by default
                cursor.moveToFirst()
                val contactId = cursor.getLong(cursor.getColumnIndexOrThrow("contact_id"))
                sendToContact(contactId, reason)
            }
            else -> {
                // Multiple contacts, ask user to select one
                pendingReason = reason
                Toast.makeText(this, "Por favor, selecciona un contacto principal", Toast.LENGTH_LONG).show()
                val intent = Intent(this, ContactListActivity::class.java)
                intent.putExtra(ContactListActivity.EXTRA_CONTROL, controlNumber)
                startActivityForResult(intent, REQUEST_CODE_SELECT_CONTACT)
            }
        }
        cursor?.close()
    }

    private fun sendToContact(contactId: Long, reason: String) {
        try {
            val c = db.getContactById(contactId)
            if (c != null && c.moveToFirst()) {
                val phone = c.getString(c.getColumnIndexOrThrow("phone"))
                c.close()
                sendWhatsAppMessage(reason, phone, contactId)
            } else {
                c?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_CONTACTO -> handleContactAction(pendingReason ?: "")
                REQUEST_CODE_SELECT_CONTACT -> {
                    val selectedId = data?.getLongExtra("selectedContactId", -1L) ?: -1L
                    if (selectedId > 0) {
                        sendToContact(selectedId, pendingReason ?: "")
                    }
                }
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
            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }

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
