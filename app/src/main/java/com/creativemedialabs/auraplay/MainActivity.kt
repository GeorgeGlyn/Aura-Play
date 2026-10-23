package com.creativemedialabs.auraplay

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.creativemedialabs.auraplay.core.database.dao.DownloadDao
import com.creativemedialabs.auraplay.feature.details.ui.DetailsScreen
import com.creativemedialabs.auraplay.feature.downloads.notification.DownloadNotificationHelper
import com.creativemedialabs.auraplay.feature.downloads.notification.InAppNotificationBanner
import com.creativemedialabs.auraplay.feature.downloads.notification.InAppNotificationManager
import com.creativemedialabs.auraplay.feature.downloads.ui.DownloadsScreen
import com.creativemedialabs.auraplay.feature.home.ui.CategoryViewAllScreen
import com.creativemedialabs.auraplay.feature.home.ui.HomeScreen
import com.creativemedialabs.auraplay.feature.home.ui.NewAndHotScreen
import com.creativemedialabs.auraplay.feature.home.ui.SearchScreen
import com.creativemedialabs.auraplay.feature.player.ui.PlayerScreen
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

sealed class BottomNavItem(val route: String, val label: String) {
    object Home : BottomNavItem("home", "Home")
    object Search : BottomNavItem("search", "Search")
    object HotAndNew : BottomNavItem("hot", "New & Hot")
    object Downloads : BottomNavItem("downloads", "Downloads")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var downloadDao: DownloadDao

