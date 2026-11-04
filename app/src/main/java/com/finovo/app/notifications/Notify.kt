package com.finovo.app.notifications

import android.Manifest
import android.app.AlarmManager
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
import com.finovo.app.MainActivity
import com.finovo.app.R
import com.finovo.app.data.BillEntity
import com.finovo.app.data.NotificationType
import com.finovo.app.data.Repository
import com.finovo.app.util.Alarm
import com.finovo.app.util.Alarme
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.finovo.app.util.S
import com.finovo.app.util.str

object Notify {

    const val CHANNEL_BILLS = "finovo_bills"
    const val CHANNEL_BUDGET = "finovo_budget"
    const val CHANNEL_RECURRING = "finovo_recurring"

    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_MESSAGE = "extra_message"
    const val EXTRA_BILL_ID = "extra_bill_id"
    const val EXTRA_REF_KEY = "extra_ref_key"
    const val EXTRA_TYPE = "extra_type"

    const val ACTION_MARK_PAID = "com.finovo.app.ACTION_MARK_PAID"
    const val EXTRA_MARK_PAID_BILL_ID = "extra_mark_paid_bill_id"
    const val EXTRA_MARK_PAID_NOTIFICATION_ID = "extra_mark_paid_notification_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val bills = NotificationChannel(
                ReminderTone.channelId(context),
                S(context, "rechnungen_erinnerungen", "Rechnungen & Erinnerungen"),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = S(context, "erinnerungen_an_faellige_rechnungen", "Erinnerungen an fällige Rechnungen")
                enableVibration(true)
                setSound(ReminderTone.soundUri(context), ReminderTone.audioAttributes)
            }
            val budget = NotificationChannel(
                CHANNEL_BUDGET,
                S(context, "budget_warnungen", "Budget-Warnungen"),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = S(context, "warnungen_wenn_das_monatsbudget_fast", "Warnungen, wenn das Monatsbudget fast aufgebraucht ist")
                enableVibration(true)
            }
            val recurring = NotificationChannel(
                CHANNEL_RECURRING,
                S(context, "wiederkehrende_zahlungen", "Wiederkehrende Zahlungen"),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = S(context, "informationen_zu_wiederkehrenden_zah", "Informationen zu wiederkehrenden Zahlungen") }
            manager.createNotificationChannel(bills)
            manager.createNotificationChannel(budget)
            manager.createNotificationChannel(recurring)
        }
    }

    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /** Shows a notification right now. Returns false when the permission is missing. */
    fun show(
        context: Context,
        id: Int,
        title: String,
        message: String,
        channel: String = CHANNEL_BILLS,
        billId: Long? = null,
        refKey: String = "",
        type: NotificationType = NotificationType.BILL_REMINDER,
        addMarkPaidAction: Boolean = false
    ): Boolean {
        if (!hasPermission(context)) return false
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_BILL_ID, billId ?: -1L)
        }
        val openPending = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlags()
        )

        // Der Erinnerungskanal traegt den gewaehlten Ton und kann daher eine
        // neue Kennung haben (siehe ReminderTone).
        val isReminder = channel == CHANNEL_BILLS
        val channelId = if (isReminder) ReminderTone.channelId(context) else channel
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_euro)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
        if (isReminder) {
            // Vor Android 8 gilt der Ton je Benachrichtigung statt je Kanal.
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            builder.setSound(ReminderTone.soundUri(context))
        } else {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        if (addMarkPaidAction && billId != null && billId > 0) {
            val paidIntent = Intent(context, ActionReceiver::class.java).apply {
                action = ACTION_MARK_PAID
                putExtra(EXTRA_MARK_PAID_BILL_ID, billId)
                putExtra(EXTRA_MARK_PAID_NOTIFICATION_ID, id)
            }
            val paidPending = PendingIntent.getBroadcast(
                context,
                id + 500_000,
                paidIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlags()
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    S(context, "als_bezahlt_markieren", "Als bezahlt markieren"),
                    paidPending
                ).build()
            )
        }

        return runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }.isSuccess
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    // ---------------------------- scheduling --------------------------------

    fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager(context).canScheduleExactAlarms()
        } else true
    }

    fun schedule(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        intent: Intent
    ) {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlags()
        )
        val am = alarmManager(context)
        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) {
            // Already in the past -> deliver a few seconds from now.
            val whenMillis = now + 5_000L
            setAlarm(am, whenMillis, pending)
            return
        }
        setAlarm(am, triggerAtMillis, pending)
    }

    private fun setAlarm(am: AlarmManager, whenMillis: Long, pending: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, whenMillis, pending)
            }
        } catch (e: SecurityException) {
            // Exact alarms not allowed -> fall back to a windowed alarm.
            runCatching {
                am.setWindow(AlarmManager.RTC_WAKEUP, whenMillis, 10 * 60 * 1000L, pending)
            }.onFailure {
                am.set(AlarmManager.RTC_WAKEUP, whenMillis, pending)
            }
        }
    }

    fun cancel(context: Context, requestCode: Int, intent: Intent) {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlags()
        )
        alarmManager(context).cancel(pending)
        runCatching { pending.cancel() }
    }

    // ------------------------- bill reminder logic ---------------------------

    fun reminderIntent(context: Context, notificationId: Int): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }

    /**
     * Schedules all reminders for the given bills.
     * Existing alarms for the same reference key are simply overwritten (no duplicates).
     */
    /**
     * Feste Benachrichtigungsnummer je Alarm. Dadurch wird eine bestehende
     * Meldung aktualisiert statt bei jedem Lauf eine neue anzulegen.
     */
    fun stableAlarmId(a: Alarm): Int = 20_000 + a.ordinal

    /**
     * Modul 15: wertet alle aktivierten Finanz-Alarme gegen die echten Daten
     * aus und zeigt fuer jeden zutreffenden Alarm eine Benachrichtigung.
     *
     * Gemeldet wird hoechstens einmal pro Alarm und Tag - sonst wuerde beim
     * jeden Start der App dieselbe Meldung erneut auftauchen.
     */
    suspend fun alarmeMelden(context: Context, repository: Repository) {
        if (!hasPermission(context)) return
        val heute = Dates.today()
        val einstellungen = withContext(Dispatchers.IO) { repository.currentSettings() }
        val meldungen = withContext(Dispatchers.Default) {
            Alarme.pruefen(
                z = Alarme.ausText(einstellungen.alertSettings),
                heute = heute,
                ausgaben = repository.expenses.first(),
                einnahmen = repository.incomes.first(),
                rechnungen = repository.allBills(),
                abos = repository.subscriptions.first(),
                regeln = repository.recurringRules.first(),
                kategorien = repository.categories.first(),
                werte = repository.allAssets(),
                budget = repository.budget.first(),
                symbol = einstellungen.currencySymbol.ifBlank { "\u20AC" }
            )
        }
        if (meldungen.isEmpty()) return
        val protokoll = context.getSharedPreferences("finovo_alarme", Context.MODE_PRIVATE)
        val heuteTag = heute.toEpochDay()
        val bearbeiter = protokoll.edit()
        var geaendert = false
        meldungen.forEach { m ->
            if (protokoll.getLong(m.alarm.key, -1L) == heuteTag) return@forEach
            val kanal = when (m.alarm) {
                Alarm.BUDGET_EXCEEDED, Alarm.UNUSUAL_SPENDING -> CHANNEL_BUDGET
                else -> CHANNEL_BILLS
            }
            if (show(context, stableAlarmId(m.alarm), m.titel, m.text, kanal)) {
                bearbeiter.putLong(m.alarm.key, heuteTag)
                geaendert = true
            }
        }
        if (geaendert) bearbeiter.apply()
    }

    suspend fun rescheduleBillReminders(
        context: Context,
        repository: Repository,
        bills: List<BillEntity>
    ) = withContext(Dispatchers.IO) {
        val today = Dates.today()
        for (bill in bills) {
            val id = stableId(bill.id)
            val intent = reminderIntent(context, id)
            if (!bill.reminderEnabled || bill.status == com.finovo.app.data.BillStatus.PAID.name) {
                cancel(context, id, intent)
                repository.clearNotificationsOfBill(bill.id)
                continue
            }
            val dueDate = Dates.fromEpochDay(bill.dueDate)
            val triggerDate = Dates.plusTage(dueDate, -bill.reminderDaysBefore.toLong())
            val trigger = LocalDateTime.of(
                if (triggerDate < today) today else triggerDate,
                java.time.LocalTime.of(
                    bill.reminderHour.coerceIn(0, 23),
                    bill.reminderMinute.coerceIn(0, 59)
                )
            )
            var triggerMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (triggerMillis <= System.currentTimeMillis()) {
                // The reminder date lies in the past (e.g. an overdue bill).
                // Deliver it exactly once, then stay quiet.
                val alreadyDelivered = repository.notificationByKey(
                    "bill-${bill.id}-${bill.reminderDaysBefore}"
                )?.delivered == true ||
                    repository.notificationByKey(
                        "bill-${bill.id}-${bill.dueDate}-${bill.reminderDaysBefore}"
                    )?.delivered == true
                val daysPast = java.time.temporal.ChronoUnit.DAYS.between(dueDate, today)
                if (alreadyDelivered || daysPast > 30) {
                    cancel(context, id, intent)
                    continue
                }
                triggerMillis = System.currentTimeMillis() + 10_000L
            }
            val dueText = Dates.full(dueDate)
            val message = when {
                bill.reminderDaysBefore == 0 -> str(
                    "notif_due_today",
                    "%1\$s – Zahlung heute fällig (%2\$s).",
                    Money.format(bill.amountCents, currentSymbol(repository)),
                    dueText
                )
                bill.reminderDaysBefore == 1 ->
                    str("notif_due_tomorrow", "%1\$s – Zahlung morgen fällig (%2\$s).", Money.format(bill.amountCents, currentSymbol(repository)), dueText)
                else ->
                    str("notif_due_in_days", "%1\$s – Zahlung in %2\$s Tagen fällig (%3\$s).", Money.format(bill.amountCents, currentSymbol(repository)), bill.reminderDaysBefore, dueText)
            }

            val refKey = "bill-${bill.id}-${bill.dueDate}-${bill.reminderDaysBefore}"
            intent.putExtra(EXTRA_TITLE, bill.name)
            intent.putExtra(EXTRA_MESSAGE, message)
            intent.putExtra(EXTRA_BILL_ID, bill.id)
            intent.putExtra(EXTRA_REF_KEY, refKey)
            intent.putExtra(EXTRA_TYPE, NotificationType.BILL_REMINDER.name)

            repository.upsertNotification(
                com.finovo.app.data.NotificationEntity(
                    billId = bill.id,
                    title = bill.name,
                    message = message,
                    type = NotificationType.BILL_REMINDER.name,
                    triggerAt = triggerMillis,
                    referenceKey = refKey,
                    createdAt = System.currentTimeMillis()
                )
            )
            schedule(context, id, triggerMillis, intent)
        }
    }

    suspend fun currentSymbol(repository: Repository): String =
        repository.currentSettings().currencySymbol.ifBlank { "€" }

    fun stableId(billId: Long): Int = (billId % 100_000L).toInt() + 10_000

    fun cancelBillReminder(context: Context, billId: Long) {
        cancel(context, stableId(billId), reminderIntent(context, stableId(billId)))
    }
}

/** Helper for the daily background job. */
object DailyWork {
    fun schedule(context: Context) {
        val intent = Intent(context, DailyReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            987_654,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 3)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        try {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pending
            )
        } catch (e: SecurityException) {
            am.setInexactRepeating(
                AlarmManager.RTC,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pending
            )
        }
    }
}

fun LocalDate.toMillisAt(hour: Int, minute: Int): Long =
    LocalDateTime.of(this, java.time.LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
