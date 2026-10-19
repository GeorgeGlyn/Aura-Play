package com.creativemedialabs.auraplay.feature.remote.server

import android.util.Log
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class PlaybackRequest(
    val videoUrl: String,
    val title: String,
    val tmdbId: Int
)

class TvCompanionServer @Inject constructor() {

    private var server: NettyApplicationEngine? = null

    // Callback to pass the parsed playback request to the UI/Player
    var onPlaybackRequested: ((PlaybackRequest) -> Unit)? = null

    fun start(port: Int = 8080) {
        if (server != null) return

        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                post("/play") {
                    try {
                        val request = call.receive<PlaybackRequest>()
                        onPlaybackRequested?.invoke(request)
                        call.respond(mapOf("status" to "success"))
                    } catch (e: Exception) {
                        Log.e("TvCompanionServer", "Error parsing payload", e)
                        call.respond(mapOf("status" to "error", "message" to e.localizedMessage))
                    }
                }
            }
        }.start(wait = false)
        Log.d("TvCompanionServer", "Server started on port $port")
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        Log.d("TvCompanionServer", "Server stopped")
    }
}
