package com.example.boton_emergencia

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.boton_emergencia.db.DbHelper

class ContactoActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CONTROL_NUMBER = "CONTROL_NUMBER"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacto)

        val input = findViewById<EditText>(R.id.contactNumberInput)
        val labelInput = findViewById<EditText?>(R.id.contactLabelInput)
        val saveButton = findViewById<Button>(R.id.saveContactButton)

        val controlNumber = intent.getStringExtra(EXTRA_CONTROL_NUMBER) ?: ""
        val db = DbHelper(this)
        val exec = java.util.concurrent.Executors.newSingleThreadExecutor()

        saveButton?.setOnClickListener {
            val number = input?.text.toString().trim()
            val label = labelInput?.text.toString().trim()
            if (number.isNotEmpty()) {
                // Normalize and validate phone
                val digits = PhoneUtils.normalize(number)
                val msg = PhoneUtils.validationMessage(digits)
                if (msg.isNotEmpty()) {
                    input?.error = msg
                    return@setOnClickListener
                }
                // Verify control number exists
                if (controlNumber.isEmpty() || !db.userExists(controlNumber)) {
                    input?.error = "No se encontró usuario. Inicia sesión primero."
                    return@setOnClickListener
                }

                // If contactId extra exists -> update flow
                val contactId = intent.getLongExtra("contactId", -1L)
                exec.execute {
                    val id: Long = if (contactId > 0) {
                        val rows = db.updateContact(contactId, digits, label)
                        if (rows > 0) contactId else -1L
                    } else {
                        db.addContact(controlNumber, digits, label)
                    }
                    runOnUiThread {
                        if (id == -1L) {
                            Toast.makeText(this, "Error al guardar el contacto", Toast.LENGTH_SHORT).show()
                        } else {
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                    }
                }
            } else {
                input?.error = "Ingresa un número"
            }
        }

        // shutdown executor when activity destroyed
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                exec.shutdown()
            }
        })
    }
}
