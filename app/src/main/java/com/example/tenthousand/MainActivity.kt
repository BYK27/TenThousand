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
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.ui.screens.habit_detail.HabitDetailScreen
import com.example.tenthousand.ui.screens.habit_list.HabitListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "habits.db"
        ).build()

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
                onOpenHabit = { id ->
                    nav.navigate("habit/$id")
                },
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
    }
}
