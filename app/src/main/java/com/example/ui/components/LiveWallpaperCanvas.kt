package com.example.ui.components

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.R
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

data class LiveWallpaperPreset(
    val id: String,
    val title: String,
    val category: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val accentColor: Color,
    val drawableRes: Int? = null,
    val motionStyle: MotionStyle = MotionStyle.AURORA
)

enum class MotionStyle {
    AURORA, CASCADE, OCEAN, RAIN, EMBER, STARFIELD, ZEN_RIPPLE, CYBER_GRID
}

object LiveWallpaperCatalog {
    val categories = listOf(
        "Tous",
        "Paysages naturels",
        "Cascades et rivières",
        "Océans et plages",
        "Montagnes",
        "Forêts et arbres",
        "Ciels étoilés",
        "Couchers et levers de soleil",
        "Pluie et orages",
        "Paysages nocturnes",
        "Feux de cheminée",
        "Paysages futuristes",
        "Animations relaxantes"
    )

    val presets = listOf(
        LiveWallpaperPreset(
            id = "cosmos_aurora",
            title = "Nébuleuse & Lac Alpin",
            category = "Ciels étoilés",
            primaryColor = Color(0xFF140D22),
            secondaryColor = Color(0xFF3A1D4D),
            accentColor = Color(0xFFCFA2EC),
            drawableRes = R.drawable.img_wallpaper_cosmos_1794552307855,
            motionStyle = MotionStyle.STARFIELD
        ),
        LiveWallpaperPreset(
            id = "cascade_emerald",
            title = "Cascade Émeraude",
            category = "Cascades et rivières",
            primaryColor = Color(0xFF0A221C),
            secondaryColor = Color(0xFF1B4B3F),
            accentColor = Color(0xFF8BE2C6),
            drawableRes = R.drawable.img_wallpaper_cascade_1794552307246,
            motionStyle = MotionStyle.CASCADE
        ),
        LiveWallpaperPreset(
            id = "prunelle_silk",
            title = "Soie Prunelle",
            category = "Animations relaxantes",
            primaryColor = Color(0xFF211024),
            secondaryColor = Color(0xFF562B54),
            accentColor = Color(0xFFE7B8DC),
            drawableRes = R.drawable.img_prunelle_hero_1794552305674,
            motionStyle = MotionStyle.ZEN_RIPPLE
        ),
        LiveWallpaperPreset(
            id = "ocean_tide",
            title = "Marée Turquoise",
            category = "Océans et plages",
            primaryColor = Color(0xFF072336),
            secondaryColor = Color(0xFF125A78),
            accentColor = Color(0xFF7BE3F5),
            motionStyle = MotionStyle.OCEAN
        ),
        LiveWallpaperPreset(
            id = "alpine_peaks",
            title = "Sommets Brumeux",
            category = "Montagnes",
            primaryColor = Color(0xFF182230),
            secondaryColor = Color(0xFF3A4B63),
            accentColor = Color(0xFFB8CCE6),
            motionStyle = MotionStyle.AURORA
        ),
        LiveWallpaperPreset(
            id = "forest_mist",
            title = "Forêt Boréale",
            category = "Forêts et arbres",
            primaryColor = Color(0xFF0C1F14),
            secondaryColor = Color(0xFF224D34),
            accentColor = Color(0xFF9BE0AD),
            motionStyle = MotionStyle.CASCADE
        ),
        LiveWallpaperPreset(
            id = "golden_horizon",
            title = "Crépuscule Doré",
            category = "Couchers et levers de soleil",
            primaryColor = Color(0xFF331524),
            secondaryColor = Color(0xFF9E4244),
            accentColor = Color(0xFFFFC178),
            motionStyle = MotionStyle.AURORA
        ),
        LiveWallpaperPreset(
            id = "rain_storm",
            title = "Pluie d'Orage",
            category = "Pluie et orages",
            primaryColor = Color(0xFF101826),
            secondaryColor = Color(0xFF24354F),
            accentColor = Color(0xFF96B8E8),
            motionStyle = MotionStyle.RAIN
        ),
        LiveWallpaperPreset(
            id = "nocturne_plum",
            title = "Nuit Velours",
            category = "Paysages nocturnes",
            primaryColor = Color(0xFF0A0812),
            secondaryColor = Color(0xFF271738),
            accentColor = Color(0xFFB594DE),
            motionStyle = MotionStyle.STARFIELD
        ),
        LiveWallpaperPreset(
            id = "hearth_ember",
            title = "Braises Chaleureuses",
            category = "Feux de cheminée",
            primaryColor = Color(0xFF1E0B07),
            secondaryColor = Color(0xFF6B2211),
            accentColor = Color(0xFFFF9852),
            motionStyle = MotionStyle.EMBER
        ),
        LiveWallpaperPreset(
            id = "cyber_prism",
            title = "Horizon Prismatique",
            category = "Paysages futuristes",
            primaryColor = Color(0xFF0D0F29),
            secondaryColor = Color(0xFF361F69),
            accentColor = Color(0xFF5EEAD4),
            motionStyle = MotionStyle.CYBER_GRID
        ),
        LiveWallpaperPreset(
            id = "nature_valley",
            title = "Vallée Sereine",
            category = "Paysages naturels",
            primaryColor = Color(0xFF16241B),
            secondaryColor = Color(0xFF385E45),
            accentColor = Color(0xFFC5E8B7),
            motionStyle = MotionStyle.ZEN_RIPPLE
        )
    )

