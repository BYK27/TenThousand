package com.example.tenthousand.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.tenthousand.TenThousandApp
import com.example.tenthousand.util.focus.FocusMode
import com.example.tenthousand.util.focus.FocusSession
import com.example.tenthousand.util.focus.FocusSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground servis koji drži jednu aktivnu focus sesiju.
 *
 * Zašto servis, a ne ViewModel: ViewModel živi koliko i ekran, a Android proces
 * bez foreground komponente sistem sme da ubije čim ode u pozadinu. Otkad je
 * ovaj servis izvor istine, sesija preživljava zatvaranje ekrana, a notifikacija
 * je ta koja drži proces živim.
 *
 * Servis NAMERNO nema tick petlju:
 * - prikaz vremena u notifikaciji radi Chronometer, sam za sebe;
 * - kraj tajmera javlja AlarmManager egzaktnim alarmom, koji budi CPU i u doze
 *   režimu (coroutine delay to ne radi - kad se ekran ugasi i CPU zaspi, delay
 *   okine tek kad se uređaj sledeći put probudi, pa bi ti tajmer kasnio);
 * - proteklo vreme se uvek RAČUNA iz zidnog sata, ne akumulira otkucajima.
 *
 * Jedina periodična petlja u app-u ostaje ona u HabitDetailViewModel-u, koja
 * radi samo dok je app u prvom planu i služi za brainrot coin drop-ove.
 */
