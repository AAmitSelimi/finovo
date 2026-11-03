package com.finovo.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.finovo.app.FinovoApp
import com.finovo.app.data.AppDatabase
import com.finovo.app.data.BillStatus
import com.finovo.app.data.NotificationType
import com.finovo.app.data.Repository
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.finovo.app.util.S
import com.finovo.app.util.str

/**
 * Fires the actual reminder. Uses goAsync() so the work survives the receiver lifetime.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val repository = Repository(AppDatabase.get(context).dao())
        val notificationId = intent.getIntExtra(Notify.EXTRA_NOTIFICATION_ID, 0)
        val title = intent.getStringExtra(Notify.EXTRA_TITLE) ?: S(context, "app_name", "Finovo")
        val message = intent.getStringExtra(Notify.EXTRA_MESSAGE)
        val billId = intent.getLongExtra(Notify.EXTRA_BILL_ID, -1L)
        val refKey = intent.getStringExtra(Notify.EXTRA_REF_KEY) ?: ""
        val type = intent.getStringExtra(Notify.EXTRA_TYPE)
            ?: NotificationType.BILL_REMINDER.name

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = repository.currentSettings()
                if (settings.notificationsEnabled && Notify.hasPermission(context)) {
                    val bill = if (billId > 0) {
                        repository.allBills().firstOrNull { it.id == billId }
                    } else null
                    val stillRelevant = bill == null ||
                        bill.status != BillStatus.PAID.name ||
                        bill.dueDate >= Dates.today().toEpochDay()

                    if (stillRelevant) {
                        val text = message ?: moneyReminderFallback(repository, billId)
                        val channel = if (type == NotificationType.BUDGET_WARNING.name) {
                            Notify.CHANNEL_BUDGET
                        } else Notify.CHANNEL_BILLS

                        Notify.show(
                            context = context,
                            id = notificationId,
                            title = title,
                            message = text,
                            channel = channel,
                            billId = billId.takeIf { it > 0 },
                            refKey = refKey,
                            type = runCatching { NotificationType.valueOf(type) }
                                .getOrDefault(NotificationType.BILL_REMINDER),
                            addMarkPaidAction = billId > 0 && bill?.status != BillStatus.PAID.name
                        )
                        if (refKey.isNotBlank()) repository.markNotificationDelivered(refKey)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun moneyReminderFallback(repository: Repository, billId: Long): String {
        val bill = repository.allBills().firstOrNull { it.id == billId } ?: return str("notif_payment_due_short", "Zahlung fällig.")
        val symbol = repository.currentSettings().currencySymbol
        return str(
            "notif_payment_due_on",
            "%1\$s – Zahlung fällig am %2\$s.",
            Money.format(bill.amountCents, symbol),
            Dates.full(Dates.fromEpochDay(bill.dueDate))
        )
    }
}

/** Handles the "Als bezahlt markieren" action on a reminder notification. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notify.ACTION_MARK_PAID) return
        val billId = intent.getLongExtra(Notify.EXTRA_MARK_PAID_BILL_ID, -1L)
        val notificationId = intent.getIntExtra(Notify.EXTRA_MARK_PAID_NOTIFICATION_ID, 0)
        if (billId <= 0L) return
        val pendingResult = goAsync()
        val repository = Repository(AppDatabase.get(context).dao())
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bill = repository.allBills().firstOrNull { it.id == billId }
                if (bill != null && bill.status != BillStatus.PAID.name) {
                    val settings = repository.currentSettings()
                    repository.markBillPaid(
                        bill = bill,
                        createExpense = settings.autoExpenseOnBillPaid
                    )
                    Notify.cancelBillReminder(context, billId)
                    Notify.rescheduleBillReminders(context, repository, repository.allBills())
                }
                runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Runs once a day: creates due recurring entries and refreshes all alarms. */
class DailyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val repository = Repository(AppDatabase.get(context).dao())
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FinovoApp.refreshInBackground(context, repository)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Restores alarms and recurring entries after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != null &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_DATE_CHANGED
        ) return
        val pendingResult = goAsync()
        val repository = Repository(AppDatabase.get(context).dao())
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FinovoApp.refreshInBackground(context, repository)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
