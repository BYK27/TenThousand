package com.example.tenthousand

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.ui.screens.habit_detail.HabitDetailScreen
import com.example.tenthousand.ui.screens.habit_list.HabitListScreen
import com.example.tenthousand.ui.screens.shop.ShopScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE habits ADD COLUMN background TEXT DEFAULT NULL")
            }
        }

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "habits.db"
        )
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration(false)
            .build()

        val dao = db.habitDao()

        setContent {
            AppNavHost(dao)
        }
    }
}

@Composable
fun AppNavHost(dao: HabitDao) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            HabitListScreen(
                onOpenHabit = { id -> nav.navigate("habit/$id") },
                onOpenShop = { nav.navigate("shop") },
                dao = dao
            )
        }

        composable(
            route = "habit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments!!.getLong("id")
            HabitDetailScreen(
                habitId = id,
                dao = dao,
                onBack = { nav.popBackStack() }
            )
        }

        composable("shop") {
            ShopScreen(onBack = { nav.popBackStack() })
        }
    }
}