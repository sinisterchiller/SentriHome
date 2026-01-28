package com.example.esp32sensormanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.esp32sensormanager.ui.screens.AddSensorScreen
import com.example.esp32sensormanager.ui.screens.HomeScreen
import com.example.esp32sensormanager.ui.screens.PairMasterScreen
import com.example.esp32sensormanager.ui.screens.SensorDetailScreen
import com.example.esp32sensormanager.ui.vm.AppViewModel

@Composable
fun AppNavGraph(navController: NavHostController) {
    val vm: AppViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.Home
    ) {
        composable(AppRoutes.Home) {
            HomeScreen(
                vm = vm,
                onGoPairMaster = { navController.navigate(AppRoutes.PairMaster) },
                onGoAddSensor = { navController.navigate(AppRoutes.AddSensor) },
                onOpenSensor = { id -> navController.navigate(AppRoutes.sensorDetail(id)) }
            )
        }

        composable(AppRoutes.PairMaster) {
            PairMasterScreen(
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.AddSensor) {
            AddSensorScreen(
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoutes.SensorDetail,
            arguments = listOf(navArgument("sensorId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sensorId = backStackEntry.arguments?.getString("sensorId") ?: ""
            SensorDetailScreen(
                vm = vm,
                sensorId = sensorId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
