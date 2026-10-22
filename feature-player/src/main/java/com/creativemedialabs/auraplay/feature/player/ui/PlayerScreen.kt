package com.creativemedialabs.auraplay.feature.player.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private fun isWebStream(url: String): Boolean {
    if (url.startsWith("/") || url.startsWith("file://")) {
        return false
    }
    val clean = url.substringBefore('?').lowercase()
    if (clean.endsWith(".mp4") || clean.endsWith(".m3u8") || clean.endsWith(".mkv") || clean.endsWith(".mpd") || url.contains(".m3u8")) {
        return false
    }
    val lower = url.lowercase()
    return lower.contains("autoembed") ||
           lower.contains("vidlink") ||
           lower.contains("vidsrc") ||
           lower.contains("embed")
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val window = activity?.window

    // Immersive Fullscreen and Sensor Orientation lifecycle
    DisposableEffect(Unit) {
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        // Extend video under camera notch / punch hole for true 100% full screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes?.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Keep screen awake while watching video
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Hide all system bars completely (status bar & nav bar) - nothing else visible!
        insetsController?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        // Enable sensor rotation so user can turn phone to landscape seamlessly
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

        onDispose {
            // Restore normal system bars
            insetsController?.apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                show(WindowInsetsCompat.Type.systemBars())
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Return to portrait orientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    var forceWebStream by remember(videoUrl) { mutableStateOf(false) }

    if (isWebStream(videoUrl) || forceWebStream) {
        WebStreamPlayer(videoUrl = videoUrl, onBack = onBack)
    } else {
        ExoPlayerStreamPlayer(
            videoUrl = videoUrl,
            onBack = onBack,
            onError = {
                forceWebStream = true
            }
        )
    }
}

@Composable
fun WebStreamPlayer(
    videoUrl: String,
    onBack: () -> Unit
) {
    var showOverlay by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var sniffedStreamUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as? Activity
    var isInPipMode by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true) true else false
        )
    }

    DisposableEffect(activity) {
        val componentActivity = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(true)
                    .build()
                activity.setPictureInPictureParams(params)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    val onEnterPip: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setAutoEnterEnabled(true)
                }
                activity.enterPictureInPictureMode(params.build())
            } catch (e: Exception) {
                Log.e("WebStreamPlayer", "Failed to enter PiP: ${e.message}")
            }
        }
    }

    // Auto-hide top controls after 4 seconds
    LaunchedEffect(showOverlay) {
        if (showOverlay) {
            delay(4000)
            showOverlay = false
        }
    }

    // Intercept back button to dismiss fullscreen or exit player
    BackHandler {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
        } else {
            webViewRef?.destroy()
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (customView != null) {
            AndroidView(
                factory = { customView!! },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                view?.evaluateJavascript(AuraAdBlocker.getAdBlockScript(), null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                view?.evaluateJavascript(AuraAdBlocker.getAdBlockScript(), null)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
                                if (AuraAdBlocker.isAdUrl(reqUrl)) {
                                    return AuraAdBlocker.createEmptyResourceResponse()
                                }
                                val lower = reqUrl.lowercase()
                                if ((lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains(".mp4")) &&
                                    !lower.contains("220884") && !lower.contains("abuse") && !lower.contains("preview")
                                ) {
                                    if (sniffedStreamUrl == null) {
                                        sniffedStreamUrl = reqUrl
                                        Log.d("WebStreamPlayer", "Sniffed direct stream: $reqUrl")
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return false
                                val scheme = uri.scheme?.lowercase() ?: ""
                                if (scheme != "http" && scheme != "https") {
                                    return true // block external apps / market
                                }
                                val urlStr = uri.toString()
                                if (AuraAdBlocker.isAdUrl(urlStr)) {
                                    return true // block navigation to any ad or tracker URL
                                }

                                val currentHost = Uri.parse(videoUrl).host?.lowercase() ?: ""
                                val targetHost = uri.host?.lowercase() ?: ""

                                if (request.isForMainFrame) {
                                    val isAllowedHost = targetHost.contains(currentHost) ||
                                                        currentHost.contains(targetHost) ||
                                                        targetHost.contains("vidlink") ||
                                                        targetHost.contains("vidsrc") ||
                                                        targetHost.contains("autoembed") ||
                                                        targetHost.contains("stream") ||
                                                        targetHost.contains("cdn") ||
                                                        urlStr.contains(".m3u8") ||
                                                        urlStr.contains(".mp4")
                                    if (!isAllowedHost) {
                                        Log.d("WebStreamPlayer", "Blocked main frame navigation to foreign/ad host: $targetHost")
                                        return true
                                    }
                                }
                                return false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                // Block all popups, ad windows, and new tabs
                                return false
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                if (newProgress > 25) {
                                    view?.evaluateJavascript(AuraAdBlocker.getAdBlockScript(), null)
                                }
                                if (newProgress > 60) {
                                    isLoading = false
                                }
                            }

                            override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                customView = view
                                customViewCallback = callback
                            }

                            override fun onHideCustomView() {
                                customViewCallback?.onCustomViewHidden()
                                customView = null
                                customViewCallback = null
                            }
                        }

                        loadUrl(videoUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading spinner
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFE50914), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading Web Stream...", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // Top Bar Overlay
        AnimatedVisibility(
            visible = showOverlay && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xDD000000), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        webViewRef?.destroy()
                        onBack()
                    },
                    modifier = Modifier.background(Color(0x66000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AuraPlay Free Web Stream",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (sniffedStreamUrl != null) "Ad-Blocked • 1080p FHD • Direct Stream Ready" else "Ad-Blocked • 1080p FHD • Zero Config",
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp
                    )
                }

                if (sniffedStreamUrl != null) {
                    IconButton(
                        onClick = {
                            val clip = android.content.ClipData.newPlainText("Direct Stream URL", sniffedStreamUrl)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "Direct offline stream captured!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.background(Color(0xFFE50914), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Direct Stream Captured",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                IconButton(
                    onClick = {
                        val act = context as? Activity
                        if (isLandscape) {
                            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                    modifier = Modifier.background(Color(0x66000000), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isLandscape) Icons.Default.ScreenLockPortrait else Icons.Default.ScreenRotation,
                        contentDescription = if (isLandscape) "Portrait" else "Landscape",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onEnterPip,
                    modifier = Modifier.background(Color(0x66000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "PIP Mode",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { webViewRef?.reload() },
                    modifier = Modifier.background(Color(0x66000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = Color.White
                    )
                }
            }
        }

        // Tap area at top to restore controls when hidden
        if (!showOverlay && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.TopCenter)
                    .clickable { showOverlay = true }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerStreamPlayer(
    videoUrl: String,
    onBack: () -> Unit,
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = context as? Activity

    // Build ExoPlayer with DefaultMediaSourceFactory and NetMirror HTTP headers
    val exoPlayer = remember {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0")
            .setDefaultRequestProperties(
                mapOf("Referer" to "https://net52.cc")
            )
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .build()
            .apply {
                try {
                    if (videoUrl.contains("220884") || videoUrl.contains("abuse", ignoreCase = true)) {
                        Log.w("PlayerScreen", "Detected anti-abuse placeholder video, switching to web backup")
                        onError()
                        return@apply
                    }
                    val uri = if (videoUrl.startsWith("/")) {
                        android.net.Uri.fromFile(java.io.File(videoUrl))
                    } else {
                        android.net.Uri.parse(videoUrl)
                    }
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = true
                } catch (e: Exception) {
                    Log.e("PlayerScreen", "ExoPlayer setup failed", e)
                    onError()
                }
            }
    }

    // Player state
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(1L) }
    var bufferingState by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isFillScreen by remember { mutableStateOf(false) }

    var isInPipMode by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true) true else false
        )
    }

    DisposableEffect(activity) {
        val componentActivity = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    LaunchedEffect(isPlaying, exoPlayer.videoSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            try {
                val rational = if (exoPlayer.videoSize.width > 0 && exoPlayer.videoSize.height > 0) {
                    val ratio = exoPlayer.videoSize.width.toFloat() / exoPlayer.videoSize.height.toFloat()
                    if (ratio in 0.42f..2.38f) {
                        Rational(exoPlayer.videoSize.width, exoPlayer.videoSize.height)
                    } else Rational(16, 9)
                } else Rational(16, 9)

                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                    .setAutoEnterEnabled(isPlaying)
                    .build()
                activity.setPictureInPictureParams(params)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    val onEnterPip: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            try {
                val rational = if (exoPlayer.videoSize.width > 0 && exoPlayer.videoSize.height > 0) {
                    val ratio = exoPlayer.videoSize.width.toFloat() / exoPlayer.videoSize.height.toFloat()
                    if (ratio in 0.42f..2.38f) {
                        Rational(exoPlayer.videoSize.width, exoPlayer.videoSize.height)
                    } else Rational(16, 9)
                } else Rational(16, 9)

                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setAutoEnterEnabled(isPlaying)
                }
                activity.enterPictureInPictureMode(params.build())
            } catch (e: Exception) {
                Log.e("PlayerScreen", "Failed to enter PiP: ${e.message}")
            }
        }
    }

    // Gesture indicator values
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var showTrackDialog by remember { mutableStateOf(false) }

    // Clear gesture indicator after delay
    LaunchedEffect(gestureIndicatorText) {
        if (gestureIndicatorText != null) {
            delay(1500)
            gestureIndicatorText = null
        }
    }

    // Listen for player events
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (exoPlayer.duration > 0) {
                    totalDuration = exoPlayer.duration
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                bufferingState = state == Player.STATE_BUFFERING
                if (exoPlayer.duration > 0) {
                    totalDuration = exoPlayer.duration
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlayerScreen", "ExoPlayer playback error: ${error.message}", error)
                gestureIndicatorText = "Stream source error. Switching to backup server..."
                onError()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Update seek position every second
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            if (exoPlayer.duration > 0 && totalDuration != exoPlayer.duration) {
                totalDuration = exoPlayer.duration
            }
            delay(1000)
        }
    }

    // Auto-hide controls after 4s
    LaunchedEffect(showControls) {
        if (showControls && !isScreenLocked) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!isInPipMode) {
                    Modifier
                        .pointerInput(isScreenLocked) {
                            if (!isScreenLocked) {
                                detectTapGestures(
                                    onTap = { showControls = !showControls },
                                    onDoubleTap = { offset ->
                                        val maxDur = if (exoPlayer.duration > 0) exoPlayer.duration else if (totalDuration > 1000L) totalDuration else Long.MAX_VALUE
                                        if (offset.x < size.width / 2) {
                                            val target = (exoPlayer.currentPosition - 10_000).coerceAtLeast(0L)
                                            exoPlayer.seekTo(target)
                                            currentPosition = target
                                            gestureIndicatorText = "-10s Rewind"
                                        } else {
                                            val target = (exoPlayer.currentPosition + 10_000).coerceAtMost(maxDur)
                                            exoPlayer.seekTo(target)
                                            currentPosition = target
                                            gestureIndicatorText = "+10s Fast Forward"
                                        }
                                        showControls = true
                                    }
                                )
                            } else {
                                detectTapGestures(onTap = { showControls = !showControls })
                            }
                        }
                        .pointerInput(isScreenLocked) {
                            if (!isScreenLocked) {
                                detectDragGestures { change, dragAmount ->
                                    val isLeftSide = change.position.x < size.width / 2
                                    val delta = -dragAmount.y

                                    if (isLeftSide && activity != null) {
                                        val lp = activity.window.attributes
                                        val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                        val newBrightness = (currentBrightness + (delta / size.height)).coerceIn(0.05f, 1.0f)
                                        lp.screenBrightness = newBrightness
                                        activity.window.attributes = lp
                                        gestureIndicatorText = "Brightness: ${(newBrightness * 100).toInt()}%"
                                    } else {
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val step = if (delta > 0) 1 else -1
                                        val newVol = (curVol + step).coerceIn(0, maxVol)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        gestureIndicatorText = "Volume: ${(newVol * 100 / maxVol)}%"
                                    }
                                }
                            }
                        }
                } else Modifier
            )
    ) {
        // ExoPlayer Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = if (isFillScreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    subtitleView?.apply {
                        setStyle(
                            CaptionStyleCompat(
                                AndroidColor.WHITE,
                                AndroidColor.argb(190, 0, 0, 0),
                                AndroidColor.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                AndroidColor.BLACK,
                                Typeface.DEFAULT_BOLD
                            )
                        )
                        setFractionalTextSize(0.048f)
                    }
                }
            },
            update = { playerView ->
                playerView.resizeMode = if (isFillScreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isInPipMode) {
            // Gesture HUD Overlay
            gestureIndicatorText?.let { text ->
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xCC000000)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                    )
                }
            }

            // Buffering Indicator
            if (bufferingState) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFE50914)
                )
            }

            // Screen Lock Indicator when Locked
            if (isScreenLocked && showControls) {
                IconButton(
                    onClick = { isScreenLocked = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(20.dp)
                        .background(Color(0x99000000), CircleShape)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color(0xFFE50914))
                }
            }

            // Full Netflix Overlay Controls (when not locked)
            if (showControls && !isScreenLocked) {
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                PlayerControlsOverlay(
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    totalDuration = totalDuration,
                    playbackSpeed = playbackSpeed,
                    isLandscape = isLandscape,
                    isFillScreen = isFillScreen,
                    onBack = {
                        exoPlayer.release()
                        onBack()
                    },
                    onPlayPause = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        isPlaying = !isPlaying
                    },
                    onSeekRelative = { deltaMs ->
                        val maxDur = if (exoPlayer.duration > 0) exoPlayer.duration else if (totalDuration > 1000L) totalDuration else Long.MAX_VALUE
                        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, maxDur)
                        exoPlayer.seekTo(target)
                        currentPosition = target
                    },
                    onLockScreen = { isScreenLocked = true },
                    onToggleSpeed = {
                        val nextSpeed = when (playbackSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 0.75f
                            else -> 1.0f
                        }
                        playbackSpeed = nextSpeed
                        exoPlayer.setPlaybackSpeed(nextSpeed)
                        gestureIndicatorText = "Speed: ${nextSpeed}x"
                    },
                    onToggleOrientation = {
                        if (isLandscape) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                    onToggleFillScreen = {
                        isFillScreen = !isFillScreen
                        gestureIndicatorText = if (isFillScreen) "Screen Fill (Zoom)" else "Original Ratio (Fit)"
                    },
                    onTracksClicked = { showTrackDialog = true },
                    onEnterPip = onEnterPip,
                    onSeek = { fraction ->
                        val durToUse = if (exoPlayer.duration > 0) exoPlayer.duration else if (totalDuration > 1000L) totalDuration else 0L
                        if (durToUse > 0) {
                            val target = (fraction * durToUse).toLong()
                            exoPlayer.seekTo(target)
                            currentPosition = target
                        }
                    }
                )
            }
        }
    }

    if (showTrackDialog) {
        TrackSelectionDialog(
            exoPlayer = exoPlayer,
            onDismiss = { showTrackDialog = false }
        )
    }
}

