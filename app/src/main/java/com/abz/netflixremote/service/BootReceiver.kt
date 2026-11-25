package com.abz.netflixremote.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abz.netflixremote.Prefs
import com.abz.netflixremote.Prefs.autostart
import com.abz.netflixremote.Prefs.role

/**
 * Rimette in piedi il servizio dopo un riavvio del tablet o un aggiornamento dell'app:
 * senza questo il telecomando smette di funzionare finche' non si riapre l'app a mano.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                if (context.role == Prefs.ROLE_AGENT && context.autostart) {
                    AgentService.start(context)
                }
            }
        }
    }
}
