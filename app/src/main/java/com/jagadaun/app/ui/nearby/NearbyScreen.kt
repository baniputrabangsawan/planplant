package com.jagadaun.app.ui.nearby

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jagadaun.app.domain.NearbyPhoto
import com.jagadaun.app.domain.NearbySpecies
import com.jagadaun.app.ui.components.EmptyState
import com.jagadaun.app.ui.components.PrimaryButton
import com.jagadaun.app.ui.components.ScreenTopBar
import com.jagadaun.app.ui.components.SecondaryButton
import com.jagadaun.app.ui.theme.PhotoPlaceholder
import com.jagadaun.app.ui.theme.Primary
import com.jagadaun.app.ui.theme.Surface as AppSurface
import com.jagadaun.app.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.*

private val idLocale = Locale.forLanguageTag("id-ID")
private val usLocale = Locale.US

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NearbyScreen(
    onBack: () -> Unit,
    /** UI tests pass a fake so no real location, network, or permission dialog is used. */
    viewModelFactory: ViewModelProvider.Factory? = null,
) {
    val model: NearbyViewModel = if (viewModelFactory == null) viewModel() else viewModel(factory = viewModelFactory)
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(hasCoarseLocation(context)) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var externalError by remember { mutableStateOf<String?>(null) }
    var showManualLocation by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
        // A fresh grant searches right away, with no second tap.
        if (granted) model.locate()
    }
    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
            externalError = null
        } catch (_: ActivityNotFoundException) {
            externalError = "Tidak ada aplikasi yang dapat membuka tautan ini."
        }
    }
    fun openUrl(url: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    val search: () -> Unit = {
        if (hasCoarseLocation(context)) {
            permissionGranted = true
            permissionDenied = false
            model.locate()
        } else {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // Auto-load once on first composition; the ViewModel does nothing without permission.
    LaunchedEffect(Unit) { model.locateIfPermitted() }

    val result = state.result
    val selected = state.selectedIndex
    if (showManualLocation) {
        ManualLocationSheet(
            onDismiss = { showManualLocation = false },
            onConfirm = { latitude, longitude ->
                showManualLocation = false
                model.searchAt(latitude, longitude)
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Tanaman di sekitar", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Foto tanaman dari catatan publik di sekitar Anda, dengan nama Indonesia dan nama ilmiahnya.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Lokasi perkiraan dikirim ke Worker Jaga Daun dan GBIF setelah Anda menekan tombol. Android dapat memakai lokasi beberapa menit sebelumnya. Lokasi tidak masuk koleksi atau riwayat; pencarian dapat di-cache sementara. Tidak ada pelacakan latar belakang.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    if (permissionGranted) {
                        SecondaryButton(
                            text = "Cari ulang di lokasi ini",
                            onClick = { model.locate() },
                            enabled = !state.loading,
                            icon = Icons.Filled.Refresh,
                        )
                        TextButton(
                            onClick = { showManualLocation = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Cari di lokasi lain") }
                    } else {
                        Text(
                            "Izin lokasi perkiraan diperlukan untuk mencari tanaman di sekitar. Setelah diizinkan, pencarian langsung berjalan tanpa menekan tombol lagi.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        PrimaryButton(
                            text = "Gunakan lokasi saya",
                            onClick = search,
                            enabled = !state.loading,
                            icon = Icons.Filled.LocationOn,
                        )
                    }
                    if (permissionDenied) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Izin lokasi belum diberikan. Anda dapat mencoba lagi, atau mengizinkan lokasi perkiraan lewat pengaturan aplikasi.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            SecondaryButton(text = "Coba izin lagi", onClick = search, enabled = !state.loading)
                            SecondaryButton(
                                text = "Buka pengaturan izin",
                                onClick = {
                                    open(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    Text("Radius pencarian", style = MaterialTheme.typography.titleMedium)
                    // Chips wrap instead of sharing one fixed row, so 200% fonts never overlap.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10, 25).forEach { radius ->
                            FilterChip(
                                selected = state.radiusKm == radius,
                                onClick = { model.setRadius(radius) },
                                enabled = !state.loading,
                                label = { Text("$radius km") },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    Text(
                        "Jumlah catatan adalah catatan publik historis GBIF, bukan ukuran banyaknya tanaman. Catatan ini tidak merata dan tidak menjamin tanaman masih ada. Status tanaman asli atau endemik tidak tersedia dari sumber ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            externalError?.let { error ->
                item {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            if (state.loading) {
                item { LoadingSkeleton() }
            }
            state.error?.let { error ->
                item {
                    NearbyErrorSheet(state.errorKind, error, onRetry = search, onManualLocation = {
                        externalError = null
                        showManualLocation = true
                    })
                }
            }
            if (!state.loading && state.error == null && result == null) {
                item {
                    Text(
                        "Tekan Gunakan lokasi saya untuk mulai. Anda tetap dapat kembali tanpa memberikan izin lokasi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            if (result != null && state.error == null) {
                item {
                    Text(
                        "${result.totalRecords} catatan publik dalam radius ${result.radiusKm} km",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (state.fromCache) {
                        Text(
                            "Hasil tersimpan hari ini untuk lokasi ini. Tekan Cari ulang di lokasi ini untuk data terbaru.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Catatan publik dihitung dari seluruh catatan yang cocok, bukan sampel. GBIF memuat pengamatan dan spesimen historis, bukan kondisi waktu nyata. Status tanaman asli atau endemik tidak tersedia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                if (result.species.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Eco,
                            title = "Belum ada foto tanaman pada radius ini",
                            body = "Tidak ada catatan publik dengan foto yang cocok. Catatan GBIF tidak merata, jadi ini bukan berarti tidak ada tanaman di sekitar Anda. Coba radius yang lebih luas.",
                        )
                    }
                } else {
                    if (state.latitude != null && state.longitude != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Peta skema",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Text(
                                    "Bukan peta jalan. Titik tengah adalah lokasi perkiraan Anda; setiap nomor adalah satu lokasi catatan perwakilan, bukan pusat sebaran atau semua catatan. Geser dan cubit peta, atau ketuk titik dan kartu di bawah.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                NearbyMap(
                                    species = result.species,
                                    latitude = state.latitude!!,
                                    longitude = state.longitude!!,
                                    radiusKm = result.radiusKm,
                                    selected = selected,
                                    onSelect = model::select,
                                )
                                Text(
                                    "Lingkaran dalam: ${result.radiusKm / 2.0} km · lingkaran luar: ${result.radiusKm} km. Utara di atas.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                    itemsIndexed(
                        result.species,
                        key = { _, species -> species.scientificName },
                    ) { index, species ->
                        SpeciesCard(
                            species = species,
                            expanded = selected == index,
                            latitude = state.latitude,
                            longitude = state.longitude,
                            onToggle = { model.select(if (selected == index) -1 else index) },
                            onOpenUrl = { url -> openUrl(url) },
                            onOpenMap = {
                                val point = "${species.latitude},${species.longitude}"
                                openUrl("geo:$point?q=${Uri.encode("$point (${species.scientificName})")}")
                            },
                        )
                    }
                }
            }
            item {
                TextButton(
                    onClick = { openUrl("https://www.gbif.org/") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Sumber catatan: GBIF · Buka situs") }
            }
        }
    }
}

private fun hasCoarseLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun LoadingSkeleton() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "Memuat catatan foto tanaman di sekitar"
        },
    ) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Mencari lokasi dan catatan foto tanaman…")
        repeat(3) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(PhotoPlaceholder),
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth(0.6f).height(20.dp)
                            .clip(RoundedCornerShape(6.dp)).background(PhotoPlaceholder),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth(0.4f).height(14.dp)
                            .clip(RoundedCornerShape(6.dp)).background(PhotoPlaceholder),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth(0.3f).height(14.dp)
                            .clip(RoundedCornerShape(6.dp)).background(PhotoPlaceholder),
                    )
                }
            }
        }
    }
}

/**
 * Photo surface that never collapses and never shows a broken image box: the leaf tile is drawn
 * underneath, so a missing photo (or a failed download that draws no error painter) still shows it.
 */
@Composable
private fun PhotoTile(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    Box(modifier.clip(shape).background(PhotoPlaceholder), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.Eco,
            contentDescription = if (url == null) contentDescription else null,
            tint = Primary,
            modifier = Modifier.size(iconSize),
        )
        if (url != null) {
            val context = LocalContext.current
            val request = remember(url) {
                ImageRequest.Builder(context).data(url).crossfade(200).build()
            }
            val placeholder = remember { ColorPainter(PhotoPlaceholder) }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                placeholder = placeholder,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun PhotoCredit(photo: NearbyPhoto, modifier: Modifier = Modifier) {
    Text(
        "Foto: ${photo.credit} · ${licenseLabel(photo.license)}",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
        color = TextSecondary,
        modifier = modifier,
    )
}

/** Turns an attribution URL or CC identifier into a short licence name; passes names through. */
internal fun licenseLabel(license: String): String {
    // The Worker stores Creative Commons URLs without a scheme, so trim it before matching.
    val value = license.trim().removePrefix("https://").removePrefix("http://")
    if (value.contains("publicdomain/zero")) return "CC0 1.0"
    if (value.contains("publicdomain/mark")) return "Domain publik"
    val match = Regex("/(licenses|publicdomain)/([A-Za-z0-9-]+)/([0-9.]+)").find(value)
    val type = match?.groupValues?.get(2)?.uppercase()
    val version = match?.groupValues?.get(3)
    return if (type != null && version != null) "CC $type $version" else license.trim()
}

@Composable
private fun SpeciesCard(
    species: NearbySpecies,
    expanded: Boolean,
    latitude: Double?,
    longitude: Double?,
    onToggle: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMap: () -> Unit,
) {
    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (expanded) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().semantics {
            this.selected = expanded
            stateDescription = if (expanded) "Detail terbuka" else "Ketuk untuk melihat detail"
        },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoTile(
                url = species.photo?.thumbUrl,
                contentDescription = "Foto ${species.commonName ?: species.scientificName}",
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            )
            species.photo?.let { PhotoCredit(it) }
            Text(
                species.commonName ?: species.scientificName,
                style = MaterialTheme.typography.titleLarge,
            )
            if (species.commonName != null) {
                Text(
                    species.scientificName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = TextSecondary,
                )
            }
            Text(
                "${species.observations} catatan publik",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            if (expanded) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                SpeciesDetail(
                    species = species,
                    latitude = latitude,
                    longitude = longitude,
                    onOpenUrl = onOpenUrl,
                    onOpenMap = onOpenMap,
                )
                TextButton(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Tutup detail") }
            }
        }
    }
}

@Composable
private fun SpeciesDetail(
    species: NearbySpecies,
    latitude: Double?,
    longitude: Double?,
    onOpenUrl: (String) -> Unit,
    onOpenMap: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        PhotoTile(
            url = species.photo?.url,
            contentDescription = "Foto besar ${species.commonName ?: species.scientificName}",
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
        )
        species.photo?.let { PhotoCredit(it) }
        species.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(
            "Koordinat catatan perwakilan: ${String.format(usLocale, "%.4f, %.4f", species.latitude, species.longitude)}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        if (latitude != null && longitude != null) {
            val point = remember(species, latitude, longitude) { project(latitude, longitude, species) }
            val distance = hypot(point.east, point.north)
            val bearing = (Math.toDegrees(atan2(point.east, point.north)) + 360) % 360
            Text(
                String.format(idLocale, "Dari lokasi Anda: %.1f km, arah %.0f° dari utara.", distance, bearing),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        species.wikiUrl?.let { wiki ->
            SecondaryButton(text = "Baca di Wikipedia", onClick = { onOpenUrl(wiki) })
        }
        SecondaryButton(text = "Buka di aplikasi peta", onClick = onOpenMap)
    }
}

private data class MapPoint(val east: Double, val north: Double)

// Azimuthal equidistant projection: spherical distance and initial bearing from the user.
// atan2(sin Δlongitude, cos Δlongitude) normalizes the dateline crossing.
private fun project(latitude: Double, longitude: Double, species: NearbySpecies): MapPoint {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(species.latitude)
    val rawDelta = Math.toRadians(species.longitude - longitude)
    val delta = atan2(sin(rawDelta), cos(rawDelta))
    val a = (sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) * sin(delta / 2).pow(2)).coerceIn(0.0, 1.0)
    val distance = 6371.0088 * 2 * atan2(sqrt(a), sqrt(1 - a))
    val bearing = atan2(sin(delta) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(delta))
    return MapPoint(distance * sin(bearing), distance * cos(bearing))
}

/** Marker center in canvas pixels for the current zoom and pan. */
private fun markerPosition(
    point: MapPoint,
    width: Float,
    height: Float,
    radiusKm: Int,
    scale: Float,
    panX: Float,
    panY: Float,
): Offset {
    val ring = minOf(width, height) * 0.39f
    val offset = Offset(
        (point.east / radiusKm * ring).toFloat(),
        (-point.north / radiusKm * ring).toFloat(),
    )
    return Offset(width / 2f, height / 2f) + offset * scale + Offset(panX, panY)
}

private val maxScale = 4f

@Composable
private fun NearbyMap(
    species: List<NearbySpecies>,
    latitude: Double,
    longitude: Double,
    radiusKm: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val points = remember(species, latitude, longitude) { species.map { project(latitude, longitude, it) } }
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface, fontSize = 12.sp)
    val markerStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onPrimary, fontSize = 12.sp)
    val northLabel = remember(measurer, labelStyle) {
        measurer.measure(AnnotatedString("U ↑"), style = labelStyle)
    }
    val innerLabel = remember(measurer, labelStyle, radiusKm) {
        measurer.measure(AnnotatedString("${radiusKm / 2} km"), style = labelStyle)
    }
    val outerLabel = remember(measurer, labelStyle, radiusKm) {
        measurer.measure(AnnotatedString("$radiusKm km"), style = labelStyle)
    }
    val markerLabels = remember(measurer, markerStyle, points.size) {
        points.indices.map { measurer.measure(AnnotatedString("${it + 1}"), style = markerStyle) }
    }
    val labelSurface = remember(colors) { colors.surface.copy(alpha = 0.94f) }

    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun zoomTo(requested: Float) {
        val next = requested.coerceIn(1f, maxScale)
        val maxX = (next - 1f) * canvasSize.width / 2f
        val maxY = (next - 1f) * canvasSize.height / 2f
        scale = next
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
        if (next == 1f) {
            panX = 0f
            panY = 0f
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceVariant)
            .semantics {
                contentDescription =
                    "Peta skema ${species.size} titik perwakilan dalam radius $radiusKm km. " +
                    "Bukan peta jalan. Utara di atas, lokasi Anda di tengah. " +
                    "Geser dan cubit untuk memperbesar hingga empat kali; ketuk titik atau kartu daftar untuk detail."
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, maxScale)
                        val maxX = (next - 1f) * size.width / 2f
                        val maxY = (next - 1f) * size.height / 2f
                        scale = next
                        panX = (panX + pan.x).coerceIn(-maxX, maxX)
                        panY = (panY + pan.y).coerceIn(-maxY, maxY)
                        if (next == 1f) {
                            panX = 0f
                            panY = 0f
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val ring = size.minDimension * 0.39f * scale
            val origin = center + Offset(panX, panY)
            drawCircle(colors.outline, ring, origin, style = Stroke(2.dp.toPx()))
            drawCircle(colors.outline, ring / 2f, origin, style = Stroke(1.dp.toPx()))
            drawLine(colors.outline, origin - Offset(ring, 0f), origin + Offset(ring, 0f), strokeWidth = 1.dp.toPx())
            drawLine(colors.outline, origin - Offset(0f, ring), origin + Offset(0f, ring), strokeWidth = 1.dp.toPx())

            // Labels draw on a solid chip so they stay legible over rings at any zoom.
            fun label(layout: TextLayoutResult, anchor: Offset) {
                val width = layout.size.width.toFloat()
                val height = layout.size.height.toFloat()
                val pad = 6.dp.toPx()
                val x = (anchor.x - width / 2f).coerceIn(pad, (size.width - width - pad).coerceAtLeast(pad))
                val y = anchor.y.coerceIn(pad, (size.height - height - pad).coerceAtLeast(pad))
                drawRoundRect(
                    color = labelSurface,
                    topLeft = Offset(x - 4.dp.toPx(), y - 2.dp.toPx()),
                    size = Size(width + 8.dp.toPx(), height + 4.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawText(layout, topLeft = Offset(x, y))
            }

            label(northLabel, Offset(24.dp.toPx(), 10.dp.toPx()))
            label(innerLabel, Offset(origin.x, origin.y - ring / 2f - 26.dp.toPx()))
            label(outerLabel, Offset(origin.x, origin.y - ring - 26.dp.toPx()))

            drawCircle(colors.onSurface, 7.dp.toPx(), origin)
            drawCircle(colors.surface, 3.dp.toPx(), origin)

            val markerRadius = 13.dp.toPx()
            fun drawMarker(index: Int) {
                val position = markerPosition(
                    points[index], size.width, size.height, radiusKm, scale, panX, panY,
                )
                if (index == selected) {
                    drawCircle(colors.surface, markerRadius + 6.dp.toPx(), position)
                    drawCircle(colors.onSurface, markerRadius + 3.dp.toPx(), position)
                }
                drawCircle(colors.primary, markerRadius, position)
                val number = markerLabels[index]
                drawText(
                    number,
                    topLeft = Offset(
                        position.x - number.size.width / 2f,
                        position.y - number.size.height / 2f,
                    ),
                )
            }
            points.indices.forEach { if (it != selected) drawMarker(it) }
            // The selected marker draws last so its callout is never hidden.
            if (selected in points.indices) drawMarker(selected)
        }

        // Real clickable markers: reliable taps and readable by TalkBack, unlike canvas-only hits.
        points.forEachIndexed { index, point ->
            if (canvasSize.width > 0) {
                val anchor = markerPosition(
                    point, canvasSize.width.toFloat(), canvasSize.height.toFloat(),
                    radiusKm, scale, panX, panY,
                )
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (anchor.x - 22.dp.toPx()).roundToInt(),
                                (anchor.y - 22.dp.toPx()).roundToInt(),
                            )
                        }
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = if (index == selected) "Tutup detail" else "Buka detail",
                        ) { onSelect(if (index == selected) -1 else index) }
                        .semantics {
                            contentDescription =
                                "Titik ${index + 1}: ${species[index].commonName ?: species[index].scientificName}"
                        },
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(labelSurface),
        ) {
            IconButton(onClick = { zoomTo(scale * 1.5f) }, enabled = scale < maxScale) {
                Icon(Icons.Filled.Add, contentDescription = "Perbesar peta")
            }
            IconButton(onClick = { zoomTo(scale / 1.5f) }, enabled = scale > 1f) {
                Icon(Icons.Filled.Remove, contentDescription = "Perkecil peta")
            }
            IconButton(
                onClick = { zoomTo(1f) },
                enabled = scale > 1f || panX != 0f || panY != 0f,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atur ulang peta")
            }
        }

        if (selected in points.indices && canvasSize.width > 0) {
            val anchor = markerPosition(
                points[selected], canvasSize.width.toFloat(), canvasSize.height.toFloat(),
                radiusKm, scale, panX, panY,
            )
            AnchoredCallout(anchor = anchor, species = species[selected])
        }
    }
}

/** Places the selected marker callout above the marker, clamped inside the map canvas. */
@Composable
private fun AnchoredCallout(anchor: Offset, species: NearbySpecies) {
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = { CalloutCard(species) },
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val margin = 8.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val maxX = (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin)
        val maxY = (constraints.maxHeight - placeable.height - margin).coerceAtLeast(margin)
        val x = (anchor.x - placeable.width / 2f).roundToInt().coerceIn(margin, maxX)
        val y = (anchor.y - placeable.height - gap).roundToInt().coerceIn(margin, maxY)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(IntOffset(x, y)) }
    }
}

@Composable
private fun CalloutCard(species: NearbySpecies) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.widthIn(max = 240.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoTile(
                url = species.photo?.thumbUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                iconSize = 24.dp,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    species.commonName ?: species.scientificName,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (species.commonName != null) {
                    Text(
                        species.scientificName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = TextSecondary,
                    )
                }
                species.photo?.let { photo ->
                    Text(
                        "Foto: ${photo.credit} · ${licenseLabel(photo.license)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Typed recovery: every failure names its cause and offers the one action that can fix it,
 * mirroring PictureThis's `MapErrorTypeBottomSheetDialogFragment` without its generic copy.
 */
@Composable
private fun NearbyErrorSheet(
    kind: NearbyErrorKind?,
    message: String,
    onRetry: () -> Unit,
    onManualLocation: () -> Unit,
) {
    val title = when (kind) {
        NearbyErrorKind.LOCATION_OFF -> "Lokasi perangkat nonaktif"
        NearbyErrorKind.LOCATION_UNAVAILABLE -> "Lokasi belum tersedia"
        NearbyErrorKind.PERMISSION -> "Izin lokasi belum diberikan"
        NearbyErrorKind.NETWORK -> "Tidak ada koneksi internet"
        NearbyErrorKind.PROVIDER -> "Layanan tanaman sekitar sedang bermasalah"
        NearbyErrorKind.CACHE_MISS, null -> "Pencarian belum berhasil"
    }
    val advice = when (kind) {
        NearbyErrorKind.LOCATION_OFF ->
            "Aktifkan lokasi pada perangkat, lalu coba lagi. Anda juga dapat mencari dengan koordinat."
        NearbyErrorKind.LOCATION_UNAVAILABLE ->
            "Coba di area terbuka, atau masukkan koordinat tempat yang ingin diperiksa."
        NearbyErrorKind.PERMISSION ->
            "Izinkan lokasi perkiraan lewat tombol di atas, atau cari dengan koordinat."
        NearbyErrorKind.NETWORK ->
            "Sambungkan internet lalu coba lagi. Hasil yang tersimpan hari ini tetap bisa dipakai."
        NearbyErrorKind.PROVIDER -> "Sumber catatan publik belum menjawab. Coba lagi beberapa saat lagi."
        NearbyErrorKind.CACHE_MISS, null -> message
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Text(advice, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            PrimaryButton(text = "Coba lagi", onClick = onRetry, icon = Icons.Filled.Refresh)
            SecondaryButton(text = "Cari dengan koordinat", onClick = onManualLocation)
        }
    }
}

/** Coordinate entry for a place the device cannot locate; bounds are checked again in the ViewModel. */
@Composable
private fun ManualLocationSheet(onDismiss: () -> Unit, onConfirm: (Double, Double) -> Unit) {
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    val parsedLatitude = latitude.replace(',', '.').trim().toDoubleOrNull()
    val parsedLongitude = longitude.replace(',', '.').trim().toDoubleOrNull()
    val valid = parsedLatitude != null && parsedLatitude in -90.0..90.0 &&
        parsedLongitude != null && parsedLongitude in -180.0..180.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cari dengan koordinat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Masukkan lintang dan bujur tempat yang ingin diperiksa. Gunakan titik sebagai desimal. Koordinat hanya dipakai untuk pencarian ini.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it.take(12) },
                    label = { Text("Lintang (-90 sampai 90)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it.take(12) },
                    label = { Text("Bujur (-180 sampai 180)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (latitude.isNotBlank() && longitude.isNotBlank() && !valid) {
                    Text(
                        "Koordinat tidak valid. Periksa angka dan batasnya.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onConfirm(parsedLatitude!!, parsedLongitude!!) },
                enabled = valid,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Cari") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Batal") }
        },
    )
}