@Composable
private fun BoxScope.PlayerControlsOverlay(
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    playbackSpeed: Float,
    isLandscape: Boolean,
    isFillScreen: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onLockScreen: () -> Unit,
    onToggleSpeed: () -> Unit,
    onToggleOrientation: () -> Unit,
    onToggleFillScreen: () -> Unit,
    onTracksClicked: () -> Unit,
    onEnterPip: () -> Unit,
    onSeek: (Float) -> Unit
) {
    // Dark Scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x77000000))
    )

    // Top Bar (Back button, Title, Orientation, Lock & Audio/Subtitle icons)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Text(
            text = "Aura Play Cinema",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleOrientation) {
                Icon(
                    imageVector = if (isLandscape) Icons.Default.ScreenLockPortrait else Icons.Default.ScreenRotation,
                    contentDescription = if (isLandscape) "Switch to Portrait" else "Switch to Landscape",
                    tint = Color.White
                )
            }
            IconButton(onClick = onEnterPip) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = "PIP Mode",
                    tint = Color.White
                )
            }
            IconButton(onClick = onLockScreen) {
                Icon(Icons.Default.LockOpen, contentDescription = "Lock", tint = Color.White)
            }
            IconButton(onClick = onTracksClicked) {
                Icon(Icons.Default.Subtitles, contentDescription = "Audio & Subtitles", tint = Color.White)
            }
        }
    }

    // Center Controls (Netflix 10s Rewind | Play/Pause | 10s Forward)
    Row(
        modifier = Modifier.align(Alignment.Center),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // -10s Rewind
        IconButton(
            onClick = { onSeekRelative(-10_000) },
            modifier = Modifier
                .size(52.dp)
                .background(Color(0x33FFFFFF), CircleShape)
        ) {
            Icon(Icons.Default.Replay, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(28.dp))
        }

        // Center Play / Pause Button
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(76.dp)
                .background(Color(0x44FFFFFF), CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }

        // +10s Forward
        IconButton(
            onClick = { onSeekRelative(10_000) },
            modifier = Modifier
                .size(52.dp)
                .background(Color(0x33FFFFFF), CircleShape)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }

    // Bottom Bar (Netflix Red Scrubber & Action Controls)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Scrubber line
        val progress = if (totalDuration > 1000L) (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f) else 0f
        val timeRemaining = if (totalDuration > 1000L) (totalDuration - currentPosition).coerceAtLeast(0) else 0L

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(currentPosition),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = progress,
                onValueChange = onSeek,
                enabled = totalDuration > 1000L,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFE50914),
                    activeTrackColor = Color(0xFFE50914),
                    inactiveTrackColor = Color(0x66FFFFFF),
                    disabledThumbColor = Color.DarkGray,
                    disabledActiveTrackColor = Color.DarkGray
                )
            )
            Text(
                text = if (totalDuration > 1000L) "-${formatTime(timeRemaining)}" else "--:--",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Extra action controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Speed controller button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x33FFFFFF),
                    modifier = Modifier.clickable { onToggleSpeed() }
                ) {
                    Text(
                        text = "Speed (${playbackSpeed}x)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Fill vs Fit aspect ratio toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isFillScreen) Color(0xFFE50914) else Color(0x33FFFFFF),
                    modifier = Modifier.clickable { onToggleFillScreen() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isFillScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isFillScreen) "Fill" else "Fit",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Audio & Subtitles button
            Row(
                modifier = Modifier
                    .clickable { onTracksClicked() }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Audio & Subtitles", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TrackSelectionDialog(
    exoPlayer: ExoPlayer,
    onDismiss: () -> Unit
) {
    val audioTracks = remember(exoPlayer) {
        val tracks = mutableListOf<Pair<String, String?>>()
        tracks.add("Auto / Default" to null)
        for (group in exoPlayer.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language
                    val label = format.label
                    val name = when {
                        !label.isNullOrBlank() -> label
                        !lang.isNullOrBlank() -> {
                            val locale = java.util.Locale(lang)
                            val display = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
                            if (display.isNotBlank()) display else lang.uppercase()
                        }
                        else -> "Audio Track ${tracks.size}"
                    }
                    if (tracks.none { it.first == name }) {
                        tracks.add(name to lang)
                    }
                }
            }
        }
        tracks
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio & Subtitle Tracks", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Select Available Audio Track", color = Color.Gray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(audioTracks) { (displayName, langCode) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val params = exoPlayer.trackSelectionParameters.buildUpon()
                                    if (langCode != null) {
                                        params.setPreferredAudioLanguage(langCode)
                                    } else {
                                        params.setPreferredAudioLanguage(null)
                                    }
                                    exoPlayer.trackSelectionParameters = params.build()
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color(0xFFE50914))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(displayName, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFFE50914))
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
