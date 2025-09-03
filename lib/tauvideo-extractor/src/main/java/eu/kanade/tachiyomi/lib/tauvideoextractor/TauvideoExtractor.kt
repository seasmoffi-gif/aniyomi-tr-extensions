package eu.kanade.tachiyomi.lib.tauvideoextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.*
import kotlinx.serialization.json.*

class TauvideoExtractor(private val client: OkHttpClient) {
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val documentHeaders: Headers = Headers.Builder().add("referer", "https://animecix.tv").build()

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val mainUrl = "https://tau-video.xyz"
        val videoKey = url.split("/").last()
        val refererHeader = Headers.headersOf("referer", "https://animecix.tv")
        val videoUrl = "$mainUrl/api/video/$videoKey"

        val api = client.newCall(GET(url)).execute()
        val subBody = api.body?.string() ?: return emptyList()
        val streamsResponse = jsonParser.decodeFromString<TauVideoUrls>(subBody)
        val videoList = mutableListOf<Video>()
        streamsResponse.urls?.map { item ->
            videoList.add(
                Video(
                    item.url,
                    "TauVideo ${item.label}",
                    item.url,
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
