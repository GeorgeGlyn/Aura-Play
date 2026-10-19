package com.creativemedialabs.auraplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.creativemedialabs.auraplay.feature.details.ui.DetailsScreen
import com.creativemedialabs.auraplay.feature.home.ui.TvHomeScreen
import com.creativemedialabs.auraplay.feature.player.ui.PlayerScreen
import com.creativemedialabs.auraplay.feature.remote.client.NsdClient
import com.creativemedialabs.auraplay.feature.remote.server.TvCompanionServer
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalTvMaterial3Api::class)
class TvMainActivity : ComponentActivity() {

    @Inject
    lateinit var tvCompanionServer: TvCompanionServer

    @Inject
    lateinit var nsdClient: NsdClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start TV companion server and announce service on local network via NSD
        val tvPort = 8080
        tvCompanionServer.start(tvPort)
        nsdClient.registerService(tvPort)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()

                    // Handle mobile push to TV companion cast
                    remember {
                        tvCompanionServer.onPlaybackRequested = { request ->
                            runOnUiThread {
                                val encodedUrl = URLEncoder.encode(request.videoUrl, StandardCharsets.UTF_8.toString())
                                navController.navigate("player?url=$encodedUrl")
                            }
                        }
                        true
                    }

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            TvHomeScreen(
                                onNavigateToDetails = { mediaId ->
                                    navController.navigate("details/$mediaId")
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
                                }
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
    }

    override fun onDestroy() {
        super.onDestroy()
        tvCompanionServer.stop()
        nsdClient.unregisterService()
    }
}
