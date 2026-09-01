package com.example.tenthousand

import AppDatabase
import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.service.FocusNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Baza je premeštena iz MainActivity.onCreate ovde.
 *
 * Dva razloga:
 * 1. FocusService mora da piše u istu bazu (addSeconds kad sesija stane), a
 *    servis nema pristup DAO-u koji nastaje u aktivnosti.
 * 2. Stari kod je gradio novu Room instancu pri svakom kreiranju aktivnosti -
 *    dakle i pri svakoj rotaciji ekrana. Room je zamišljen kao singleton.
 *
 * [appScope] postoji zbog upisa koji moraju da prežive gašenje komponente koja
 * ih je pokrenula: kad servis kreditira sekunde pa odmah pozove stopSelf(),
 * njegov sopstveni scope se ruši i upis bi bio otkazan.
 */
class TenThousandApp : Application() {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "habits.db"
        )
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration(false)
            .build()
    }

    val habitDao: HabitDao by lazy { database.habitDao() }

    override fun onCreate() {
        super.onCreate()
        // Kanali moraju da postoje pre nego što se traži POST_NOTIFICATIONS
        // dozvola i pre prvog startForeground() poziva.
        FocusNotifications.ensureChannels(this)
    }

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE habits ADD COLUMN background TEXT DEFAULT NULL")
            }
        }
    }
}