class FocusService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: FocusSessionStore

    private val app: TenThousandApp get() = application as TenThousandApp
    private val alarmManager: AlarmManager by lazy { getSystemService(AlarmManager::class.java) }

    /** Keš snapshot-a pozadine u memoriji, da se PNG ne čita sa diska pri svakom update-u. */
    private var backgroundBitmap: Bitmap? = null
    private var backgroundBitmapName: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = FocusSessionStore.getInstance(this)
        FocusNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground() mora da se pozove pre bilo čega drugog i pre nego
        // što istekne prozor koji sistem daje posle startForegroundService(),
        // inače dobijaš ForegroundServiceDidNotStartInTimeException. Zato prvo
        // ide notifikacija sa trenutno poznatim stanjem, pa tek onda logika.
        promoteToForeground(store.session.value)

        when (intent?.action) {
            ACTION_START_TIMER -> startSession(
                habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L),
                mode = FocusMode.TIMER,
                totalSeconds = intent.getLongExtra(EXTRA_TOTAL_SECONDS, 25 * 60L)
            )

            ACTION_START_STOPWATCH -> startSession(
                habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L),
                mode = FocusMode.STOPWATCH,
                totalSeconds = 0L
            )

            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            ACTION_STOP_AND_SAVE -> stopAndSave()
            ACTION_TIMER_ELAPSED -> completeTimer()

            // intent == null znači da nas je sistem restartovao posle ubijanja
            // procesa (START_STICKY). Stanje čitamo sa diska.
            else -> restoreOrStop()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Komande
    // ---------------------------------------------------------------------

    private fun startSession(habitId: Long, mode: FocusMode, totalSeconds: Long) {
        if (habitId <= 0L) {
            stopEverything()
            return
        }

        // Pravilo "jedna sesija u celom app-u": sve što je do sada teklo se
        // kreditira i zatvara pre nego što nova sesija krene. Zato start
        // tajmera gasi štopericu i obrnuto, i to bez gubitka minuta.
        creditAndClear(store.session.value)

        serviceScope.launch {
            val habit = withContext(Dispatchers.IO) { app.habitDao.observeByIdOnce(habitId) }
            if (habit == null) {
                stopEverything()
                return@launch
            }

            val session = FocusSession(
                habitId = habit.id,
                habitName = habit.name,
                habitColor = habit.color,
                backgroundName = habit.background,
                mode = mode,
                totalSeconds = if (mode == FocusMode.TIMER) totalSeconds else 0L,
                accumulatedMillis = 0L,
                runningSinceMillis = System.currentTimeMillis()
            )

            store.set(session)
            scheduleFinishAlarm(session)
            refreshNotification(session)
        }
    }

    private fun pauseSession() {
        val current = store.session.value ?: return stopEverything()
        if (!current.isRunning) return

        val paused = current.pausedAt(System.currentTimeMillis())
        store.set(paused)
        cancelFinishAlarm()
        refreshNotification(paused)
    }

    private fun resumeSession() {
        val current = store.session.value ?: return stopEverything()
        if (current.isRunning) return

        // Tajmer koji je već potrošen ne može da se nastavi - tretiramo ga
        // kao završen, da korisnik ne ostane sa notifikacijom na 00:00.
        if (current.mode == FocusMode.TIMER &&
            current.accumulatedMillis >= current.totalSeconds * 1000L
        ) {
            completeTimer()
            return
        }

        val resumed = current.resumedAt(System.currentTimeMillis())
        store.set(resumed)
        scheduleFinishAlarm(resumed)
        refreshNotification(resumed)
    }

    private fun stopAndSave() {
        creditAndClear(store.session.value)
        stopEverything()
    }

    private fun completeTimer() {
        val current = store.session.value
        if (current == null || current.mode != FocusMode.TIMER) {
            stopEverything()
            return
        }

        cancelFinishAlarm()
        store.set(null)
        // Tajmer koji je istekao kreditira PUNO trajanje, ne izmereno proteklo -
        // izbegava da zaokruživanje milisekundi otme sekundu na kraju.
        creditSeconds(current.habitId, current.totalSeconds)

        runCatching {
            NotificationManagerCompat.from(this).notify(
                FocusNotifications.COMPLETE_NOTIFICATION_ID,
                FocusNotifications.buildCompletionNotification(this, current)
            )
        }

        stopEverything()
    }

    private fun restoreOrStop() {
        val current = store.session.value
        if (current == null) {
            stopEverything()
            return
        }

        // Ako je tajmer istekao dok je proces bio mrtav, zatvori ga odmah.
        if (current.mode == FocusMode.TIMER &&
            current.isRunning &&
            current.remainingMillisAt(System.currentTimeMillis()) <= 0L
        ) {
            completeTimer()
            return
        }

        scheduleFinishAlarm(current)
        refreshNotification(current)
    }

    // ---------------------------------------------------------------------
    // Baza
    // ---------------------------------------------------------------------

    private fun creditAndClear(session: FocusSession?) {
        if (session == null) return
        cancelFinishAlarm()
        val seconds = session.elapsedSecondsAt(System.currentTimeMillis())
        store.set(null)
        creditSeconds(session.habitId, seconds)
    }

    /**
     * Upis ide kroz application scope, ne kroz serviceScope: stopSelf() odmah
     * posle ovoga ruši servis i njegov scope, pa bi upis u bazu bio otkazan
     * na pola. Application scope preživljava gašenje servisa.
     */
    private fun creditSeconds(habitId: Long, seconds: Long) {
        if (seconds <= 0L) return
        app.appScope.launch { app.habitDao.addSeconds(habitId, seconds) }
    }

    // ---------------------------------------------------------------------
    // Alarm za kraj tajmera
    // ---------------------------------------------------------------------

    private fun scheduleFinishAlarm(session: FocusSession) {
        cancelFinishAlarm()
        val finishAt = session.finishAtMillis() ?: return
        // setExactAndAllowWhileIdle probija doze; dozvolu daje USE_EXACT_ALARM
        // iz manifesta, koja se za timer/alarm app-ove dodeljuje automatski.
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, finishAt, finishPendingIntent())
    }

    private fun cancelFinishAlarm() {
        alarmManager.cancel(finishPendingIntent())
    }

    private fun finishPendingIntent(): PendingIntent =
        PendingIntent.getForegroundService(
            this,
            REQ_FINISH_ALARM,
            Intent(this, FocusService::class.java).setAction(ACTION_TIMER_ELAPSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ---------------------------------------------------------------------
    // Notifikacija
    // ---------------------------------------------------------------------

    private fun promoteToForeground(session: FocusSession?) {
        startForeground(
            FocusNotifications.SESSION_NOTIFICATION_ID,
            FocusNotifications.buildSessionNotification(this, session, bitmapFor(session)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun refreshNotification(session: FocusSession) {
        val wanted = session.backgroundName
        if (wanted != null && wanted != backgroundBitmapName) {
            // Render je GPU posao i čitanje sa diska - ne na main thread-u.
            // Notifikacija se u međuvremenu prikazuje sa bojom navike, pa se
            // osveži sa slikom čim stigne.
            serviceScope.launch {
                val bitmap = withContext(Dispatchers.Default) {
                    ShaderSnapshotRenderer.snapshot(this@FocusService, wanted)
                }
                backgroundBitmap = bitmap
                backgroundBitmapName = wanted
                store.session.value?.let { notifySession(it) }
            }
        } else if (wanted == null) {
            backgroundBitmap = null
            backgroundBitmapName = null
        }

        notifySession(session)
    }

    private fun notifySession(session: FocusSession) {
        runCatching {
            NotificationManagerCompat.from(this).notify(
                FocusNotifications.SESSION_NOTIFICATION_ID,
                FocusNotifications.buildSessionNotification(this, session, bitmapFor(session))
            )
        }
    }

    private fun bitmapFor(session: FocusSession?): Bitmap? =
        if (session?.backgroundName != null && session.backgroundName == backgroundBitmapName) {
            backgroundBitmap
        } else {
            null
        }

    private fun stopEverything() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------------------------------------------------------------
    // API za ostatak app-a
    // ---------------------------------------------------------------------

    companion object {
        const val ACTION_START_TIMER = "com.example.tenthousand.action.START_TIMER"
        const val ACTION_START_STOPWATCH = "com.example.tenthousand.action.START_STOPWATCH"
        const val ACTION_PAUSE = "com.example.tenthousand.action.PAUSE"
        const val ACTION_RESUME = "com.example.tenthousand.action.RESUME"
        const val ACTION_STOP_AND_SAVE = "com.example.tenthousand.action.STOP_AND_SAVE"
        const val ACTION_TIMER_ELAPSED = "com.example.tenthousand.action.TIMER_ELAPSED"

        const val EXTRA_HABIT_ID = "habitId"
        const val EXTRA_TOTAL_SECONDS = "totalSeconds"

        private const val REQ_FINISH_ALARM = 900

        fun startTimer(context: Context, habitId: Long, totalSeconds: Long) {
            send(context, Intent(context, FocusService::class.java).apply {
                action = ACTION_START_TIMER
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
            })
        }

        fun startStopwatch(context: Context, habitId: Long) {
            send(context, Intent(context, FocusService::class.java).apply {
                action = ACTION_START_STOPWATCH
                putExtra(EXTRA_HABIT_ID, habitId)
            })
        }

        fun pause(context: Context) = send(context, action(context, ACTION_PAUSE))
        fun resume(context: Context) = send(context, action(context, ACTION_RESUME))
        fun stopAndSave(context: Context) = send(context, action(context, ACTION_STOP_AND_SAVE))

        private fun action(context: Context, action: String) =
            Intent(context, FocusService::class.java).setAction(action)

        private fun send(context: Context, intent: Intent) {
            ContextCompat.startForegroundService(context, intent)
        }
    }
}