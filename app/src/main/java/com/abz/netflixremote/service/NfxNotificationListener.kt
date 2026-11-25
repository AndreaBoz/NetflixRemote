package com.abz.netflixremote.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Non legge notifiche: serve solo perche' MediaSessionManager.getActiveSessions()
 * richiede un NotificationListener abilitato dall'utente.
 */
class NfxNotificationListener : NotificationListenerService() {
    companion object {
        fun component(c: Context) = ComponentName(c, NfxNotificationListener::class.java)

        fun isEnabled(c: Context): Boolean {
            val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == c.packageName }
        }
    }
}
