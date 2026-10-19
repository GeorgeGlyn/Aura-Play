package com.creativemedialabs.auraplay.core.network.resolver

/**
 * Represents a resolved stream including video url, optional subtitle, and audio tracks.
 */
data class ResolvedStream(
    val videoUrl: String,
    val isDrmProtected: Boolean = false,
    val subtitleUrl: String? = null,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Abstraction layer for stream resolvers/scrapers.
 */
interface MediaResolver {
    suspend fun resolveStream(tmdbId: Int, mediaType: String): Result<ResolvedStream>
}

/**
 * A mock implementation using public domain test videos.
 */
class MockMediaResolver : MediaResolver {
    override suspend fun resolveStream(tmdbId: Int, mediaType: String): Result<ResolvedStream> {
        // Return a public test video (Big Buck Bunny)
        return Result.success(
            ResolvedStream(
                videoUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
            )
        )
    }
}
