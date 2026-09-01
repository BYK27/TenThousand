package com.example.tenthousand.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.tenthousand.MainActivity
import com.example.tenthousand.R
import com.example.tenthousand.util.focus.FocusMode
import com.example.tenthousand.util.focus.FocusSession
import com.example.tenthousand.util.focus.formatFocusClock

/**
 * Sve što se tiče izgleda notifikacije. Servis odlučuje ŠTA je stanje, ovaj
 * objekat samo zna kako se to stanje crta.
 *
 * Ključna odluka: vreme se prikazuje kroz [android.widget.Chronometer] dok
 * sesija teče. Chronometer se sam osvežava svake sekunde u system_ui procesu,
 * pa servis NE mora da radi tick petlju niti da poziva notify() svake sekunde -
 * notifikacija se update-uje samo kad se stanje stvarno promeni (start, pauza,
 * nastavak, kraj). To je najveća ušteda baterije u celoj izmeni.
 *
 * Kad je sesija pauzirana, Chronometer se sakriva i prikazuje se statičan
 * TextView sa zamrznutim vremenom - zaustavljen Chronometer u RemoteViews-u
 * ume da ne osveži tekst, pa se na njega ne oslanjam.
 */
object FocusNotifications {

    const val SESSION_CHANNEL_ID = "focus_session"
    const val COMPLETE_CHANNEL_ID = "focus_complete"

    const val SESSION_NOTIFICATION_ID = 1001
    const val COMPLETE_NOTIFICATION_ID = 1002

    private const val REQ_CONTENT = 100
    private const val REQ_TOGGLE = 101
    private const val REQ_STOP = 102

    private const val SCRIM = 0xB3000000.toInt() // 70% crno preko pozadine

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val session = NotificationChannel(
            SESSION_CHANNEL_ID,
            "Focus session",
            NotificationManager.IMPORTANCE_LOW // bez zvuka, bez heads-up-a
        ).apply {
            description = "Prikazuje aktivan tajmer ili štopericu"
            setShowBadge(false)
            enableVibration(false)
        }

        val complete = NotificationChannel(
            COMPLETE_CHANNEL_ID,
            "Focus complete",
            NotificationManager.IMPORTANCE_HIGH // zvuk + vibracija ide preko kanala
        ).apply {
            description = "Javlja kad tajmer istekne"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
        }

        manager.createNotificationChannel(session)
        manager.createNotificationChannel(complete)
    }

    // ---------------------------------------------------------------------
    // Session notifikacija
    // ---------------------------------------------------------------------

    fun buildSessionNotification(
        context: Context,
        session: FocusSession?,
        background: Bitmap?
    ): Notification {
        val builder = NotificationCompat.Builder(context, SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        if (session == null) {
            // Prelazno stanje: servis mora da pozove startForeground() u roku od
            // par sekundi od startForegroundService(), a podatke o navici tek
            // čita iz baze. Ovo je placeholder koji se odmah zameni.
            return builder
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("Focus")
                .build()
        }

        builder.setColor(session.habitColor)
            .setContentIntent(contentIntent(context, session.habitId))
            // Fallback tekst za površine koje ne prikazuju custom view
            // (Wear, Android Auto, neki launcher-i).
            .setContentTitle(session.habitName)
            .setContentText(fallbackText(session))
            .setCustomContentView(buildViews(context, session, background, R.layout.notification_focus_collapsed))
            .setCustomBigContentView(buildViews(context, session, background, R.layout.notification_focus_expanded))

        return builder.build()
    }

    private fun fallbackText(session: FocusSession): String {
        val now = System.currentTimeMillis()
        val seconds = if (session.mode == FocusMode.TIMER) {
            session.remainingSecondsAt(now)
        } else {
            session.elapsedSecondsAt(now)
        }
        val state = if (session.isRunning) "" else " (paused)"
        return formatFocusClock(seconds) + state
    }

    private fun buildViews(
        context: Context,
        session: FocusSession,
        background: Bitmap?,
        layoutRes: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)
        val now = System.currentTimeMillis()
        val expanded = layoutRes == R.layout.notification_focus_expanded

        // --- pozadina ---------------------------------------------------
        // Boja navike je uvek ispod; snapshot shadera ide preko nje ako postoji.
        // Kad navika nema pozadinu (ili render nije uspeo), ostaje čista boja.
        views.setInt(R.id.notif_bg, "setBackgroundColor", session.habitColor)
        if (background != null) {
            views.setImageViewBitmap(R.id.notif_bg, background)
        }
        views.setInt(R.id.notif_content, "setBackgroundColor", SCRIM)

        // --- tekst ------------------------------------------------------
        views.setTextViewText(R.id.notif_habit, session.habitName)

        // --- vreme ------------------------------------------------------
        val seconds = if (session.mode == FocusMode.TIMER) {
            session.remainingSecondsAt(now)
        } else {
            session.elapsedSecondsAt(now)
        }

        if (session.isRunning) {
            views.setViewVisibility(R.id.notif_chrono, View.VISIBLE)
            views.setViewVisibility(R.id.notif_time, View.GONE)

            // Chronometer radi na elapsedRealtime() osi, pa se zidno vreme
            // sesije prevodi u tu osu preko trenutne razlike.
            val base = if (session.mode == FocusMode.TIMER) {
                SystemClock.elapsedRealtime() + session.remainingMillisAt(now)
            } else {
                SystemClock.elapsedRealtime() - session.elapsedMillisAt(now)
            }
            views.setChronometerCountDown(R.id.notif_chrono, session.mode == FocusMode.TIMER)
            views.setChronometer(R.id.notif_chrono, base, null, true)
        } else {
            views.setViewVisibility(R.id.notif_chrono, View.GONE)
            views.setViewVisibility(R.id.notif_time, View.VISIBLE)
            views.setTextViewText(R.id.notif_time, formatFocusClock(seconds))
        }

        // --- dugmići (samo u proširenom prikazu) ------------------------
        if (expanded) {
            views.setTextViewText(
                R.id.notif_mode,
                if (session.mode == FocusMode.TIMER) "Timer" else "Stopwatch"
            )
            views.setTextViewText(
                R.id.notif_toggle,
                if (session.isRunning) "PAUSE" else "RESUME"
            )
            views.setOnClickPendingIntent(
                R.id.notif_toggle,
                serviceIntent(
                    context,
                    if (session.isRunning) FocusService.ACTION_PAUSE else FocusService.ACTION_RESUME,
                    REQ_TOGGLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.notif_stop,
                serviceIntent(context, FocusService.ACTION_STOP_AND_SAVE, REQ_STOP)
            )
        }

        return views
    }

    // ---------------------------------------------------------------------
    // Notifikacija kad tajmer istekne
    // ---------------------------------------------------------------------

    fun buildCompletionNotification(context: Context, session: FocusSession): Notification =
        NotificationCompat.Builder(context, COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle("${session.habitName} - gotovo")
            .setContentText("Upisano ${formatFocusClock(session.totalSeconds)} fokusa")
            .setColor(session.habitColor)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent(context, session.habitId))
            .build()

    // ---------------------------------------------------------------------
    // PendingIntent-i
    // ---------------------------------------------------------------------

    private fun contentIntent(context: Context, habitId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_HABIT_ID, habitId)
        }
        return PendingIntent.getActivity(
            context,
            REQ_CONTENT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, FocusService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}