package eu.hxreborn.biometricapplock.root

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.hxreborn.biometricapplock.prefs.Prefs

class RootBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = context.getSharedPreferences(Prefs.GROUP, Context.MODE_PRIVATE)
            if (Prefs.ROOT_PROTECTION_ENABLED.read(prefs)) {
                RootLockService.start(context)
            }
        }
    }
}
