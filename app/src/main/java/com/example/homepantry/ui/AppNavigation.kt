package com.example.homepantry.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.homepantry.ui.screens.InventoryScreen
import com.example.homepantry.ui.screens.ItemFormScreen
import com.example.homepantry.ui.screens.PinEntryScreen
import com.example.homepantry.ui.screens.SettingsScreen
import com.example.homepantry.ui.screens.AuthCheckScreen

object AppRoutes {
    const val AUTH_CHECK = "auth_check"
    const val PIN_ENTRY = "pin_entry"
    const val INVENTORY_LIST = "inventory_list"
    const val SETTINGS = "settings"
    const val ADD_ITEM = "add_item"
    const val EDIT_ITEM = "edit_item/{itemId}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // CRITICAL FIX: Wrap NavHost in a Box with MaterialTheme background
    // This prevents white flashes during navigation transitions
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = AppRoutes.AUTH_CHECK,
            // IMPROVED TRANSITIONS: Smooth horizontal slide + fade for modern feel
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            }
        ) {
            composable(AppRoutes.AUTH_CHECK) {
                AuthCheckScreen(
                    onNavigateToLogin = {
                        navController.navigate(AppRoutes.PIN_ENTRY) {
                            popUpTo(AppRoutes.AUTH_CHECK) { inclusive = true }
                        }
                    },
                    onNavigateToInventory = {
                        navController.navigate(AppRoutes.INVENTORY_LIST) {
                            popUpTo(AppRoutes.AUTH_CHECK) { inclusive = true }
                        }
                    }
                )
            }

            composable(AppRoutes.PIN_ENTRY) {
                PinEntryScreen(onLoginSuccess = {
                    navController.navigate(AppRoutes.INVENTORY_LIST) {
                        popUpTo(AppRoutes.PIN_ENTRY) { inclusive = true }
                    }
                })
            }

            composable(AppRoutes.INVENTORY_LIST) {
                InventoryScreen(
                    navController = navController, // ADDED: Pass navController for ViewModel scoping
                    onAddItem = { navController.navigate(AppRoutes.ADD_ITEM) },
                    onEditItem = { itemId -> navController.navigate("edit_item/$itemId") },
                    onGoToSettings = { navController.navigate(AppRoutes.SETTINGS) }
                )
            }

            composable(AppRoutes.SETTINGS) {
                SettingsScreen(navController = navController)
            }

            composable(AppRoutes.ADD_ITEM) {
                ItemFormScreen(
                    navController = navController, // ADDED: Pass navController for ViewModel scoping
                    itemId = null,
                    onSave = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(
                route = AppRoutes.EDIT_ITEM,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")?.toLongOrNull()
                ItemFormScreen(
                    navController = navController, // ADDED: Pass navController for ViewModel scoping
                    itemId = itemId,
                    onSave = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
        }
    }
}