    private val playUrlFromNotification = mutableStateOf<String?>(null)
    var isPlayerActive = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.R) {
            if (isPlayerActive) {
                try {
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Initialize download notification channels for system tray
        DownloadNotificationHelper.initChannels(this)

        handlePlayIntent(intent)

        setContent {
            // Android 13+ runtime notification permission request
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* handle result */ }

                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFE50914),
                    background = Color(0xFF070707),
                    surface = Color(0xFF121212),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                AuraPlayApp(
                    downloadDao = downloadDao,
                    initialPlayUrl = playUrlFromNotification.value
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handlePlayIntent(intent)
    }

    private fun handlePlayIntent(intent: Intent?) {
        intent?.getStringExtra("EXTRA_PLAY_URL")?.let { path ->
            playUrlFromNotification.value = path
        }
    }
}

@Composable
private fun AuraPlayApp(
    downloadDao: DownloadDao,
    initialPlayUrl: String? = null
) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // Direct playback from notification click
    LaunchedEffect(initialPlayUrl) {
        if (!initialPlayUrl.isNullOrBlank()) {
            val encodedPath = URLEncoder.encode(initialPlayUrl, StandardCharsets.UTF_8.toString())
            navController.navigate("player?url=$encodedPath")
        }
    }

    // Monitor completed downloads while app is open to pop up in-app notification banner
    val allDownloads by downloadDao.getAllDownloads().collectAsState(initial = emptyList())
    val previousStatuses = remember { mutableMapOf<Int, String>() }

    LaunchedEffect(allDownloads) {
        for (dl in allDownloads) {
            val prev = previousStatuses[dl.mediaId]
            if (prev == "DOWNLOADING" && dl.downloadStatus == "COMPLETED") {
                InAppNotificationManager.showDownloadCompleted(dl.title) {
                    val encoded = URLEncoder.encode(dl.localFilePath, StandardCharsets.UTF_8.toString())
                    navController.navigate("player?url=$encoded")
                }
            }
            previousStatuses[dl.mediaId] = dl.downloadStatus
        }
    }

    val bottomNavRoutes = listOf(
        BottomNavItem.Home.route,
        BottomNavItem.Search.route,
        BottomNavItem.HotAndNew.route,
        BottomNavItem.Downloads.route
    )
    val context = LocalContext.current
    val isPlayerRoute = currentRoute?.startsWith("player") == true

    var isInPipMode by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (context as? Activity)?.isInPictureInPictureMode == true) true else false
        )
    }

    DisposableEffect(context) {
        val componentActivity = context as? androidx.activity.ComponentActivity
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    LaunchedEffect(isPlayerRoute) {
        (context as? MainActivity)?.isPlayerActive = isPlayerRoute
    }

    val isTv = remember {
        BuildConfig.FLAVOR == "tv" ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        (context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager)?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        (context.resources.configuration.smallestScreenWidthDp >= 600 && context.packageManager.hasSystemFeature("android.hardware.type.television"))
    }

    LaunchedEffect(isPlayerRoute, isInPipMode, isTv) {
        if (isTv || BuildConfig.FLAVOR == "tv") {
            (context as? Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else if (!isPlayerRoute && !isInPipMode) {
            (context as? Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    if (isTv) {
        // ----------------------------------------------------------------------
        // D-PAD & 16:9 OPTIMIZED TV SHELL
        // ----------------------------------------------------------------------
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070707))
        ) {
            if (!isPlayerRoute) {
                com.creativemedialabs.auraplay.tv.TvNavigationRail(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(BottomNavItem.Home.route)
                            launchSingleTop = true
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onNavigateToDetails = { mediaId, type ->
                                navController.navigate("details/$mediaId?type=$type")
                            },
                            onNavigateToSearch = {
                                navController.navigate("search")
                            },
                            onNavigateToCategory = { title, categoryType, genreId, mediaType ->
                                val encTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                                val encType = URLEncoder.encode(categoryType, StandardCharsets.UTF_8.toString())
                                val encGenre = URLEncoder.encode(genreId ?: "", StandardCharsets.UTF_8.toString())
                                navController.navigate("category_view_all?title=$encTitle&categoryType=$encType&genreId=$encGenre&mediaType=$mediaType")
                            },
                            isTv = true
                        )
                    }
                    composable("category_view_all?title={title}&categoryType={categoryType}&genreId={genreId}&mediaType={mediaType}") { backStackEntry ->
                        val rawTitle = backStackEntry.arguments?.getString("title") ?: "Catalog"
                        val title = URLDecoder.decode(rawTitle, StandardCharsets.UTF_8.toString())
                        val rawType = backStackEntry.arguments?.getString("categoryType") ?: "trending"
                        val categoryType = URLDecoder.decode(rawType, StandardCharsets.UTF_8.toString())
                        val rawGenre = backStackEntry.arguments?.getString("genreId")
                        val genreId = if (rawGenre.isNullOrBlank()) null else URLDecoder.decode(rawGenre, StandardCharsets.UTF_8.toString())
                        val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"

                        CategoryViewAllScreen(
                            title = title,
                            categoryType = categoryType,
                            genreId = genreId,
                            mediaType = mediaType,
                            onNavigateToDetails = { mediaId, type ->
                                navController.navigate("details/$mediaId?type=$type")
                            },
                            onBack = { navController.popBackStack() },
                            isTv = true
                        )
                    }
                    composable("search") {
                        SearchScreen(
                            onNavigateToDetails = { mediaId, type ->
                                navController.navigate("details/$mediaId?type=$type")
                            }
                        )
                    }
                    composable("hot") {
                        NewAndHotScreen(
                            onNavigateToDetails = { mediaId, type ->
                                navController.navigate("details/$mediaId?type=$type")
                            }
                        )
                    }
                    composable("downloads") {
                        DownloadsScreen(
                            onPlayDownload = { localPath ->
                                val encodedPath = URLEncoder.encode(localPath, StandardCharsets.UTF_8.toString())
                                navController.navigate("player?url=$encodedPath")
                            }
                        )
                    }
                    composable("details/{mediaId}?type={type}") { backStackEntry ->
                        val mediaId = backStackEntry.arguments?.getString("mediaId")?.toIntOrNull() ?: 0
                        val type = backStackEntry.arguments?.getString("type")
                        DetailsScreen(
                            mediaId = mediaId,
                            mediaType = type,
                            onNavigateToPlayer = { url ->
                                val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                                navController.navigate("player?url=$encodedUrl&mediaId=$mediaId&mediaType=${type ?: "movie"}")
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "player?url={url}&mediaId={mediaId}&mediaType={mediaType}&title={title}&poster={poster}",
                        arguments = listOf(
                            androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType },
                            androidx.navigation.navArgument("mediaId") { type = androidx.navigation.NavType.IntType; defaultValue = 0 },
                            androidx.navigation.navArgument("mediaType") { type = androidx.navigation.NavType.StringType; defaultValue = "movie" },
                            androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
                            androidx.navigation.navArgument("poster") { type = androidx.navigation.NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val url = backStackEntry.arguments?.getString("url") ?: ""
                        val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
                        val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"
                        val title = backStackEntry.arguments?.getString("title") ?: ""
                        val poster = backStackEntry.arguments?.getString("poster") ?: ""

                        val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
                        val decodedTitle = if (title.isNotBlank()) URLDecoder.decode(title, StandardCharsets.UTF_8.toString()) else ""
                        val decodedPoster = if (poster.isNotBlank()) URLDecoder.decode(poster, StandardCharsets.UTF_8.toString()) else null

                        PlayerScreen(
                            videoUrl = decodedUrl,
                            mediaId = mediaId,
                            mediaType = mediaType,
                            title = decodedTitle,
                            posterPath = decodedPoster,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("player?url={url}") { backStackEntry ->
                        val url = backStackEntry.arguments?.getString("url") ?: ""
                        val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
                        PlayerScreen(
                            videoUrl = decodedUrl,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Sleek in-app popup notification banner
                if (!isPlayerRoute) {
                    InAppNotificationBanner(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    )
                }
            }
        }
    } else {
        // ----------------------------------------------------------------------
        // MOBILE SHELL (SCAFFOLD WITH BOTTOM BAR)
        // ----------------------------------------------------------------------
        val showBottomBar = currentRoute in bottomNavRoutes && !isInPipMode

        Scaffold(
            containerColor = Color(0xFF070707),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = Color(0xF5101010),
                        contentColor = Color.White,
                        modifier = Modifier.border(BorderStroke(0.5.dp, Color(0x18FFFFFF)))
                    ) {
                        // Home Tab
                        NavigationBarItem(
                            selected = currentRoute == BottomNavItem.Home.route,
                            onClick = {
                                navController.navigate(BottomNavItem.Home.route) {
                                    popUpTo(BottomNavItem.Home.route) { inclusive = true }
                                }
                            },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFE50914),
                                selectedTextColor = Color(0xFFE50914),
                                unselectedIconColor = Color(0xFF888888),
                                unselectedTextColor = Color(0xFF888888),
                                indicatorColor = Color(0x22E50914)
                            )
                        )

                        // Search Tab
                        NavigationBarItem(
                            selected = currentRoute == BottomNavItem.Search.route,
                            onClick = {
                                navController.navigate(BottomNavItem.Search.route) {
                                    popUpTo(BottomNavItem.Home.route)
                                }
                            },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFE50914),
                                selectedTextColor = Color(0xFFE50914),
                                unselectedIconColor = Color(0xFF888888),
                                unselectedTextColor = Color(0xFF888888),
                                indicatorColor = Color(0x22E50914)
                            )
                        )

                        // New & Hot Tab (Netflix iconic tab)
                        NavigationBarItem(
                            selected = currentRoute == BottomNavItem.HotAndNew.route,
                            onClick = {
                                navController.navigate(BottomNavItem.HotAndNew.route) {
                                    popUpTo(BottomNavItem.Home.route)
                                }
                            },
                            icon = { Icon(Icons.Default.Whatshot, contentDescription = "New & Hot") },
                            label = { Text("New & Hot", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFE50914),
                                selectedTextColor = Color(0xFFE50914),
                                unselectedIconColor = Color(0xFF888888),
                                unselectedTextColor = Color(0xFF888888),
                                indicatorColor = Color(0x22E50914)
                            )
                        )

                        // Downloads Tab
                        NavigationBarItem(
                            selected = currentRoute == BottomNavItem.Downloads.route,
                            onClick = {
                                navController.navigate(BottomNavItem.Downloads.route) {
                                    popUpTo(BottomNavItem.Home.route)
                                }
                            },
                            icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
                            label = { Text("Downloads", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFE50914),
                                selectedTextColor = Color(0xFFE50914),
                                unselectedIconColor = Color(0xFF888888),
                                unselectedTextColor = Color(0xFF888888),
                                indicatorColor = Color(0x22E50914)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onNavigateToDetails = { mediaId, type ->
                                navController.navigate("details/$mediaId?type=$type")
                            },
                            onNavigateToSearch = {
                                navController.navigate("search")
                            },
                            onNavigateToCategory = { title, categoryType, genreId, mediaType ->
                                val encTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                                val encType = URLEncoder.encode(categoryType, StandardCharsets.UTF_8.toString())
                                val encGenre = URLEncoder.encode(genreId ?: "", StandardCharsets.UTF_8.toString())
                                navController.navigate("category_view_all?title=$encTitle&categoryType=$encType&genreId=$encGenre&mediaType=$mediaType")
                            },
                            isTv = false
                        )
                    }
                    composable("category_view_all?title={title}&categoryType={categoryType}&genreId={genreId}&mediaType={mediaType}") { backStackEntry ->
                        val rawTitle = backStackEntry.arguments?.getString("title") ?: "Catalog"
                        val title = URLDecoder.decode(rawTitle, StandardCharsets.UTF_8.toString())
                        val rawType = backStackEntry.arguments?.getString("categoryType") ?: "trending"
                        val categoryType = URLDecoder.decode(rawType, StandardCharsets.UTF_8.toString())
                        val rawGenre = backStackEntry.arguments?.getString("genreId")
                        val genreId = if (rawGenre.isNullOrBlank()) null else URLDecoder.decode(rawGenre, StandardCharsets.UTF_8.toString())
                        val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"

                        CategoryViewAllScreen(
                            title = title,
                            categoryType = categoryType,
                            genreId = genreId,
                            mediaType = mediaType,
                            onNavigateToDetails = { mediaId, type ->
                                navController.navigate("details/$mediaId?type=$type")
                            },
                            onBack = { navController.popBackStack() },
                            isTv = false
                        )
                    }
                    composable("search") {
                        SearchScreen(
                            onNavigateToDetails = { mediaId, type ->
                                navController.navigate("details/$mediaId?type=$type")
                            }
                        )
                    }
                    composable("hot") {
                        NewAndHotScreen(
                            onNavigateToDetails = { mediaId, type ->
                                navController.navigate("details/$mediaId?type=$type")
                            }
                        )
                    }
                    composable("downloads") {
                        DownloadsScreen(
                            onPlayDownload = { localPath ->
                                val encodedPath = URLEncoder.encode(localPath, StandardCharsets.UTF_8.toString())
                                navController.navigate("player?url=$encodedPath")
                            }
                        )
                    }
                    composable("details/{mediaId}?type={type}") { backStackEntry ->
                        val mediaId = backStackEntry.arguments?.getString("mediaId")?.toIntOrNull() ?: 0
                        val type = backStackEntry.arguments?.getString("type")
                        DetailsScreen(
                            mediaId = mediaId,
                            mediaType = type,
                            onNavigateToPlayer = { url ->
                                val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                                navController.navigate("player?url=$encodedUrl&mediaId=$mediaId&mediaType=${type ?: "movie"}")
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "player?url={url}&mediaId={mediaId}&mediaType={mediaType}&title={title}&poster={poster}",
                        arguments = listOf(
                            androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType },
                            androidx.navigation.navArgument("mediaId") { type = androidx.navigation.NavType.IntType; defaultValue = 0 },
                            androidx.navigation.navArgument("mediaType") { type = androidx.navigation.NavType.StringType; defaultValue = "movie" },
                            androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
                            androidx.navigation.navArgument("poster") { type = androidx.navigation.NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val url = backStackEntry.arguments?.getString("url") ?: ""
                        val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
                        val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"
                        val title = backStackEntry.arguments?.getString("title") ?: ""
                        val poster = backStackEntry.arguments?.getString("poster") ?: ""

                        val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
                        val decodedTitle = if (title.isNotBlank()) URLDecoder.decode(title, StandardCharsets.UTF_8.toString()) else ""
                        val decodedPoster = if (poster.isNotBlank()) URLDecoder.decode(poster, StandardCharsets.UTF_8.toString()) else null

                        PlayerScreen(
                            videoUrl = decodedUrl,
                            mediaId = mediaId,
                            mediaType = mediaType,
                            title = decodedTitle,
                            posterPath = decodedPoster,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("player?url={url}") { backStackEntry ->
                        val url = backStackEntry.arguments?.getString("url") ?: ""
                        val decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
                        PlayerScreen(
                            videoUrl = decodedUrl,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Sleek in-app popup notification banner (hidden during video playback)
                if (!isPlayerRoute) {
                    InAppNotificationBanner(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    )
                }
            }
        }
    }
}
