package com.creativemedialabs.auraplay.core.network.cache

import android.content.Context
import com.creativemedialabs.auraplay.core.network.model.TmdbMediaItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CachedHomeCatalog(
    val spotlightItem: TmdbMediaItem? = null,
    val trendingItems: List<TmdbMediaItem> = emptyList(),
    val top10Movies: List<TmdbMediaItem> = emptyList(),
    val popularMovies: List<TmdbMediaItem> = emptyList(),
    val popularTvShows: List<TmdbMediaItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class CatalogCache @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val cacheFile = File(context.filesDir, "cached_home_catalog.json")

    @Volatile
    private var memoryCache: CachedHomeCatalog? = null

    fun getCachedCatalog(): CachedHomeCatalog? {
        memoryCache?.let { return it }

        return try {
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val type = object : TypeToken<CachedHomeCatalog>() {}.type
                val cached: CachedHomeCatalog? = gson.fromJson(json, type)
                if (cached != null && cached.trendingItems.isNotEmpty()) {
                    memoryCache = cached
                    cached
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveCatalog(catalog: CachedHomeCatalog) {
        memoryCache = catalog
        try {
            val json = gson.toJson(catalog)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            // Ignore disk cache write failure
        }
    }
}
