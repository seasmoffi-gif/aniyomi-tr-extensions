package eu.kanade.tachiyomi.animeextension.tr.animecix

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.lib.filemoonextractor.FilemoonExtractor
import eu.kanade.tachiyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.sendvidextractor.SendvidExtractor
import eu.kanade.tachiyomi.lib.sibnetextractor.SibnetExtractor
import eu.kanade.tachiyomi.lib.streamtapeextractor.StreamTapeExtractor
import eu.kanade.tachiyomi.lib.tauvideoextractor.TauvideoExtractor
import eu.kanade.tachiyomi.lib.uqloadextractor.UqloadExtractor
import eu.kanade.tachiyomi.lib.voeextractor.VoeExtractor
import eu.kanade.tachiyomi.lib.youruploadextractor.YourUploadExtractor
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response

class Animecix : AnimeHttpSource() {

    override val name = "AnimeDB"
    override val baseUrl = "https://base.vulnton.org"
    override val lang = "tr"
    override val supportsLatest = true

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val doodExtractor by lazy { DoodExtractor(client) }
    private val filemoonExtractor by lazy { FilemoonExtractor(client) }
    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client) }
    private val okruExtractor by lazy { OkruExtractor(client) }
    private val sendvidExtractor by lazy { SendvidExtractor(client, headers) }
    private val sibnetExtractor by lazy { SibnetExtractor(client) }
    private val tauvideoExtractor by lazy { TauvideoExtractor(client) }
    private val streamtapeExtractor by lazy { StreamTapeExtractor(client) }
    private val uqloadExtractor by lazy { UqloadExtractor(client) }
    private val voeExtractor by lazy { VoeExtractor(client, headers) }
    private val yourUploadExtractor by lazy { YourUploadExtractor(client) }

    // --- POPULAR / LATEST ---
    override fun popularAnimeRequest(page: Int): Request =
        GET(
            "$baseUrl/api/collections/anime/records?page=$page&fields=title,type,synopsis,year,poster_url,id",
            headers,
        )

    override fun popularAnimeParse(response: Response): AnimesPage {
        val body = response.body?.string() ?: return AnimesPage(emptyList(), false)
        val apiResponse = jsonParser.decodeFromString<ApiResponse>(body)

        val animes = apiResponse.items.map { item ->
            SAnime.create().apply {
                title = item.title
                thumbnail_url = item.poster_url
                setUrlWithoutDomain(
                    "$baseUrl/api/collections/anime/records?filter=(id='${item.id}')",
                )
            }
        }

        val hasNext = apiResponse.page < apiResponse.totalPages
        return AnimesPage(animes, hasNext)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET(
            "$baseUrl/api/collections/anime/records?sort=-created&page=$page&fields=title,type,synopsis,year,poster_url,id",
            headers,
        )

    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)

    // --- SEARCH ---
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        GET(
            "$baseUrl/api/collections/anime/records?filter=(title='$query')&page=$page&fields=title,type,synopsis,year,poster_url,id,genres",
            headers,
        )

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
        val body = response.body?.string() ?: return emptyList()
        val apiResponse = jsonParser.decodeFromString<ApiResponse>(body)
        val doc = apiResponse.items.firstOrNull() ?: return emptyList()

        val subData = client.newCall(
            GET("$baseUrl/api/collections/videos/records?filter=(anime_id='${doc.id}')"),
        ).execute()

        val subBody = subData.body?.string() ?: return emptyList()
        val streamsResponse = jsonParser.decodeFromString<StreamsData>(subBody)

        return streamsResponse.items?.map { item ->
            SEpisode.create().apply {
                episode_number = (item.episode ?: 0).toFloat()
                name = "Episode ${item.episode ?: "?"}"
                setUrlWithoutDomain(
                    "$baseUrl/api/collections/videos/records?filter=(anime_id='${doc.id}' && episode=${item.episode})",
                )
            }
        }?.reversed() ?: emptyList()
    }

    private fun getVideosFromUrl(firstUrl: String): List<Video> {
        val url = noRedirectClient.newCall(GET(firstUrl, headers)).execute()
            .use { it.headers["location"] }
            ?: return emptyList()

        return when {
            "filemoon.sx" in url -> filemoonExtractor.videosFromUrl(url, headers = headers)
            "sendvid.com" in url -> sendvidExtractor.videosFromUrl(url)
            "video.sibnet" in url -> sibnetExtractor.videosFromUrl(url)
            "mp4upload" in url -> mp4uploadExtractor.videosFromUrl(url, headers)
            "ok.ru" in url || "odnoklassniki.ru" in url -> okruExtractor.videosFromUrl(url)
            "yourupload" in url -> yourUploadExtractor.videoFromUrl(url, headers)
            "streamtape" in url -> streamtapeExtractor.videoFromUrl(url)?.let(::listOf)
            "dood" in url -> doodExtractor.videoFromUrl(url)?.let(::listOf)
            "uqload" in url -> uqloadExtractor.videosFromUrl(url)
            "voe.sx" in url -> voeExtractor.videosFromUrl(url)
            "tau-video.xyz" in url -> tauvideoExtractor.videosFromUrl(url)
            else -> null
        } ?: emptyList()
    }

    // --- VIDEOS ---
    override fun videoListParse(response: Response): List<Video> {
        val subBody = response.body?.string() ?: return emptyList()
        val streamsResponse = jsonParser.decodeFromString<StreamsData>(subBody)

        return streamsResponse.items?.flatMap { item ->
            item.url?.let { url ->
                getVideosFromUrl(url).map {
                    Video(
                        it.url,
                        "[${item.fansub ?: "Fansub"}] ${it.quality}",
                        it.videoUrl,
                        it.headers,
                        it.subtitleTracks,
                        it.audioTracks,
                    )
                }
            } ?: emptyList()
        } ?: emptyList()
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
    val streams: StreamsData? = null,
    val poster_url: String,
)

@Serializable
data class StreamsData(
    val items: List<EpisodeData>?,
)

@Serializable
data class EpisodeData(
    val episode: Int?,
    val fansub: String?,
    val url: String?,
)
