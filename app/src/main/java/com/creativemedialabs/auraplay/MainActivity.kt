package com.creativemedialabs.auraplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.creativemedialabs.auraplay.feature.details.ui.DetailsScreen
import com.creativemedialabs.auraplay.feature.downloads.ui.DownloadsScreen
import com.creativemedialabs.auraplay.feature.home.ui.HomeScreen
import com.creativemedialabs.auraplay.feature.home.ui.NewAndHotScreen
import com.creativemedialabs.auraplay.feature.home.ui.SearchScreen
import com.creativemedialabs.auraplay.feature.player.ui.PlayerScreen
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class BottomNavItem(val route: String, val label: String) {
    object Home : BottomNavItem("home", "Home")
    object Search : BottomNavItem("search", "Search")
    object HotAndNew : BottomNavItem("hot", "New & Hot")
    object Downloads : BottomNavItem("downloads", "Downloads")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFE50914),
                    background = Color(0xFF070707),
                    surface = Color(0xFF121212),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                AuraPlayApp()
            }
        }
    }
}

@Composable
private fun AuraPlayApp() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val bottomNavRoutes = listOf(
        BottomNavItem.Home.route,
        BottomNavItem.Search.route,
        BottomNavItem.HotAndNew.route,
        BottomNavItem.Downloads.route
    )
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        containerColor = Color(0xFF070707),
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
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        onNavigateToDetails = { mediaId ->
                            navController.navigate("details/$mediaId")
                        },
                        onNavigateToSearch = {
                            navController.navigate("search")
                        }
                    )
                }
                composable("search") {
                    SearchScreen(
                        onNavigateToDetails = { mediaId ->
                            navController.navigate("details/$mediaId")
                        }
                    )
                }
                composable("hot") {
                    NewAndHotScreen(
                        onNavigateToDetails = { mediaId ->
                            navController.navigate("details/$mediaId")
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
                composable("details/{mediaId}") { backStackEntry ->
                    val mediaId = backStackEntry.arguments?.getString("mediaId")?.toIntOrNull() ?: 0
                    DetailsScreen(
                        mediaId = mediaId,
                        onNavigateToPlayer = { url ->
                            val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                            navController.navigate("player?url=$encodedUrl")
                        },
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
        }
    }
}
