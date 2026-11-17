package com.jagadaun.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jagadaun.app.ui.garden.GardenScreen
import com.jagadaun.app.ui.garden.PlantDetailScreen
import com.jagadaun.app.ui.garden.PlantFormScreen
import com.jagadaun.app.ui.identify.IdentifyScreen
import com.jagadaun.app.ui.identify.PreviewScreen
import com.jagadaun.app.ui.reminders.ReminderFormScreen
import com.jagadaun.app.ui.reminders.RemindersScreen
import com.jagadaun.app.ui.health.HealthScreen
import com.jagadaun.app.ui.health.HealthHistoryScreen
import com.jagadaun.app.ui.theme.Background
import com.jagadaun.app.ui.components.rememberPhotoActions

object Routes {
    const val KENALI = "kenali"
    const val GARDEN = "garden"
    const val REMINDERS = "reminders"
    const val PREVIEW = "preview"
    const val PLANT_DETAIL = "plant/{id}"
    const val PLANT_FORM = "plant_form/{id}"
    const val REMINDER_FORM = "reminder_form/{id}/{plantId}"
    const val HEALTH = "health/{plantId}"
    const val HEALTH_HISTORY = "health_history/{plantId}"

    fun plantDetail(id: String): String = "plant/$id"
    fun plantForm(id: String?): String = "plant_form/${id ?: "none"}"
    fun health(plantId: String?): String = "health/${plantId ?: "none"}"
    fun healthHistory(plantId: String?): String = "health_history/${plantId ?: "none"}"
    fun reminderForm(id: String?, plantId: String?): String =
        "reminder_form/${id ?: "none"}/${plantId ?: "none"}"
}

@Composable
fun JagaDaunApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val tabRoutes = setOf(Routes.KENALI, Routes.GARDEN, Routes.REMINDERS)
    val showBottomBar = route in tabRoutes
    var draftPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var showPhotoMenu by rememberSaveable { mutableStateOf(false) }
    var candidateName by rememberSaveable { mutableStateOf<String?>(null) }
    var candidateScientificName by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val photoActions = rememberPhotoActions(container.photos) { path ->
        container.photos.discardDraft(draftPhoto)
        draftPhoto = path
        candidateName = null
        candidateScientificName = null
        if (route != Routes.PREVIEW) navController.navigate(Routes.PREVIEW)
    }
    LaunchedEffect(photoActions.error) {
        photoActions.error?.let { snackbar.showSnackbar(it) }
    }
    val leavePreview: () -> Unit = {
        container.photos.discardDraft(draftPhoto)
        draftPhoto = null
        navController.popBackStack()
    }
    BackHandler(enabled = route == Routes.PREVIEW, onBack = leavePreview)

    if (showPhotoMenu) {
        AlertDialog(
            onDismissRequest = { showPhotoMenu = false },
            title = { Text("Ganti foto") },
            text = { Text("Pilih sumber foto. Foto sebelumnya tetap ada jika dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoMenu = false
                    photoActions.takePhoto()
                }) { Text("Ambil Foto") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoMenu = false
                    photoActions.pickPhoto()
                }) { Text("Pilih dari Galeri") }
            },
        )
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = route == Routes.KENALI,
                        onClick = { navController.navigateToTab(Routes.KENALI) },
                        icon = { Icon(Icons.Filled.CameraAlt, contentDescription = "Kenali") },
                        label = { Text("Kenali") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.GARDEN,
                        onClick = { navController.navigateToTab(Routes.GARDEN) },
                        icon = { Icon(Icons.Filled.Eco, contentDescription = "Kebun Saya") },
                        label = { Text("Kebun Saya") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.REMINDERS,
                        onClick = { navController.navigateToTab(Routes.REMINDERS) },
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = "Pengingat") },
                        label = { Text("Pengingat") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.KENALI,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.KENALI) {
                IdentifyScreen(
                    onTakePhoto = photoActions.takePhoto,
                    onPickFromGallery = photoActions.pickPhoto,
                    busy = photoActions.busy,
                    onCheckHealth = { navController.navigate(Routes.health(null)) },
                    onOpenHistory = { navController.navigate(Routes.healthHistory(null)) },
                )
            }
            composable(Routes.GARDEN) {
                GardenScreen(
                    container = container,
                    onOpenDetail = { id -> navController.navigate(Routes.plantDetail(id)) },
                    onAddManual = { navController.navigate(Routes.plantForm(null)) },
                    onIdentify = { navController.navigateToTab(Routes.KENALI) },
                )
            }
            composable(Routes.REMINDERS) {
                RemindersScreen(
                    container = container,
                    onAdd = { navController.navigate(Routes.reminderForm(null, null)) },
                    onEdit = { id -> navController.navigate(Routes.reminderForm(id, null)) },
                )
            }
            composable(Routes.PREVIEW) {
                PreviewScreen(
                    photoRef = draftPhoto,
                    busy = photoActions.busy,
                    onBack = leavePreview,
                    onReplacePhoto = { showPhotoMenu = true },
                    onSaveManual = {
                        candidateName = null
                        candidateScientificName = null
                        navController.navigate(Routes.plantForm(null)) {
                            popUpTo(Routes.PREVIEW) { inclusive = true }
                        }
                    },
                    onCandidateSelected = { candidate ->
                        candidateName = candidate.displayName
                        candidateScientificName = candidate.scientificName
                        navController.navigate(Routes.plantForm(null)) {
                            popUpTo(Routes.PREVIEW) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.PLANT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                PlantDetailScreen(
                    container = container,
                    plantId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.plantForm(id)) },
                    onAddReminder = { navController.navigate(Routes.reminderForm(null, id)) },
                    onCheckHealth = { navController.navigate(Routes.health(id)) },
                )
            }
            composable(
                route = Routes.HEALTH,
                arguments = listOf(navArgument("plantId") { type = NavType.StringType }),
            ) { entry ->
                HealthScreen(
                    container = container,
                    plantId = entry.arguments?.getString("plantId")?.takeIf { it != "none" },
                    onBack = { navController.popBackStack() },
                    onOpenHistory = {
                        navController.navigate(Routes.healthHistory(entry.arguments?.getString("plantId")?.takeIf { it != "none" }))
                    },
                )
            }
            composable(
                route = Routes.HEALTH_HISTORY,
                arguments = listOf(navArgument("plantId") { type = NavType.StringType }),
            ) { entry ->
                HealthHistoryScreen(
                    container = container,
                    plantId = entry.arguments?.getString("plantId")?.takeIf { it != "none" },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PLANT_FORM,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id")?.takeIf { it != "none" }
                PlantFormScreen(
                    container = container,
                    plantId = id,
                    initialPhotoRef = if (id == null) draftPhoto else null,
                    initialName = candidateName.takeIf { id == null },
                    initialScientificName = candidateScientificName.takeIf { id == null },
                    onDone = {
                        draftPhoto = null
                        candidateName = null
                        candidateScientificName = null
                        navController.navigateToTab(Routes.GARDEN)
                    },
                    onBack = {
                        draftPhoto = null
                        candidateName = null
                        candidateScientificName = null
                        navController.popBackStack()
                    },
                )
            }
            composable(
                route = Routes.REMINDER_FORM,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("plantId") { type = NavType.StringType },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id")?.takeIf { it != "none" }
                val plantId = entry.arguments?.getString("plantId")?.takeIf { it != "none" }
                ReminderFormScreen(
                    container = container,
                    reminderId = id,
                    presetPlantId = plantId,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}
