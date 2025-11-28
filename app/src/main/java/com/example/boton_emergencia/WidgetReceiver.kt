package com.example.boton_emergencia

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class WidgetReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WIDGET_CLICK = "com.example.boton_emergencia.ACTION_WIDGET_CLICK"
        const val EXTRA_WIDGET_TYPE = "EXTRA_WIDGET_TYPE"
        const val TYPE_ENFERMERIA = "ENFERMERIA"
        const val TYPE_TUTOR = "TUTOR"
        const val TYPE_CONTACTO = "CONTACTO"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WIDGET_CLICK) {
            val widgetType = intent.getStringExtra(EXTRA_WIDGET_TYPE)

            // Check if user is logged in by reading SharedPreferences
            val prefs = context.getSharedPreferences("BotonEmergenciaPrefs", Context.MODE_PRIVATE)
            val savedControlNumber = prefs.getString("logged_in_control_number", null)

            val targetIntent: Intent
            if (savedControlNumber != null) {
                // User is logged in, launch EmergencyActivity with the specific action
                targetIntent = Intent(context, EmergencyActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("CONTROL_NUMBER", savedControlNumber)
                    putExtra("WIDGET_ACTION", widgetType) // Pass widget type to the activity
                }
            } else {
                // User is not logged in, launch MainActivity to prompt for login
                targetIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                Toast.makeText(context, "Por favor, inicia sesión en la app primero", Toast.LENGTH_LONG).show()
            }
            context.startActivity(targetIntent)
        }
    }
}