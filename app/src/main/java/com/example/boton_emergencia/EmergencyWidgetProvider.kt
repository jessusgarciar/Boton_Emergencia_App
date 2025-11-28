package com.example.boton_emergencia

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

open class EmergencyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, getWidgetType(javaClass))
        }
    }

    private fun getWidgetType(widgetClass: Class<*>): String {
        return when (widgetClass) {
            EnfermeriaWidget::class.java -> WidgetReceiver.TYPE_ENFERMERIA
            TutorWidget::class.java -> WidgetReceiver.TYPE_TUTOR
            ContactoWidget::class.java -> WidgetReceiver.TYPE_CONTACTO
            else -> ""
        }
    }

    companion object {
        internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, widgetType: String) {
            val layoutId = when (widgetType) {
                WidgetReceiver.TYPE_ENFERMERIA -> R.layout.widget_enfermeria
                WidgetReceiver.TYPE_TUTOR -> R.layout.widget_tutor
                WidgetReceiver.TYPE_CONTACTO -> R.layout.widget_contacto
                else -> R.layout.widget_enfermeria // A default layout
            }
            val views = RemoteViews(context.packageName, layoutId)

            // Create an Intent to launch WidgetReceiver
            val intent = Intent(context, WidgetReceiver::class.java).apply {
                action = WidgetReceiver.ACTION_WIDGET_CLICK
                putExtra(WidgetReceiver.EXTRA_WIDGET_TYPE, widgetType)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId, // Use widget ID as request code to ensure uniqueness
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

// Specific provider classes for each widget type
class EnfermeriaWidget : EmergencyWidgetProvider()
class TutorWidget : EmergencyWidgetProvider()
class ContactoWidget : EmergencyWidgetProvider()