package com.example.tenthousand

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.ui.screens.habit_detail.HabitDetailScreen
import com.example.tenthousand.ui.screens.habit_list.HabitListScreen
import com.example.tenthousand.ui.screens.shop.ShopScreen

class MainActivity : ComponentActivity() {

    /**
     * habitId koji je stigao iz notifikacije. Drži se kao Compose state da bi
     * NavHost mogao da reaguje i kad aktivnost već postoji (onNewIntent).
     */
    private val pendingHabitId: MutableState<Long?> = mutableStateOf(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* bez akcije */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        readHabitIdFrom(intent)

        // Baza više ne nastaje ovde - vidi TenThousandApp.
        val dao = (application as TenThousandApp).habitDao

        setContent {
            AppNavHost(dao = dao, pendingHabitId = pendingHabitId)
        }
    }

    /**
     * Aktivnost je launchMode="singleTop", pa tap na notifikaciju dok je app
     * već otvoren stiže ovde umesto kroz onCreate.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readHabitIdFrom(intent)
    }

    private fun readHabitIdFrom(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_HABIT_ID, -1L) ?: -1L
        if (id > 0L) {
            pendingHabitId.value = id
            // Extra se skida da se ista navigacija ne bi ponovila pri rotaciji.
            intent?.removeExtra(EXTRA_HABIT_ID)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_HABIT_ID = "com.example.tenthousand.extra.HABIT_ID"
    }
}

@Composable
fun AppNavHost(dao: HabitDao, pendingHabitId: MutableState<Long?>) {
    val nav = rememberNavController()

    LaunchedEffect(pendingHabitId.value) {
        val id = pendingHabitId.value ?: return@LaunchedEffect
        pendingHabitId.value = null
        nav.navigate("habit/$id") { launchSingleTop = true }
    }

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