package eu.kanade.tachiyomi.lib.tauvideoextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.serialization.*
import kotlinx.serialization.json.*

class TauvideoExtractor(private val client: OkHttpClient) {

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val documentHeaders: Headers = Headers.Builder()
        .add("referer", "https://animecix.tv")
        .build()

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val mainUrl = "https://tau-video.xyz"
        val videoKey = url.split("/").last()
        val refererHeader = Headers.headersOf("referer", "https://animecix.tv")
        val videoUrl = "$mainUrl/api/video/$videoKey"

        val apiResponse = client.newCall(GET(videoUrl)).execute()
        val subBody = apiResponse.body?.string() ?: return emptyList()

        // JSON parse ve hata yönetimi
        val streamsResponse: TauVideoUrls? = try {
            jsonParser.decodeFromString<TauVideoUrls>(subBody)
        } catch (e: Exception) {
            // Hata ve raw response'u sunucuya gönder (opsiyonel)
            try {
                val reportJson = """
                    {
                        "error": "${e.message}",
                        "response": "${subBody.replace("\"", "\\\"")}",
                        "url": "$url"
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url("https://bot.yusiqo.com/log/log.php")
                    .post(okhttp3.RequestBody.create("application/json".toMediaType(), reportJson))
                    .build()

                client.newCall(request).execute()
            } catch (_: Exception) { /* Sunucuya gönderme hatası göz ardı edilir */ }

            return emptyList()
        }

        // Video listesini oluştur
        val videoList = mutableListOf<Video>()
        streamsResponse?.urls?.forEach { item: TauVideoData ->
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

// Hata sınıfı (opsiyonel)
class ErrorLoadingException(source: String) : Exception("Error loading from $source")