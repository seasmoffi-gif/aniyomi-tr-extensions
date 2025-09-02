package eu.kanade.tachiyomi.animeextension.tr.animecix

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response

class Animecix : AnimeHttpSource() {

    override val name = "AnimeDB"

    override val baseUrl = "https://base.vulnton.org/api/collections/anime"
    override val lang = "tr"
    override val supportsLatest = true

    private val jsonParser = Json { ignoreUnknownKeys = true }

    // --- POPULAR / LATEST ---
    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/records?page=$page&fields=title,type,synopsis,year,poster_url,id", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val body = response.body?.string() ?: return AnimesPage(emptyList(), false)
        val apiResponse = jsonParser.decodeFromString<ApiResponse>(body)

        val animes = apiResponse.items.map { item ->
            SAnime.create().apply {
                title = item.title
                thumbnail_url = item.poster_url
                setUrlWithoutDomain("$baseUrl/records?filter=(id~'${item.id}')")
            }
        }

        val hasNext = apiResponse.page < apiResponse.totalPages
        return AnimesPage(animes, hasNext)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/records?sort=-created&page=$page&fields=title,type,synopsis,year,poster_url,id", headers)

    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)

    // --- SEARCH ---
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        GET("$baseUrl/records?filter=(title='$query')&page=$page&fields=title,type,synopsis,year,poster_url,id,genres", headers)

    override fun searchAnimeParse(response: Response) = popularAnimeParse(response)

    // --- ANIME DETAILS ---
    override fun animeDetailsParse(response: Response): SAnime {
        val body = response.body?.string() ?: return SAnime.create()
        val apiResponse = jsonParser.decodeFromString<ApiResponse>(body)
        val doc = apiResponse.items.firstOrNull()

        return SAnime.create().apply {
            title = doc?.title ?: ""
            description = doc?.synopsis ?: ""
            genre = doc?.genres ?: ""
            thumbnail_url = doc?.poster_url ?: ""
            status = SAnime.UNKNOWN
        }
    }

    // --- EPISODES ---
    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = response.asJsoup()
        return doc.select(".episode-list li").mapIndexed { idx, element ->
            SEpisode.create().apply {
                episode_number = idx + 1F
                name = element.selectFirst("a")?.text() ?: "Episode ${idx + 1}"
                setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
            }
        }.reversed()
    }

    // --- VIDEOS ---
    override fun videoListParse(response: Response): List<Video> {
        val doc = response.asJsoup()
        return doc.select("iframe").mapNotNull { element ->
            val videoUrl = element.attr("abs:src")
            if (videoUrl.isNotBlank()) Video(videoUrl, "Default", videoUrl) else null
        }
    }
}

// --- JSON DATA CLASSES ---
@Serializable
data class ApiResponse(
    val page: Int,
    val totalPages: Int,
    val items: List<AnimeData>,
)

@Serializable
data class AnimeData(
    val title: String,
    val type: String,
    val synopsis: String,
    val year: Int,
    val id: String,
    val genres: String? = null,
    val poster_url: String,
)

@Serializable
data class EpisodeData(
    val title_name: String,
    val title_id: Int,
    val title_poster: String,
)

@Serializable
data class VideoData(
    val url: String,
    val quality: String,
)
