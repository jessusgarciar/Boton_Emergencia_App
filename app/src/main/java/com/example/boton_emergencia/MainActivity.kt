package com.example.boton_emergencia

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.boton_emergencia.db.DbHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var controlNumber: EditText
    private lateinit var password: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var exec: ExecutorService

    companion object {
        private const val PREFS_NAME = "BotonEmergenciaPrefs"
        private const val KEY_CONTROL_NUMBER = "logged_in_control_number"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for a saved session
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedControlNumber = prefs.getString(KEY_CONTROL_NUMBER, null)

        if (savedControlNumber != null) {
            // If a session exists, go directly to EmergencyActivity
            goToEmergencyActivity(savedControlNumber)
            return // Finish onCreate early
        }

        // If no session, show the login screen
        setContentView(R.layout.activity_main)

        controlNumber = findViewById(R.id.controlNumber)
        password = findViewById(R.id.password)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)

        val db = DbHelper(this)
        exec = Executors.newSingleThreadExecutor()

        registerButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        loginButton.setOnClickListener {
            val userControlNumber = controlNumber.text.toString().trim()
            val userPassword = password.text.toString()

            if (userControlNumber.isEmpty() || userPassword.isEmpty()) {
                Toast.makeText(
                    this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT
                ).show()
            } else {
                if (userControlNumber.length < 4) {
                    controlNumber.error = "Número de control inválido"
                    return@setOnClickListener
                }

                exec.execute {
                    val ok = db.checkUser(userControlNumber, userPassword)
                    runOnUiThread {
                        if (ok) {
                            // On successful login, save the session
                            prefs.edit().putString(KEY_CONTROL_NUMBER, userControlNumber).apply()

                            val cursor = db.getContactsForUser(userControlNumber)
                            if (cursor == null || !cursor.moveToFirst()) {
                                androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Agregar contacto cercano")
                                    .setMessage("Parece que no tienes un contacto cercano guardado. ¿Quieres agregar uno ahora?")
                                    .setPositiveButton("Sí") { _, _ ->
                                        val i = Intent(this, ContactoActivity::class.java)
                                        i.putExtra(ContactoActivity.EXTRA_CONTROL_NUMBER, userControlNumber)
                                        startActivityForResult(i, 2001)
                                    }
                                    .setNegativeButton("No") { _, _ ->
                                        goToEmergencyActivity(userControlNumber)
                                    }
                                    .setCancelable(false)
                                    .show()
                                cursor?.close()
                            } else {
                                cursor.close()
                                goToEmergencyActivity(userControlNumber)
                            }
                        } else {
                            Toast.makeText(this, "Credenciales inválidas", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun goToEmergencyActivity(userControlNumber: String) {
        val intent = Intent(this, EmergencyActivity::class.java)
        intent.putExtra("CONTROL_NUMBER", userControlNumber)
        startActivity(intent)
        finish() // Close MainActivity so the user can't go back to it
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == Activity.RESULT_OK) {
            val userControlNumber = controlNumber.text.toString().trim()
            goToEmergencyActivity(userControlNumber)
        }
    }

    override fun onDestroy() {
        if (::exec.isInitialized) exec.shutdown()
        super.onDestroy()
    }
}