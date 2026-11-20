package com.jagadaun.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.jagadaun.app.data.identification.MAX_ANALYSIS_PHOTOS
import com.jagadaun.app.ui.garden.GardenScreen
import com.jagadaun.app.ui.garden.PlantDetailScreen
import com.jagadaun.app.ui.garden.PlantFormScreen
import com.jagadaun.app.ui.identify.IdentifyScreen
import com.jagadaun.app.ui.identify.PreviewScreen
import com.jagadaun.app.ui.reminders.ReminderFormScreen
import com.jagadaun.app.ui.reminders.RemindersScreen
import com.jagadaun.app.ui.health.HealthScreen
import com.jagadaun.app.ui.health.HealthHistoryScreen
import com.jagadaun.app.ui.home.HealthHubScreen
import com.jagadaun.app.ui.home.CareToolsScreen
import com.jagadaun.app.ui.theme.Background
import com.jagadaun.app.ui.components.rememberPhotoActions
import com.jagadaun.app.ui.components.ScreenTopBar
import com.jagadaun.app.ui.camera.GuidedCameraScreen
import com.jagadaun.app.ui.nearby.NearbyScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Routes {
    const val KENALI = "kenali"
    const val GARDEN = "garden"
    const val REMINDERS = "reminders"
    const val HEALTH_HOME = "health_home"
    const val MORE = "more"
    const val NEARBY = "nearby"
    const val PREVIEW = "preview"
    const val CAMERA = "camera"
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
    val tabRoutes = setOf(Routes.KENALI, Routes.HEALTH_HOME, Routes.GARDEN, Routes.MORE)
    val showBottomBar = route in tabRoutes
    var draftPhotos by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val draftPhoto = draftPhotos.firstOrNull()
    var showPhotoMenu by rememberSaveable { mutableStateOf(false) }
    var candidateName by rememberSaveable { mutableStateOf<String?>(null) }
    var candidateScientificName by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val photoScope = rememberCoroutineScope()
    val replacePhotos: (List<String>) -> Unit = { paths ->
        val discarded = draftPhotos.filter { it !in paths }
        photoScope.launch(Dispatchers.IO) {
            discarded.filter(container.photos::isDraft).forEach(container.photos::discardDraft)
        }
        draftPhotos = paths
        candidateName = null
        candidateScientificName = null
    }
    val keepCoverPhoto = { replacePhotos(draftPhotos.take(1)) }
    var appendNextPhoto by remember { mutableStateOf(false) }
    val photoActions = rememberPhotoActions(container.photos) { path ->
        // Append only while adding an angle; a full set replaces, so the pick is never dropped.
        val next = if (appendNextPhoto && draftPhotos.size < MAX_ANALYSIS_PHOTOS) {
            (draftPhotos + path).distinct()
        } else {
            listOf(path)
        }
        appendNextPhoto = false
        replacePhotos(next)
        if (route != Routes.PREVIEW) navController.navigate(Routes.PREVIEW)
    }
    LaunchedEffect(photoActions.error) {
        photoActions.error?.let { snackbar.showSnackbar(it) }
    }
    val leavePreview: () -> Unit = {
        replacePhotos(emptyList())
        navController.popBackStack()
    }
    BackHandler(enabled = route == Routes.PREVIEW, onBack = leavePreview)

    if (showPhotoMenu) {
        AlertDialog(
            onDismissRequest = { showPhotoMenu = false },
            title = { Text(if (route == Routes.PREVIEW) "Tambah atau ganti foto" else "Foto tanaman") },
            text = { Text("Pilih sumber foto. Sudut sebelumnya tetap ada selama jumlah belum mencapai lima.") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoMenu = false
                    navController.navigate(Routes.CAMERA)
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
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Beranda") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.HEALTH_HOME,
                        onClick = { navController.navigateToTab(Routes.HEALTH_HOME) },
                        icon = { Icon(Icons.Filled.Healing, contentDescription = null) },
                        label = { Text("Periksa") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { showPhotoMenu = true },
                        icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                        label = { Text("Foto") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.GARDEN,
                        onClick = { navController.navigateToTab(Routes.GARDEN) },
                        icon = { Icon(Icons.Filled.Eco, contentDescription = null) },
                        label = { Text("Kebun") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.MORE,
                        onClick = { navController.navigateToTab(Routes.MORE) },
                        icon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                        label = { Text("Lainnya") },
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
                    container = container,
                    onOpenGarden = { navController.navigateToTab(Routes.GARDEN) },
                    onOpenReminders = { navController.navigate(Routes.REMINDERS) },
                    onTakePhoto = { navController.navigate(Routes.CAMERA) },
                    onPickFromGallery = photoActions.pickPhoto,
                    busy = photoActions.busy,
                    onCheckHealth = { navController.navigateToTab(Routes.HEALTH_HOME) },
                    onOpenHistory = { navController.navigate(Routes.healthHistory(null)) },
                    onOpenNearby = { navController.navigate(Routes.NEARBY) },
                )
            }
            composable(Routes.HEALTH_HOME) {
                HealthHubScreen(
                    container = container,
                    onCheckHealth = { plantId -> navController.navigate(Routes.health(plantId)) },
                    onOpenHistory = { navController.navigate(Routes.healthHistory(null)) },
                )
            }
            composable(Routes.MORE) {
                CareToolsScreen(
                    onOpenReminders = { navController.navigate(Routes.REMINDERS) },
                    onOpenHistory = { navController.navigate(Routes.healthHistory(null)) },
                    onCheckHealth = { navController.navigateToTab(Routes.HEALTH_HOME) },
                    onOpenNearby = { navController.navigate(Routes.NEARBY) },
                )
            }
            composable(Routes.NEARBY) {
                NearbyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GARDEN) {
                GardenScreen(
                    container = container,
                    onOpenDetail = { id -> navController.navigate(Routes.plantDetail(id)) },
                    onAddManual = { navController.navigate(Routes.plantForm(null)) },
                    onIdentify = { navController.navigateToTab(Routes.KENALI) },
                )
            }
            composable(
                Routes.REMINDERS,
                deepLinks = listOf(navDeepLink { uriPattern = "jagadaun://reminders" }),
            ) {
                Column(Modifier.fillMaxSize()) {
                    ScreenTopBar(title = "Perawatan", onBack = { navController.popBackStack() })
                    RemindersScreen(
                        container = container,
                        onAdd = { navController.navigate(Routes.reminderForm(null, null)) },
                        onEdit = { id -> navController.navigate(Routes.reminderForm(id, null)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            composable(Routes.CAMERA) {
                GuidedCameraScreen(
                    photos = container.photos,
                    onBack = { navController.popBackStack() },
                    onCaptured = { paths ->
                        val caller = navController.previousBackStackEntry
                        if (caller?.destination?.route == Routes.HEALTH || caller?.destination?.route == Routes.PLANT_FORM) {
                            caller?.savedStateHandle?.set("cameraPhotos", paths)
                            navController.popBackStack()
                        } else {
                            val next = if (appendNextPhoto && paths.size == 1 &&
                                draftPhotos.size < MAX_ANALYSIS_PHOTOS) {
                                (draftPhotos + paths).distinct()
                            } else {
                                paths
                            }
                            appendNextPhoto = false
                            replacePhotos(next)
                            navController.popBackStack()
                            if (navController.currentDestination?.route != Routes.PREVIEW) {
                                navController.navigate(Routes.PREVIEW)
                            }
                        }
                    },
                    allowRotation = navController.previousBackStackEntry?.destination?.route != Routes.PLANT_FORM,
                )
            }
            composable(Routes.PREVIEW) {
                PreviewScreen(
                    onPhotosSaved = { draftPhotos = it },
                    photoRefs = draftPhotos,
                    busy = photoActions.busy,
                    onBack = leavePreview,
                    onReplacePhoto = { appendNextPhoto = false; showPhotoMenu = true },
                    onAddAngle = { if (draftPhotos.size < MAX_ANALYSIS_PHOTOS) { appendNextPhoto = true; showPhotoMenu = true } },
                    onSaveManual = {
                        keepCoverPhoto()
                        candidateName = null
                        candidateScientificName = null
                        navController.navigate(Routes.plantForm(null)) {
                            popUpTo(Routes.PREVIEW) { inclusive = true }
                        }
                    },
                    onCandidateSelected = { candidate ->
                        keepCoverPhoto()
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
                val cameraPhotos by entry.savedStateHandle.getStateFlow("cameraPhotos", emptyList<String>())
                    .collectAsStateWithLifecycle()
                HealthScreen(
                    container = container,
                    plantId = entry.arguments?.getString("plantId")?.takeIf { it != "none" },
                    cameraPhotos = cameraPhotos,
                    onTakePhoto = { navController.navigate(Routes.CAMERA) },
                    onCameraPhotosConsumed = { entry.savedStateHandle["cameraPhotos"] = emptyList<String>() },
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
                val cameraPhotos by entry.savedStateHandle.getStateFlow("cameraPhotos", emptyList<String>())
                    .collectAsStateWithLifecycle()
                PlantFormScreen(
                    container = container,
                    plantId = id,
                    initialPhotoRef = if (id == null) draftPhoto else null,
                    initialName = candidateName.takeIf { id == null },
                    initialScientificName = candidateScientificName.takeIf { id == null },
                    cameraPhotos = cameraPhotos,
                    onTakePhoto = { navController.navigate(Routes.CAMERA) },
                    onCameraPhotosConsumed = { entry.savedStateHandle["cameraPhotos"] = emptyList<String>() },
                    onDone = {
                        draftPhotos = emptyList()
                        candidateName = null
                        candidateScientificName = null
                        navController.navigateToTab(Routes.GARDEN)
                    },
                    onBack = {
                        draftPhotos = emptyList()
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
