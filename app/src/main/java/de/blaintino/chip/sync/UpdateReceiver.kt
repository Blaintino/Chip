package de.blaintino.chip.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.blaintino.chip.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Nach einem App-Update (und nach dem Neustart des Handys): Hintergrundarbeit neu anmelden und das
 * Widget neu zeichnen. Ein vor dem Update platziertes Widget hatte sich sonst nie wieder gemeldet
 * (vom Nutzer beobachtet, 2026-11-02; erst Entfernen und neu Platzieren half).
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED && intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        Background.schedule(app)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                TodayWidget.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }
}
