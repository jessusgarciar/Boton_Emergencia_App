package com.example.boton_emergencia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.boton_emergencia.db.DbHelper
import com.example.boton_emergencia.PhoneUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmergencyActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: DatabaseReference
    private var controlNumber: String? = null
    private var pendingReason: String? = null
    private lateinit var db: DbHelper

    companion object {
        private const val REQUEST_CODE_CONTACTO = 1001
        private const val REQUEST_CODE_SELECT_CONTACT = 1002
        private const val ENFERMERIA_WHATSAPP = "+524493935203"
        private const val TUTOR_WHATSAPP = "+524651130447"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency)

        try {
            // Inicializar Firebase explícitamente
            val fbInstance = FirebaseDatabase.getInstance()
            database = fbInstance.reference
            Log.d("FIREBASE_TEST", "Firebase inicializado correctamente")
        } catch (e: Exception) {
            Log.e("FIREBASE_TEST", "Error al inicializar Firebase: ${e.message}")
        }

        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
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

            val targetNumber = when (v.id) {
                R.id.enfermeriaButton -> ENFERMERIA_WHATSAPP
                R.id.tutorButton -> TUTOR_WHATSAPP
                else -> ""
            }

            if (v.id == R.id.contactoButton) {
                handleContactAction(reason)
            } else {
                startEmergencyProtocol(reason, targetNumber)
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
            WidgetReceiver.TYPE_ENFERMERIA -> startEmergencyProtocol("atención médica urgente en enfermería", ENFERMERIA_WHATSAPP)
            WidgetReceiver.TYPE_TUTOR -> startEmergencyProtocol("ayuda a mi tutor", TUTOR_WHATSAPP)
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
                pendingReason = reason
                val intent = Intent(this, ContactoActivity::class.java)
                intent.putExtra(ContactoActivity.EXTRA_CONTROL_NUMBER, controlNumber)
                startActivityForResult(intent, REQUEST_CODE_CONTACTO)
            }
            1 -> {
                cursor.moveToFirst()
                val contactId = cursor.getLong(cursor.getColumnIndexOrThrow("contact_id"))
                sendToContact(contactId, reason)
            }
            else -> {
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
                startEmergencyProtocol(reason, phone, contactId)
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

    private fun startEmergencyProtocol(tipoAlerta: String, targetNumber: String, contactIdForLog: Long? = null) {
        // Enviar a la nube primero para asegurar registro
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                registrarEnNube(location?.latitude, location?.longitude, tipoAlerta)
                sendWhatsAppMessage(tipoAlerta, targetNumber, location?.latitude, location?.longitude, contactIdForLog)
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            registrarEnNube(null, null, tipoAlerta)
            sendWhatsAppMessage(tipoAlerta, targetNumber, null, null, contactIdForLog)
        }
    }

    private fun registrarEnNube(lat: Double?, lng: Double?, tipoAlerta: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val alertId = database.child("alertas").push().key ?: "error_id"
        
        val alertData = mapOf(
            "controlNumber" to (controlNumber ?: "Desconocido"),
            "tipo" to tipoAlerta,
            "latitud" to (lat ?: 0.0),
            "longitud" to (lng ?: 0.0),
            "fecha" to timestamp
        )

        Log.d("FIREBASE_TEST", "Intentando guardar: $alertData")

        database.child("alertas").child(alertId).setValue(alertData)
            .addOnSuccessListener {
                Log.d("FIREBASE_TEST", "¡Éxito! Datos guardados en Firebase")
                Toast.makeText(this, "Datos en la nube: OK", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE_TEST", "Fallo al guardar: ${e.message}")
                Toast.makeText(this, "Error Nube: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sendWhatsAppMessage(reason: String?, phoneNumber: String, lat: Double? = null, lng: Double? = null, contactIdForLog: Long? = null) {
        val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val formattedNumber = PhoneUtils.formatPhoneNumberForWhatsApp(phoneNumber)
        val message = buildAlertMessage(reason, currentTime, lat, lng)

        try {
            val url = "https://api.whatsapp.com/send?phone=$formattedNumber&text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
            db.addAlert(controlNumber ?: "", contactIdForLog, message)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al abrir WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildAlertMessage(reason: String?, currentTime: String, lat: Double? = null, lng: Double? = null): String {
        val controlDisplay = controlNumber?.takeIf { it.isNotBlank() } ?: "SIN_NUMERO"
        val header = "🚨 EMERGENCIA - $controlDisplay 🚨"
        val loc = if (lat != null && lng != null) "https://www.google.com/maps?q=$lat,$lng" else "No disponible"
        
        return "$header\n\nMotivo: $reason\nHora: $currentTime\nUbicación: $loc"
    }
}
