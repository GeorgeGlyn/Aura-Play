package com.creativemedialabs.auraplay.core.player.exo

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@OptIn(UnstableApi::class)
class ExoPlayerWrapper(context: Context) {

    private val dataSourceFactory = DefaultDataSource.Factory(context)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()

    fun prepareAndPlay(
        videoUrl: String,
        subtitleUrl: String? = null,
        preferredAudioLanguage: String? = null
    ) {
        val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)

        // Attach external subtitle track (.srt / .vtt) if present
        if (!subtitleUrl.isNullOrEmpty()) {
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                .setMimeType(if (subtitleUrl.endsWith(".srt", ignoreCase = true)) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        // Configure multi-track audio preference (e.g. English, Tamil, Hindi)
        if (!preferredAudioLanguage.isNullOrEmpty()) {
            val params = player.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage(preferredAudioLanguage)
                .build()
            player.trackSelectionParameters = params
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.playWhenReady = true
    }

    fun setAudioLanguage(languageCode: String?) {
        val params = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(languageCode)
            .build()
        player.trackSelectionParameters = params
    }

    fun release() {
        player.release()
    }
}
