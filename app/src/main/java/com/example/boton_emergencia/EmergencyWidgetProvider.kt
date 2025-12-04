package com.example.boton_emergencia

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

open class EmergencyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, javaClass)
        }
    }

    companion object {
        internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, widgetClass: Class<*>) {
            val (widgetType, isLarge) = getWidgetInfo(widgetClass)
            if (widgetType.isEmpty()) return

            val layoutId = getLayoutId(widgetType, isLarge)
            val views = RemoteViews(context.packageName, layoutId)

            val intent = Intent(context, WidgetReceiver::class.java).apply {
                action = WidgetReceiver.ACTION_WIDGET_CLICK
                putExtra(WidgetReceiver.EXTRA_WIDGET_TYPE, widgetType)
            }

            val requestCode = appWidgetId * 10 + getTypeCode(widgetType)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                flags
            )

            val clickableViewId = if (isLarge) R.id.widget_container else R.id.widget_button
            views.setOnClickPendingIntent(clickableViewId, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getWidgetInfo(widgetClass: Class<*>): Pair<String, Boolean> {
            return when (widgetClass) {
                EnfermeriaWidget::class.java -> Pair(WidgetReceiver.TYPE_ENFERMERIA, false)
                TutorWidget::class.java -> Pair(WidgetReceiver.TYPE_TUTOR, false)
                ContactoWidget::class.java -> Pair(WidgetReceiver.TYPE_CONTACTO, false)
                EnfermeriaWidgetLarge::class.java -> Pair(WidgetReceiver.TYPE_ENFERMERIA, true)
                TutorWidgetLarge::class.java -> Pair(WidgetReceiver.TYPE_TUTOR, true)
                ContactoWidgetLarge::class.java -> Pair(WidgetReceiver.TYPE_CONTACTO, true)
                else -> Pair("", false)
            }
        }

        private fun getLayoutId(widgetType: String, isLarge: Boolean): Int {
            return when (widgetType) {
                WidgetReceiver.TYPE_ENFERMERIA -> if (isLarge) R.layout.widget_enfermeria_large else R.layout.widget_enfermeria
                WidgetReceiver.TYPE_TUTOR -> if (isLarge) R.layout.widget_tutor_large else R.layout.widget_tutor
                WidgetReceiver.TYPE_CONTACTO -> if (isLarge) R.layout.widget_contacto_large else R.layout.widget_contacto
                else -> R.layout.widget_enfermeria // Fallback
            }
        }

        private fun getTypeCode(widgetType: String): Int {
            return when (widgetType) {
                WidgetReceiver.TYPE_ENFERMERIA -> 1
                WidgetReceiver.TYPE_TUTOR -> 2
                WidgetReceiver.TYPE_CONTACTO -> 3
                else -> 0
            }
        }
    }
}

// Small Widgets
class EnfermeriaWidget : EmergencyWidgetProvider()
class TutorWidget : EmergencyWidgetProvider()
class ContactoWidget : EmergencyWidgetProvider()

// Large Widgets
class EnfermeriaWidgetLarge : EmergencyWidgetProvider()
class TutorWidgetLarge : EmergencyWidgetProvider()
class ContactoWidgetLarge : EmergencyWidgetProvider()
