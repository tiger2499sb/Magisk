package com.topjohnwu.magisk.model.receiver

import android.content.ContextWrapper
import android.content.Intent
import com.topjohnwu.magisk.Config
import com.topjohnwu.magisk.Const
import com.topjohnwu.magisk.Info
import com.topjohnwu.magisk.base.BaseReceiver
import com.topjohnwu.magisk.data.database.PolicyDao
import com.topjohnwu.magisk.data.database.base.su
import com.topjohnwu.magisk.extensions.reboot
import com.topjohnwu.magisk.intent
import com.topjohnwu.magisk.model.download.DownloadService
import com.topjohnwu.magisk.model.entity.ManagerJson
import com.topjohnwu.magisk.model.entity.internal.Configuration
import com.topjohnwu.magisk.model.entity.internal.DownloadSubject
import com.topjohnwu.magisk.ui.surequest.SuRequestActivity
import com.topjohnwu.magisk.utils.SuLogger
import com.topjohnwu.magisk.view.Notifications
import com.topjohnwu.magisk.view.Shortcuts
import com.topjohnwu.superuser.Shell
import org.koin.core.inject

open class GeneralReceiver : BaseReceiver() {

    private val policyDB: PolicyDao by inject()

    companion object {
        const val REQUEST = "request"
        const val LOG = "log"
        const val NOTIFY = "notify"
        const val TEST = "test"
    }

    private fun getPkg(intent: Intent): String {
        return intent.data?.encodedSchemeSpecificPart.orEmpty()
    }

    override fun onReceive(context: ContextWrapper, intent: Intent?) {
        intent ?: return
        when (intent.action ?: return) {
            Intent.ACTION_REBOOT, Intent.ACTION_BOOT_COMPLETED -> {
                val action = intent.getStringExtra("action")
                if (action == null) {
                    // Actual boot completed event
                    Shell.su("mm_patch_dtbo").submit {
                        if (it.isSuccess)
                            Notifications.dtboPatched(context)
                    }
                    return
                }
                when (action) {
                    REQUEST -> {
                        val i = context.intent(SuRequestActivity::class.java)
                                .setAction(action)
                                .putExtra("socket", intent.getStringExtra("socket"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        context.startActivity(i)
                    }
                    LOG -> SuLogger.handleLogs(intent)
                    NOTIFY -> SuLogger.handleNotify(intent)
                    TEST -> Shell.su("magisk --use-broadcast").submit()
                }
            }
            Intent.ACTION_PACKAGE_REPLACED ->
                // This will only work pre-O
                if (Config.suReAuth)
                    policyDB.delete(getPkg(intent)).blockingGet()
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                val pkg = getPkg(intent)
                policyDB.delete(pkg).blockingGet()
                "magiskhide --rm $pkg".su().blockingGet()
            }
            Intent.ACTION_LOCALE_CHANGED -> Shortcuts.setup(context)
            Const.Key.BROADCAST_MANAGER_UPDATE -> {
                intent.getParcelableExtra<ManagerJson>(Const.Key.INTENT_SET_APP)?.let {
                    Info.remote = Info.remote.copy(app = it)
                }
                DownloadService(context) {
                    subject = DownloadSubject.Manager(Configuration.APK.Upgrade)
                }
            }
            Const.Key.BROADCAST_REBOOT -> reboot()
        }
    }
}
