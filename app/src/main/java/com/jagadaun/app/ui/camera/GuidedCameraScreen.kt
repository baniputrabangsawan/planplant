package com.jagadaun.app.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface as ComposeSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jagadaun.app.data.photos.PhotoStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val CameraBackground = Color(0xFF101713)
private val CameraPanel = Color(0xFF223329)
private val CameraAccent = Color(0xFFC9E9B3)
private val ViewNames = listOf("Depan", "Sisi", "Belakang", "Bunga/buah", "Detail")

/** Returns ordered, app-private photo drafts. The caller owns them only after onCaptured. */
@Composable
fun GuidedCameraScreen(
    photos: PhotoStore,
    onBack: () -> Unit,
    onCaptured: (List<String>) -> Unit,
    allowRotation: Boolean = true,
    /** Galeri hanya ditawarkan di dalam kamera, bukan sebagai dialog sumber foto. */
    onPickFromGallery: (() -> Unit)? = null,
) {
    val factory = remember(photos, allowRotation) {
        viewModelFactory {
            initializer { GuidedCameraViewModel(photos, createSavedStateHandle(), allowRotation) }
        }
    }
    val model: GuidedCameraViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context.applicationContext) }
    var permitted by remember { mutableStateOf(cameraPermitted(context)) }
    var askedPermission by rememberSaveable { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = it
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permitted = cameraPermitted(context)
    }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val cancel = { model.cancel { currentOnBack() } }
    BackHandler(onBack = cancel)

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permitted = cameraPermitted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!permitted && !askedPermission) {
            askedPermission = true
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    ComposeSurface(color = CameraBackground, contentColor = Color.White) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = cancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Batalkan pengambilan foto")
                }
                Text("Foto tanaman", style = MaterialTheme.typography.titleMedium)
            }
            if (!permitted) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Izinkan kamera untuk memotret tanaman.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        askedPermission = true
                        permission.launch(Manifest.permission.CAMERA)
                    }) { Text("Izinkan kamera") }
                    TextButton(onClick = {
                        try {
                            settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)))
                        } catch (_: Exception) {
                            permissionError = "Pengaturan tidak dapat dibuka. Buka izin kamera lewat pengaturan ponsel."
                        }
                    }) { Text("Buka pengaturan", color = CameraAccent) }
                    permissionError?.let { Text(it, textAlign = TextAlign.Center) }
                    if (onPickFromGallery != null) {
                        TextButton(onClick = onPickFromGallery) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Pilih dari Galeri", Modifier.padding(start = 8.dp), color = CameraAccent)
                        }
                    }
                    TextButton(onClick = cancel) { Text("Kembali", color = Color.White) }
                }
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val landscape = maxWidth > maxHeight
                    val viewport: @Composable (Modifier) -> Unit = { modifier ->
                        Box(modifier.background(Color.Black)) {
                            if (state.reviewing) {
                                DraftPhoto(photos, state.refs[state.selected], 1600,
                                    "Foto ${ViewNames[state.selected].lowercase()} tanaman", Modifier.fillMaxSize())
                                Text("Periksa ketajaman foto", Modifier.align(Alignment.TopCenter)
                                    .padding(12.dp).background(CameraBackground.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                    .padding(10.dp), textAlign = TextAlign.Center)
                            } else {
                                CameraViewport(
                                    busy = state.busy,
                                    prompt = capturePrompt(state),
                                    onCaptureReady = { capture = it },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    val controls: @Composable () -> Unit = {
                        CaptureControls(
                            photos = photos,
                            state = state,
                            ready = capture != null,
                            onSelect = model::select,
                            onRetake = model::retake,
                            onNext = model::next,
                            onAccept = { model.accept { currentOnCaptured(it) } },
                            onCapture = {
                                capture?.let {
                                    model.capture(it, mainExecutor, view.display?.rotation ?: Surface.ROTATION_0)
                                }
                            },
                            onPickFromGallery = onPickFromGallery,
                        )
                    }
                    if (landscape) {
                        Row(Modifier.fillMaxSize()) {
                            viewport(Modifier.weight(1f).fillMaxSize())
                            Column(Modifier.weight(0.85f).widthIn(max = 340.dp)
                                .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                                if (allowRotation) CameraModes(state, model::setSurround)
                                controls()
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            if (allowRotation) CameraModes(state, model::setSurround)
                            viewport(Modifier.weight(1f).fillMaxWidth())
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                controls()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraModes(state: GuidedCaptureState, onMode: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        listOf(false to "Satu foto", true to "360°").forEach { (surround, label) ->
            FilterChip(
                selected = state.surround == surround,
                onClick = { onMode(surround) },
                enabled = !state.busy,
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = CameraBackground, labelColor = Color.White,
                    selectedContainerColor = CameraAccent, selectedLabelColor = CameraBackground,
                    disabledLabelColor = Color.LightGray,
                    disabledSelectedContainerColor = CameraPanel,
                ),
            )
        }
    }
}

@Composable
private fun CaptureControls(
    photos: PhotoStore,
    state: GuidedCaptureState,
    ready: Boolean,
    onSelect: (Int) -> Unit,
    onRetake: () -> Unit,
    onNext: () -> Unit,
    onAccept: () -> Unit,
    onCapture: () -> Unit,
    onPickFromGallery: (() -> Unit)?,
) {
    if (state.surround) {
        Text("${state.captured}/$MAX_CAPTURE_VIEWS foto · tanaman yang sama. Satu foto sudah cukup.",
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(MAX_CAPTURE_VIEWS) { index ->
                val ref = state.refs[index]
                Box(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(10.dp))
                    .background(CameraPanel)
                    .border(if (state.selected == index) 2.dp else 1.dp,
                        if (state.selected == index) CameraAccent else Color.Gray, RoundedCornerShape(10.dp))
                    .clickable(enabled = !state.busy, role = Role.Button) { onSelect(index) }
                    .semantics {
                        selected = state.selected == index
                        contentDescription = "Foto ${index + 1}, ${ViewNames[index]}, " +
                            if (ref.isEmpty()) "belum diambil" else "sudah diambil; ketuk untuk periksa"
                    }) {
                    if (ref.isNotEmpty()) DraftPhoto(photos, ref, 240, null, Modifier.fillMaxSize())
                    Text("${index + 1} · ${ViewNames[index]}", Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth().background(CameraBackground.copy(alpha = 0.85f)).padding(3.dp),
                        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    state.error?.let {
        Text(it, color = Color(0xFFFFDAD6), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (state.busy) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), color = CameraAccent, strokeWidth = 2.dp)
            Text("Menyiapkan foto…", Modifier.padding(start = 12.dp), color = Color.White)
        }
    } else if (state.reviewing) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text("Ulangi foto", color = CameraAccent)
            }
            if (!state.full) {
                Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Foto berikutnya") }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onCapture, enabled = ready, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(if (state.surround) "Ambil foto ${state.selected + 1}" else "Ambil foto",
                    modifier = Modifier.padding(start = 8.dp))
            }
            if (onPickFromGallery != null) {
                OutlinedButton(onClick = onPickFromGallery, modifier = Modifier.height(52.dp)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Galeri", Modifier.padding(start = 6.dp), color = CameraAccent)
                }
            }
        }
        if (state.refs[state.selected].isNotEmpty()) {
            TextButton(onClick = { onSelect(state.selected) }, modifier = Modifier.fillMaxWidth()) {
                Text("Kembali ke foto sebelumnya", color = CameraAccent)
            }
        }
    }
    Button(onClick = onAccept, enabled = !state.busy && state.complete,
        colors = ButtonDefaults.buttonColors(disabledContainerColor = CameraPanel, disabledContentColor = Color.LightGray),
        modifier = Modifier.fillMaxWidth()) { Text("Gunakan foto") }
    Text("Foto tetap di ponsel sampai kamu melanjutkan.",
        style = MaterialTheme.typography.labelSmall, color = Color.LightGray,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

private fun capturePrompt(state: GuidedCaptureState): String = if (!state.surround) {
    "Tempatkan tanaman di dalam bingkai"
} else when (state.selected) {
    0 -> "1/$MAX_CAPTURE_VIEWS · Depan\nFoto seluruh tanaman dari depan."
    1 -> "2/$MAX_CAPTURE_VIEWS · Sisi\nGeser ke samping tanaman yang sama."
    2 -> "3/$MAX_CAPTURE_VIEWS · Belakang\nLanjut ke sisi lain tanaman yang sama."
    3 -> "4/$MAX_CAPTURE_VIEWS · Bunga/buah\nBunga dan buah paling membantu pengenalan."
    else -> "5/$MAX_CAPTURE_VIEWS · Detail\nFoto dekat daun atau gejala, tetap tanaman yang sama."
}

private data class DraftImageState(val image: ImageBitmap? = null, val loading: Boolean = true)

@Composable
private fun DraftPhoto(photos: PhotoStore, ref: String, maxSize: Int, description: String?, modifier: Modifier) {
    // An explicit state holder keeps the loading/loaded pair in one assignment, which the
    // produceState detector misreads; behaviour is identical to the previous declaration.
    var image by remember(photos, ref, maxSize) { mutableStateOf(DraftImageState()) }
    LaunchedEffect(photos, ref, maxSize) {
        image = withContext(Dispatchers.IO) {
            DraftImageState(photos.readBitmap(ref, maxSize)?.asImageBitmap(), loading = false)
        }
    }
    val bitmap = image.image
    if (bitmap == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(if (image.loading) "Memuat foto…" else "Foto tidak terbaca. Ambil ulang.",
                color = Color.White, style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center)
        }
    } else {
        Image(bitmap, contentDescription = description, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

private fun cameraPermitted(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CameraViewport(
    busy: Boolean,
    prompt: String,
    onCaptureReady: (ImageCapture?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var light by remember { mutableStateOf("Ketuk tanaman untuk fokus. Tahan ponsel saat memotret.") }
    var focus by remember { mutableStateOf<Offset?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val onReady by rememberUpdatedState(onCaptureReady)

    DisposableEffect(owner, previewView, attempt) {
        val executor = ContextCompat.getMainExecutor(context.applicationContext)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0).build()
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var observedCamera: Camera? = null
        error = null
        opening = true
        onReady(null)
        val cameraObserver = Observer<CameraState> { state ->
            if (!disposed) {
                opening = state.type != CameraState.Type.OPEN
                onReady(if (!opening) capture else null)
                error = if (state.error != null || state.type == CameraState.Type.PENDING_OPEN) {
                    "Kamera belum siap. Tutup aplikasi lain yang memakai kamera."
                } else null
            }
        }
        val torchObserver = Observer<Int> { if (!disposed) torch = it == TorchState.ON }
        analysis.setAnalyzer(analysisExecutor, PreviewLightAnalyzer { message ->
            executor.execute { if (!disposed) light = message }
        })
        val future = ProcessCameraProvider.getInstance(context.applicationContext)
        future.addListener({
            if (!disposed) {
                try {
                    val readyProvider = future.get()
                    provider = readyProvider
                    val selectedCamera = try {
                        readyProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                    } catch (_: IllegalArgumentException) {
                        // Some devices cannot combine still capture and analysis. Keep the real
                        // preview/shutter usable; only the optional light hint is omitted.
                        readyProvider.unbind(preview, capture, analysis)
                        analysis.clearAnalyzer()
                        readyProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    }
                    observedCamera = selectedCamera
                    camera = selectedCamera
                    hasTorch = selectedCamera.cameraInfo.hasFlashUnit()
                    selectedCamera.cameraInfo.cameraState.observe(owner, cameraObserver)
                    selectedCamera.cameraInfo.torchState.observe(owner, torchObserver)
                } catch (_: Exception) {
                    onReady(null)
                    opening = false
                    error = "Kamera tidak dapat dibuka. Periksa izin dan tutup aplikasi kamera lain."
                }
            }
        }, executor)
        onDispose {
            disposed = true
            onReady(null)
            analysis.clearAnalyzer()
            observedCamera?.let {
                it.cameraInfo.cameraState.removeObserver(cameraObserver)
                it.cameraInfo.torchState.removeObserver(torchObserver)
                runCatching { it.cameraControl.enableTorch(false) }
            }
            try {
                provider?.unbind(preview, capture, analysis)
            } finally {
                camera = null
                torch = false
                hasTorch = false
                analysisExecutor.shutdown()
            }
        }
    }
    LaunchedEffect(focus) {
        if (focus != null) {
            delay(1200)
            focus = null
        }
    }
    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().pointerInput(camera, busy) {
            detectTapGestures { position ->
                if (!busy) camera?.let { active ->
                    try {
                        val point = previewView.meteringPointFactory.createPoint(position.x, position.y)
                        active.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS).build())
                        focus = position
                    } catch (_: IllegalArgumentException) {
                        light = "Fokus belum tersedia. Coba ketuk lagi."
                    }
                }
            }
        })
        Canvas(Modifier.fillMaxSize()) {
            val frameWidth = size.width * 0.78f
            val frameHeight = size.height * 0.7f
            drawRoundRect(Color.White.copy(alpha = 0.8f),
                topLeft = Offset((size.width - frameWidth) / 2, (size.height - frameHeight) / 2),
                size = Size(frameWidth, frameHeight), cornerRadius = CornerRadius(22.dp.toPx()),
                style = Stroke(2.dp.toPx()))
            focus?.let { drawCircle(CameraAccent, 24.dp.toPx(), it, style = Stroke(2.dp.toPx())) }
        }
        Text(prompt, Modifier.align(Alignment.TopCenter).padding(horizontal = 48.dp, vertical = 12.dp)
            .background(CameraBackground.copy(alpha = 0.88f), RoundedCornerShape(12.dp)).padding(10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
            color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (hasTorch) {
            IconButton(onClick = {
                camera?.let { active ->
                    val result = active.cameraControl.enableTorch(!torch)
                    result.addListener({
                        try { result.get() } catch (_: Exception) { light = "Lampu belum tersedia. Coba lagi." }
                    }, ContextCompat.getMainExecutor(context))
                }
            }, enabled = !busy,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    .background(CameraBackground.copy(alpha = 0.85f), CircleShape)) {
                Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = if (torch) "Matikan lampu" else "Nyalakan lampu", tint = Color.White)
            }
        }
        Text(light, Modifier.align(Alignment.BottomCenter).padding(12.dp)
            .background(CameraBackground.copy(alpha = 0.88f), RoundedCornerShape(12.dp)).padding(10.dp),
            color = Color.White, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        if (opening && error == null) {
            Column(Modifier.align(Alignment.Center).background(CameraBackground.copy(alpha = 0.85f),
                RoundedCornerShape(12.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(24.dp), color = CameraAccent, strokeWidth = 2.dp)
                Text("Menyiapkan kamera…", Modifier.padding(top = 8.dp), color = Color.White,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { message ->
            Column(Modifier.align(Alignment.Center).padding(24.dp)
                .background(CameraBackground, RoundedCornerShape(16.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = { attempt++ }, enabled = !busy) { Text("Coba lagi", color = CameraAccent) }
            }
        }
    }
}

/** Samples real preview luminance only; this does not detect plants or judge diagnoses. */
private class PreviewLightAnalyzer(private val onHint: (String) -> Unit) : ImageAnalysis.Analyzer {
    private var lastTimestamp = Long.MIN_VALUE
    private var previousHint: String? = null

    override fun analyze(image: ImageProxy) {
        try {
            val timestamp = image.imageInfo.timestamp
            if (lastTimestamp != Long.MIN_VALUE && timestamp - lastTimestamp < 500_000_000L) return
            lastTimestamp = timestamp
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val base = buffer.position()
            val crop = image.cropRect
            var total = 0L
            var samples = 0
            val stepX = (crop.width() / 16).coerceAtLeast(1)
            val stepY = (crop.height() / 16).coerceAtLeast(1)
            var y = crop.top
            while (y < crop.bottom) {
                var x = crop.left
                while (x < crop.right) {
                    val index = base + y * plane.rowStride + x * plane.pixelStride
                    if (index < buffer.limit()) {
                        total += buffer.get(index).toInt() and 0xff
                        samples++
                    }
                    x += stepX
                }
                y += stepY
            }
            if (samples == 0) return
            val mean = total / samples
            val hint = when {
                mean < 45 -> "Pratinjau gelap. Cari cahaya lebih terang atau nyalakan lampu."
                mean > 225 -> "Pratinjau sangat terang. Hindari sorot cahaya langsung."
                else -> "Ketuk tanaman untuk fokus. Tahan ponsel saat memotret."
            }
            if (hint != previousHint) {
                previousHint = hint
                onHint(hint)
            }
        } finally {
            image.close()
        }
    }
}
