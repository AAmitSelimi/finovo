package com.finovo.app

import android.app.Application
import com.finovo.app.data.AppDatabase
import com.finovo.app.data.Repository
import com.finovo.app.notifications.DailyWork
import com.finovo.app.notifications.Notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.content.Context
import com.finovo.app.util.AppLocale
import com.finovo.app.util.CrashGuard
import com.finovo.app.util.attachAppResources

class FinovoApp : Application() {

    lateinit var repository: Repository
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base, AppLocale.resolve(base)))
    }

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        attachAppResources(this)
        Notify.createChannels(this)
        repository = Repository(AppDatabase.get(this).dao())
        appScope.launch {
            runCatching { repository.seedIfEmpty() }
            runCatching { refreshInBackground(this@FinovoApp, repository) }
        }
        runCatching { DailyWork.schedule(this) }
    }

    companion object {
        /**
         * Creates recurring entries that became due and re-arms every reminder.
         * Called on app start, after boot and once a day - safe to call repeatedly.
         */
        suspend fun refreshInBackground(context: android.content.Context, repository: Repository) {
            runCatching { repository.seedIfEmpty() }
            val settings = runCatching { repository.currentSettings() }.getOrNull()
            if (settings?.autoCreateRecurring != false) {
                runCatching { repository.generateRecurringEntries() }
            }
            if (settings?.notificationsEnabled != false) {
                runCatching {
                    Notify.rescheduleBillReminders(context, repository, repository.allBills())
                }
                // Modul 15: Finanz-Alarme auch ohne offenen Bildschirm melden
                runCatching { Notify.alarmeMelden(context, repository) }
            }
        }
    }
}
