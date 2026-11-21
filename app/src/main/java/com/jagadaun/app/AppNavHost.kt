package com.jagadaun.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.SoftAccent
import com.jagadaun.app.ui.theme.Divider
import com.jagadaun.app.ui.theme.TextSecondary
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
    // Hanya empat tujuan utama yang memakai bar. Pengingat dan halaman lain adalah rute
    // sekunder: punya tombol Kembali sendiri dan tidak menampilkan bar, jadi tidak ada
    // dua jalur keluar yang bersaing di satu layar.
    val tabRoutes = remember { setOf(Routes.KENALI, Routes.HEALTH_HOME, Routes.GARDEN, Routes.MORE) }
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
        if (route != Routes.PREVIEW) navController.navigateOnce(Routes.PREVIEW)
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
                    navController.navigateOnce(Routes.CAMERA)
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
                GardenNavigation(
                    selectedRoute = route,
                    onSelect = navController::navigateToTab,
                    onCamera = { navController.navigateOnce(Routes.CAMERA) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.KENALI,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(120)) },
            exitTransition = { fadeOut(tween(90)) },
            popEnterTransition = { fadeIn(tween(120)) },
            popExitTransition = { fadeOut(tween(90)) },
        ) {
            composable(Routes.KENALI) {
                IdentifyScreen(
                    container = container,
                    onOpenGarden = { navController.navigateToTab(Routes.GARDEN) },
                    onOpenReminders = { navController.navigateToTab(Routes.REMINDERS) },
                    onTakePhoto = { navController.navigateOnce(Routes.CAMERA) },
                    onPickFromGallery = photoActions.pickPhoto,
                    busy = photoActions.busy,
                    onCheckHealth = { navController.navigateToTab(Routes.HEALTH_HOME) },
                    onOpenHistory = { navController.navigateOnce(Routes.healthHistory(null)) },
                    onOpenTools = { navController.navigateToTab(Routes.MORE) },
                    onOpenNearby = { navController.navigateOnce(Routes.NEARBY) },
                    onOpenPlant = { id -> navController.navigateOnce(Routes.plantDetail(id)) },
                )
            }
            composable(Routes.HEALTH_HOME) {
                // Tab utama: judul layar sendiri, tanpa tombol Kembali dan tanpa AppBar ganda.
                HealthHubScreen(
                    container = container,
                    onCheckHealth = { plantId -> navController.navigateOnce(Routes.health(plantId)) },
                    onOpenHistory = { navController.navigateOnce(Routes.healthHistory(null)) },
                )
            }
            composable(Routes.MORE) {
                CareToolsScreen(
                    onOpenReminders = { navController.navigateOnce(Routes.REMINDERS) },
                    onOpenHistory = { navController.navigateOnce(Routes.healthHistory(null)) },
                    onCheckHealth = { navController.navigateToTab(Routes.HEALTH_HOME) },
                    onOpenNearby = { navController.navigateOnce(Routes.NEARBY) },
                )
            }
            composable(Routes.NEARBY) {
                NearbyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GARDEN) {
                GardenScreen(
                    container = container,
                    onOpenDetail = { id -> navController.navigateOnce(Routes.plantDetail(id)) },
                    onAddManual = { navController.navigateOnce(Routes.plantForm(null)) },
                    onIdentify = { navController.navigateToTab(Routes.KENALI) },
                )
            }
            composable(
                Routes.REMINDERS,
                deepLinks = listOf(navDeepLink { uriPattern = "jagadaun://reminders" }),
            ) {
                Column(Modifier.fillMaxSize()) {
                    ScreenTopBar(title = "Pengingat", onBack = { navController.popBackStack() })
                    RemindersScreen(
                        container = container,
                        onAdd = { navController.navigateOnce(Routes.reminderForm(null, null)) },
                        onEdit = { id -> navController.navigateOnce(Routes.reminderForm(id, null)) },
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
                                navController.navigateOnce(Routes.PREVIEW)
                            }
                        }
                    },
                    allowRotation = navController.previousBackStackEntry?.destination?.route != Routes.PLANT_FORM,
                    // Galeri dari kamera kembali ke layar hasil, bukan menumpuk kamera di belakangnya.
                    onPickFromGallery = { navController.popBackStack(); photoActions.pickPhoto() },
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
                            launchSingleTop = true
                            popUpTo(Routes.PREVIEW) { inclusive = true }
                        }
                    },
                    onCandidateSelected = { candidate ->
                        keepCoverPhoto()
                        candidateName = candidate.displayName
                        candidateScientificName = candidate.scientificName
                        navController.navigate(Routes.plantForm(null)) {
                            launchSingleTop = true
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
                    onEdit = { navController.navigateOnce(Routes.plantForm(id)) },
                    onAddReminder = { navController.navigateOnce(Routes.reminderForm(null, id)) },
                    onEditReminder = { reminderId -> navController.navigateOnce(Routes.reminderForm(reminderId, id)) },
                    onCheckHealth = { navController.navigateOnce(Routes.health(id)) },
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
                    onTakePhoto = { navController.navigateOnce(Routes.CAMERA) },
                    onCameraPhotosConsumed = { entry.savedStateHandle["cameraPhotos"] = emptyList<String>() },
                    onBack = { navController.popBackStack() },
                    onOpenHistory = {
                        navController.navigateOnce(Routes.healthHistory(entry.arguments?.getString("plantId")?.takeIf { it != "none" }))
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
                    onTakePhoto = { navController.navigateOnce(Routes.CAMERA) },
                    onCameraPhotosConsumed = { entry.savedStateHandle["cameraPhotos"] = emptyList<String>() },
                    onDone = {
                        draftPhotos = emptyList()
                        candidateName = null
                        candidateScientificName = null
                        if (!navController.popBackStack(Routes.GARDEN, inclusive = false)) {
                            navController.navigate(Routes.GARDEN) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        photoScope.launch { snackbar.showSnackbar("Tanaman tersimpan di Kebun Saya.") }
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
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun androidx.navigation.NavHostController.navigateOnce(route: String) {
    navigate(route) { launchSingleTop = true }
}

/**
 * Empat tujuan tab dengan tombol kamera menonjol di tengah, meniru navigasi PictureThis:
 * kamera langsung membuka kamera; galeri ditawarkan di dalam layar kamera.
 *
 * Tinggi dipatok eksplisit karena `bottomBar` diukur dengan batas tinggi tak terhingga:
 * `Box` + `weight` di sana membuat label tab melayang di tengah layar.
 */
@Composable
private fun GardenNavigation(
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    onCamera: () -> Unit,
) {
    val barHeight = 66.dp
    val cameraSize = 60.dp
    val cameraLift = 26.dp
    // Inset navigasi sistem dipasang di luar tinggi bar, supaya label tidak terhimpit.
    Box(Modifier.fillMaxWidth().navigationBarsPadding().height(barHeight + cameraLift)) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(barHeight).align(Alignment.BottomCenter),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 4.dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavigationSlot(Routes.KENALI, "Beranda", Icons.Filled.Home, selectedRoute, onSelect)
                NavigationSlot(Routes.HEALTH_HOME, "Periksa", Icons.Filled.Healing, selectedRoute, onSelect)
                // Ruang kosong tepat di bawah tombol kamera.
                Spacer(Modifier.weight(1f))
                NavigationSlot(Routes.GARDEN, "Kebun", Icons.Filled.Eco, selectedRoute, onSelect)
                NavigationSlot(Routes.MORE, "Lainnya", Icons.Filled.GridView, selectedRoute, onSelect)
            }
        }
        FloatingActionButton(
            onClick = onCamera,
            modifier = Modifier.align(Alignment.TopCenter).size(cameraSize),
            shape = CircleShape,
            containerColor = Primary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = "Ambil foto tanaman",
                modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavigationSlot(
    destination: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
) {
    val selected = selectedRoute == destination
    Column(
        modifier = Modifier.weight(1f).fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected, role = Role.Tab, onClick = { onSelect(destination) })
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null,
            tint = if (selected) Primary else TextSecondary, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) Primary else TextSecondary,
            maxLines = 1, textAlign = TextAlign.Center)
    }
}
