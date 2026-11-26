package com.muthu.disciplinex.lock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.data.tr

/**
 * Device-admin entry for DisciplineX. It asks for NO policies (no wipe, no lock, nothing);
 * it exists only because Android refuses to uninstall an active device-admin app until
 * the admin is switched off first. That is one more step in Settings (which the app-lock
 * blocks while a challenge is pending or missed), and the app-lock asks for the uninstall
 * password on that screen too.
 */
class DisciplineAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        AppSettings.load(context)
        return tr(
            "Uninstall protection will stop working. Your challenge lock can then be bypassed.",
            "நிறுவல் நீக்கப் பாதுகாப்பு செயலிழக்கும். சவால் பூட்டை தவிர்க்க முடியும்."
        )
    }
}