    fun findById(id: String?): LiveWallpaperPreset =
        presets.firstOrNull { it.id == id } ?: presets.first()
}

fun queryDeviceMaxRefreshHz(context: Context): Int {
    return runCatching {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.refreshRate?.toInt() ?: 60
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.refreshRate.toInt()
        }
    }.getOrDefault(60).coerceIn(30, 120)
}

@Composable
fun LiveWallpaperBackground(
    wallpaperId: String?,
    customUri: String?,
    targetFps: Int,
    dataSaverEnabled: Boolean,
    dimAlpha: Float = 0.28f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember { mutableStateOf(true) }
    var videoPlaybackFailed by remember(customUri) { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isForeground = event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isCustomValid = remember(customUri) {
        if (customUri.isNullOrBlank()) false
        else if (customUri.startsWith("file://")) {
            Uri.parse(customUri).path?.let { File(it).exists() } == true
        } else true
    }

    val isVideoUri = remember(customUri, isCustomValid) {
        if (!isCustomValid || customUri.isNullOrBlank()) false
        else {
            val mime = runCatching { context.contentResolver.getType(Uri.parse(customUri)) }.getOrNull()
            mime?.startsWith("video/") == true ||
                customUri.endsWith(".mp4", ignoreCase = true) ||
                customUri.endsWith(".webm", ignoreCase = true) ||
                customUri.endsWith(".mov", ignoreCase = true)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val preset = remember(wallpaperId) { LiveWallpaperCatalog.findById(wallpaperId) }

        if (isCustomValid && isVideoUri && !videoPlaybackFailed && !customUri.isNullOrBlank()) {
            // Render preset subtly beneath while TextureView prepares its first frame -> ZERO black screens
            Procedural4KLiveCanvas(
                preset = preset,
                isForeground = false,
                targetFps = 30,
                modifier = Modifier.fillMaxSize()
            )
            key(customUri) {
                LoopingGalleryVideoWallpaper(
                    videoUri = customUri,
                    isActive = isForeground && !dataSaverEnabled,
                    onPlaybackError = { videoPlaybackFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (isCustomValid && !isVideoUri && !customUri.isNullOrBlank()) {
            AsyncImage(
                model = customUri,
                contentDescription = "Fond d'écran personnalisé",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Procedural4KLiveCanvas(
                preset = preset,
                isForeground = isForeground,
                targetFps = if (dataSaverEnabled) 30 else targetFps,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.background.copy(alpha = dimAlpha)
                )
        )
    }
}

private fun applyCenterCropTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    videoWidth: Int,
    videoHeight: Int
) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
    val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    val scaleX: Float
    val scaleY: Float
    if (videoAspect > viewAspect) {
        scaleX = videoAspect / viewAspect
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = viewAspect / videoAspect
    }
    val matrix = Matrix().apply {
        setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    }
    textureView.setTransform(matrix)
}

@Composable
private fun LoopingGalleryVideoWallpaper(
    videoUri: String,
    isActive: Boolean,
    onPlaybackError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeSurface by remember { mutableStateOf<Surface?>(null) }
    var videoDims by remember { mutableStateOf(0 to 0) }
    val activeState by rememberUpdatedState(isActive)

    LaunchedEffect(isActive, mediaPlayer) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        runCatching {
            if (isActive && !mp.isPlaying) mp.start()
            else if (!isActive && mp.isPlaying) mp.pause()
        }
    }

    DisposableEffect(videoUri) {
        onDispose {
            runCatching {
                if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            runCatching { activeSurface?.release() }
            mediaPlayer = null
            activeSurface = null
        }
    }

    AndroidView(
        factory = { ctx ->
            val textureView = TextureView(ctx)
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    val surface = Surface(st)
                    activeSurface = surface
                    runCatching {
                        val mp = MediaPlayer().apply {
                            val parsed = Uri.parse(videoUri)
                            setDataSource(context, parsed)
                            setSurface(surface)
                            isLooping = true
                            setVolume(0f, 0f)
                            setOnVideoSizeChangedListener { _, vw, vh ->
                                videoDims = vw to vh
                                applyCenterCropTransform(textureView, textureView.width, textureView.height, vw, vh)
                            }
                            setOnPreparedListener { player ->
                                if (activeState) player.start()
                            }
                            setOnErrorListener { _, _, _ ->
                                onPlaybackError()
                                true
                            }
                            prepareAsync()
                        }
                        mediaPlayer = mp
                    }.onFailure {
                        onPlaybackError()
                    }
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                    applyCenterCropTransform(textureView, w, h, videoDims.first, videoDims.second)
                }

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    runCatching {
                        if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
                        mediaPlayer?.release()
                    }
                    runCatching { activeSurface?.release() }
                    mediaPlayer = null
                    activeSurface = null
                    return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
            textureView
        },
        update = { tv ->
            applyCenterCropTransform(tv, tv.width, tv.height, videoDims.first, videoDims.second)
        },
        modifier = modifier
    )
}

@Composable
private fun Procedural4KLiveCanvas(
    preset: LiveWallpaperPreset,
    isForeground: Boolean,
    targetFps: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val deviceMaxHz = remember { queryDeviceMaxRefreshHz(context) }
    val effectiveFps = targetFps.coerceAtMost(deviceMaxHz).coerceAtLeast(30)
    val frameIntervalNanos = 1_000_000_000L / effectiveFps
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isForeground, effectiveFps) {
        if (!isForeground) return@LaunchedEffect
        var lastDrawn = 0L
        while (true) {
            withFrameNanos { now ->
                if (now - lastDrawn >= frameIntervalNanos) {
                    lastDrawn = now
                    phase = (phase + 0.008f) % (2f * Math.PI.toFloat() * 100f)
                }
            }
        }
    }

    Box(modifier = modifier) {
        preset.drawableRes?.let { resId ->
            Image(
                painter = painterResource(id = resId),
                contentDescription = preset.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (preset.drawableRes == null) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(preset.primaryColor, preset.secondaryColor, preset.primaryColor),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                )
            }

            when (preset.motionStyle) {
                MotionStyle.STARFIELD -> {
                    for (i in 0 until 26) {
                        val x = ((i * 73 + sin(phase * 0.25f + i) * 14f) % w + w) % w
                        val y = ((i * 137 + cos(phase * 0.18f + i) * 10f) % h + h) % h
                        val alpha = 0.22f + 0.35f * (0.5f + 0.5f * sin(phase * 1.4f + i))
                        drawCircle(
                            color = preset.accentColor.copy(alpha = alpha),
                            radius = (2f + (i % 3) * 1.8f),
                            center = Offset(x, y)
                        )
                    }
                }
                MotionStyle.RAIN -> {
                    for (i in 0 until 28) {
                        val x = (i * (w / 28f) + (phase * 18f)) % w
                        val yStart = ((i * 97f + phase * 320f) % h)
                        drawLine(
                            color = preset.accentColor.copy(alpha = 0.24f),
                            start = Offset(x, yStart),
                            end = Offset(x - 10f, yStart + 42f),
                            strokeWidth = 2.2f
                        )
                    }
                }
                MotionStyle.EMBER -> {
                    for (i in 0 until 20) {
                        val x = (w * 0.15f + (i * 53f % (w * 0.7f)) + sin(phase * 1.3f + i) * 18f)
                        val y = h - ((i * 67f + phase * 120f) % h)
                        drawCircle(
                            color = preset.accentColor.copy(alpha = 0.32f),
                            radius = 3f + (i % 4) * 2f,
                            center = Offset(x, y)
                        )
                    }
                }
                MotionStyle.CASCADE, MotionStyle.OCEAN, MotionStyle.AURORA, MotionStyle.ZEN_RIPPLE, MotionStyle.CYBER_GRID -> {
                    for (i in 0 until 5) {
                        val cx = w * (0.2f + 0.15f * i) + sin(phase * 0.45f + i) * (w * 0.14f)
                        val cy = h * (0.25f + 0.12f * i) + cos(phase * 0.38f + i) * (h * 0.1f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    preset.accentColor.copy(alpha = 0.16f),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = w * 0.45f
                            ),
                            radius = w * 0.45f,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }
        }
    }
}
