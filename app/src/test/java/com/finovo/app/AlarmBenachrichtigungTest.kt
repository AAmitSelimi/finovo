package com.finovo.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.notifications.Notify
import com.finovo.app.util.Alarm
import com.finovo.app.util.Alarme
import com.finovo.app.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Modul 15: die Finanz-Alarme muessen auch dann melden, wenn gerade kein
 * Bildschirm offen ist - und zwar maximal einmal pro Alarm und Tag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class AlarmBenachrichtigungTest {

    private fun app(): FinovoApp = ApplicationProvider.getApplicationContext()

    @Before
    fun leer() {
        runBlocking(Dispatchers.IO) { app().repository.deleteAllData() }
    }

    @Test
    fun niedrigerKontostandWirdEinmalTaeglichGemeldet() {
        val app = app()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notify.createChannels(app)

        runBlocking(Dispatchers.IO) {
            app.repository.addExpense(
                ExpenseEntity(
                    id = 0,
                    amountCents = 50_00L,
                    categoryId = 1,
                    date = Dates.today().toEpochDay(),
                    note = "Alarmtest"
                )
            )
        }

        // Ohne Einnahmen liegt das verfuegbare Geld unter der Schwelle von 100 €
        val erwarteterTitel = Alarme.titel(Alarm.LOW_BALANCE)
        runBlocking { Notify.alarmeMelden(app, app.repository) }

        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val erste = shadowOf(manager).allNotifications
        assertTrue(
            "Keine Benachrichtigung fuer '$erwarteterTitel' - vorhanden: " +
                erste.map { it.extras.getString(Notification.EXTRA_TITLE) },
            erste.any { it.extras.getString(Notification.EXTRA_TITLE) == erwarteterTitel }
        )

        // Vormerken: derselbe Alarm soll heute nicht erneut stoeren
        val protokoll = app.getSharedPreferences("finovo_alarme", Context.MODE_PRIVATE)
        assertEquals(
            Dates.today().toEpochDay(),
            protokoll.getLong(Alarm.LOW_BALANCE.key, -1L)
        )

        // Zweiter Lauf am selben Tag: keine neue Benachrichtigung
        runBlocking { Notify.alarmeMelden(app, app.repository) }
        assertEquals(
            "Beim zweiten Lauf wurde erneut gemeldet",
            erste.size,
            shadowOf(manager).allNotifications.size
        )
    }

    @Test
    fun ohneAusloeserBleibtEsStill() {
        val app = app()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notify.createChannels(app)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()

        // Alle Alarme aus, dann darf nichts gemeldet werden
        runBlocking(Dispatchers.IO) {
            val zustand = Alarme.Zustand(
                aktiv = Alarm.values().associateWith { false },
                schwellen = Alarm.values().associateWith { it.standardWert }
            )
            app.repository.updateSettings { it.copy(alertSettings = Alarme.zuText(zustand)) }
        }
        runBlocking { Notify.alarmeMelden(app, app.repository) }
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
