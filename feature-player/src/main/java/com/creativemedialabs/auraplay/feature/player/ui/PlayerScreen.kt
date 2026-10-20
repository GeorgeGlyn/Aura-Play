package com.creativemedialabs.auraplay.feature.player.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.media.AudioManager
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private fun isWebStream(url: String): Boolean {
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
    if (isWebStream(videoUrl)) {
        WebStreamPlayer(videoUrl = videoUrl, onBack = onBack)
    } else {
        ExoPlayerStreamPlayer(videoUrl = videoUrl, onBack = onBack)
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
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return false
                                val scheme = uri.scheme?.lowercase() ?: ""
                                if (scheme != "http" && scheme != "https") {
                                    return true // block external apps / market
                                }
                                val host = uri.host?.lowercase() ?: ""
                                // Only block known intrusive ad popups on main frame
                                if (request.isForMainFrame) {
                                    val isAd = host.contains("adsterra") ||
                                               host.contains("popcash") ||
                                               host.contains("bet365") ||
                                               host.contains("histats") ||
                                               host.contains("onclick") ||
                                               host.contains("traffic")
                                    if (isAd) return true
                                }
                                return false // allow player iframes, CDNs, and media streams
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
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
            visible = showOverlay,
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
                        text = "Ad-Blocked • 1080p FHD • Zero Config",
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp
                    )
                }

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

        // Persistent floating back button when top bar is hidden
        if (!showOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = {
                        webViewRef?.destroy()
                        onBack()
                    },
                    modifier = Modifier.background(Color(0x77000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerStreamPlayer(
    videoUrl: String,
    onBack: () -> Unit
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
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                try {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    prepare()
                    playWhenReady = true
                } catch (e: Exception) {
                    Log.e("PlayerScreen", "ExoPlayer setup failed", e)
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
            override fun onPlaybackStateChanged(state: Int) {
                bufferingState = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration.coerceAtLeast(1L)
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlayerScreen", "ExoPlayer playback error: ${error.message}", error)
                gestureIndicatorText = "Stream error: ${error.errorCodeName}"
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
            .pointerInput(isScreenLocked) {
                if (!isScreenLocked) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2) {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0))
                                gestureIndicatorText = "-10s Rewind"
                            } else {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(exoPlayer.duration))
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
    ) {
        // ExoPlayer Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
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
            modifier = Modifier.fillMaxSize()
        )

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
            PlayerControlsOverlay(
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                playbackSpeed = playbackSpeed,
                onBack = {
                    exoPlayer.release()
                    onBack()
                },
                onPlayPause = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    isPlaying = !isPlaying
                },
                onSeekRelative = { deltaMs ->
                    exoPlayer.seekTo((exoPlayer.currentPosition + deltaMs).coerceIn(0, exoPlayer.duration))
                    currentPosition = exoPlayer.currentPosition
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
                onTracksClicked = { showTrackDialog = true },
                onSeek = { fraction ->
                    exoPlayer.seekTo((fraction * totalDuration).toLong())
                    currentPosition = exoPlayer.currentPosition
                }
            )
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
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onLockScreen: () -> Unit,
    onToggleSpeed: () -> Unit,
    onTracksClicked: () -> Unit,
    onSeek: (Float) -> Unit
) {
    // Dark Scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x77000000))
    )

    // Top Bar (Back button, Title, Lock & Audio/Subtitle icons)
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
        val progress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f
        val timeRemaining = (totalDuration - currentPosition).coerceAtLeast(0)

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
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFE50914),
                    activeTrackColor = Color(0xFFE50914),
                    inactiveTrackColor = Color(0x66FFFFFF)
                )
            )
            Text(
                text = "-${formatTime(timeRemaining)}",
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
    val languages = listOf("English", "Tamil", "Hindi", "Telugu", "Malayalam", "Auto")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio & Subtitle Tracks", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Select Preferred Audio Track", color = Color.Gray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(languages) { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val langCode = when (lang) {
                                        "English" -> "eng"
                                        "Tamil" -> "tam"
                                        "Hindi" -> "hin"
                                        "Telugu" -> "tel"
                                        "Malayalam" -> "mal"
                                        else -> null
                                    }
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
                            Text(lang, color = Color.White, fontSize = 15.sp)
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
