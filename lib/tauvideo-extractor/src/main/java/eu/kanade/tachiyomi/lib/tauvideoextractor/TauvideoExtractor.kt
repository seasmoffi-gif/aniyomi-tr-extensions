package eu.kanade.tachiyomi.lib.tauvideoextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.*
import kotlinx.serialization.json.*

class TauvideoExtractor(private val client: OkHttpClient) {
    private val documentHeaders by lazy {
        headers.newBuilder().add("referer", "https://animecix.tv").build()
    }

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val mainUrl = "https://tau-video.xyz"
        val videoKey = url.split("/").last()
        val refererHeader = Headers.headersOf("referer", "https://animecix.tv")
        val videoUrl = "$mainUrl/api/video/$videoKey"

        val api = client.newCall(GET(url, documentHeaders)).execute().use { response ->
            if (!response.isSuccessful) throw ErrorLoadingException("TauVideo")
            val body = response.body?.string() ?: throw ErrorLoadingException("TauVideo")
            Json.decodeFromString<TauVideoUrls>(body)
        }

        val videoList = mutableListOf<Video>()
        for (video in api.urls) {
            videoList.add(
                Video(
                    video.url,
                    "TauVideo ${video.label}",
                    video.url,
                    headers = refererHeader
                )
            )
        }
        return videoList
    }

    @Serializable
    data class TauVideoUrls(
        val urls: List<TauVideoData>
    )

    @Serializable
    data class TauVideoData(
        val url: String,
        val label: String
    )
}

// Hata sınıfını da ekleyelim
class ErrorLoadingException(source: String) : Exception("Error loading from $source")
