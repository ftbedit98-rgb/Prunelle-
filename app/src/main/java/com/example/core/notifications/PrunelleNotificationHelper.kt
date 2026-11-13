package com.example.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object PrunelleNotificationHelper {
    const val CHANNEL_MESSAGES = "prunelle_messages"
    const val CHANNEL_CALLS = "prunelle_calls"
    const val CHANNEL_UPDATES = "prunelle_updates"
    const val CHANNEL_BIRTHDAYS = "prunelle_birthdays"

    const val EXTRA_CONVERSATION_ID = "prunelle_extra_conversation_id"
    const val EXTRA_ROUTE_TAB = "prunelle_extra_route_tab"
    const val EXTRA_OPEN_BIRTHDAY = "prunelle_extra_open_birthday"

    const val TAB_HOME = "home"
    const val TAB_CALLS = "calls"
    const val TAB_UPDATES = "updates"
    const val TAB_NOTIFICATIONS = "notifications"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    "Messages & Groupes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Nouveaux messages privés, réponses et réactions" },
                NotificationChannel(
                    CHANNEL_CALLS,
                    "Appels audio et vidéo",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Appels entrants Prunelle" },
                NotificationChannel(
                    CHANNEL_UPDATES,
                    "Chaînes & Statuts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Nouvelles publications et interactions de statut" },
                NotificationChannel(
                    CHANNEL_BIRTHDAYS,
                    "Anniversaires",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Vœux personnels et rappels d'anniversaires des contacts" }
            )
            manager.createNotificationChannels(channels)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        conversationId: String? = null,
        routeTab: String? = null,
        openBirthday: Boolean = false,
        isIncomingCall: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return

        ensureChannels(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!conversationId.isNullOrBlank()) putExtra(EXTRA_CONVERSATION_ID, conversationId)
            if (!routeTab.isNullOrBlank()) putExtra(EXTRA_ROUTE_TAB, routeTab)
            if (openBirthday) putExtra(EXTRA_OPEN_BIRTHDAY, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when {
            isIncomingCall -> NotificationCompat.PRIORITY_MAX
            channelId == CHANNEL_MESSAGES || channelId == CHANNEL_CALLS -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val category = when {
            isIncomingCall || channelId == CHANNEL_CALLS -> NotificationCompat.CATEGORY_CALL
            channelId == CHANNEL_MESSAGES -> NotificationCompat.CATEGORY_MESSAGE
            else -> NotificationCompat.CATEGORY_SOCIAL
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setCategory(category)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
