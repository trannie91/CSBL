package com.blvietsub

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class BLVietsubProvider : MainAPI() {
    override var mainUrl = "https://blvietsub.com"
    override var name = "BLVietsub"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "" to "BLVietsub - Mới Cập Nhật",
        "category/phim-bo" to "Phim Bộ Đam Mỹ",
        "category/phim-le" to "Phim Lẻ Đam Mỹ",
        "category/thai-lan" to "BL Thái Lan",
        "category/trung-quoc" to "BL Trung Quốc",
        "category/han-quoc" to "BL Hàn Quốc",
        "category/nhat-ban" to "BL Nhật Bản",
        "category/dai-loan" to "BL Đài Loan",
        "category/hoan-tat" to "Phim Đã Hoàn Tất",
        "category/phim-doc" to "Phim Dọc"
    )

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val mapper = jacksonObjectMapper()

    data class EpisodePayload(
        val ss: String? = null,
        val dl: String? = null,
        val ok: String? = null,
        val vk: String? = null,
        val direct: String? = null
    )

    private fun extractPoster(element: Element): String {
        val img = element.selectFirst("img")
        var poster = ""

        if (img != null) {
            val candidates = listOf(
                img.attr("data-src"),
                img.attr("data-lazy-src"),
                img.attr("data-original"),
                img.attr("data-srcset"),
                img.attr("srcset"),
                img.attr("src")
            )

            for (cand in candidates) {
                val trimmed = cand.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("data:image") && !trimmed.contains("blank.gif") && !trimmed.contains("placeholder")) {
                    poster = if (trimmed.contains(",")) {
                        trimmed.split(",").lastOrNull()?.trim()?.split(" ")?.firstOrNull() ?: ""
                    } else if (trimmed.contains(" ")) {
                        trimmed.split(" ").firstOrNull() ?: ""
                    } else {
                        trimmed
                    }
                    if (poster.isNotEmpty() && !poster.startsWith("data:image")) break
                }
            }
        }

        if (poster.isEmpty() || poster.startsWith("data:image")) {
            val styleEl = element.selectFirst("[style*='background-image'], [data-bg], [data-background]")
            val bgStyle = styleEl?.attr("style") ?: ""
            val bgMatch = Regex("""url\(['"]?(.*?)['"]?\)""").find(bgStyle)?.groupValues?.get(1)
            if (!bgMatch.isNullOrEmpty()) {
                poster = bgMatch
            } else {
                poster = styleEl?.attr("data-bg")?.ifEmpty { styleEl.attr("data-background") } ?: ""
            }
        }

        if (poster.isEmpty()) return ""
        return fixUrl(poster)
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page <= 1) {
            if (request.data.isEmpty()) "$mainUrl/" else "$mainUrl/${request.data}/"
        } else {
            if (request.data.isEmpty()) "$mainUrl/page/$page/" else "$mainUrl/${request.data}/page/$page/"
        }
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        val items = doc.select("article, .item-film, .halim-item, .film-item, .post-item").mapNotNull { article ->
            toSearchResult(article)
        }.distinctBy { it.url }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    name = request.name,
                    list = items,
                    isHorizontalImages = false
                )
            ),
            hasNext = items.isNotEmpty()
        )
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val link = element.selectFirst("a[href^=https://blvietsub.com/]") 
            ?: element.selectFirst("a[href*='blvietsub.com']")
            ?: element.selectFirst("a") 
            ?: return null

        val href = fixUrl(link.attr("href"))
        val slug = href.removePrefix("https://blvietsub.com/").removePrefix("http://blvietsub.com/").trim('/')
        
        if (slug.isEmpty() || listOf("category", "tag", "actor", "page", "xmlrpc", "feed", "wp-admin", "lien-he", "privacy-policy").any { slug.startsWith(it) }) {
            return null
        }

        val title = element.selectFirst("h2, h3, h4, .entry-title, .title, .film-name")?.text()?.trim() 
            ?: link.attr("title").ifEmpty { slug.replace("-", " ") }
            
        val poster = extractPoster(element)

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = poster
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.trim().isEmpty()) return emptyList()

        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val searchUrl = "$mainUrl/?s=$encodedQuery"
        
        val doc = app.get(searchUrl, headers = mapOf(
            "User-Agent" to userAgent,
            "Referer" to "$mainUrl/"
        )).document

        val results = doc.select("article, .item-film, .halim-item, .film-item, .post-item").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }

        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        val title = doc.selectFirst("h1, .entry-title, .film-info h1")?.text()?.trim() ?: return null
        
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?.ifEmpty { doc.selectFirst("meta[name=twitter:image]")?.attr("content") }
            ?: extractPoster(doc.selectFirst("article, .film-info, .poster") ?: doc)

        val description = doc.select("article p, .film-content p, .entry-content p")
            .filter { it.text().length > 20 && !it.text().contains("Xem phim") && !it.text().contains("Server SS") && !it.text().contains("Server DL") && !it.text().contains("Server OK") && !it.text().contains("Server VK") }
            .joinToString("\n\n") { it.text().trim() }
            .ifEmpty { title }

        val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
        val tags = doc.select("article a[href*=/category/], .film-info a[href*=/category/]").map { it.text().trim() }

        // Bóc tách toàn bộ các nút server: hỗ trợ Server SS, Server DL, Server OK, Server VK (cả phim mới lẫn phim cũ)
        val episodes = mutableListOf<Episode>()
        val allServerButtons = doc.select("[data-server-url], .server-item button, .server-item a, .list-server a, .btn-episode, [data-server]")

        val epMap = linkedMapOf<Int, MutableMap<String, String>>()

        allServerButtons.forEachIndexed { index, btn ->
            val rawUrl = btn.attr("data-server-url").ifEmpty {
                btn.attr("data-url").ifEmpty {
                    btn.attr("href")
                }
            }.trim()

            val serverUrl = rawUrl.replace("&amp;", "&")

            if (serverUrl.isNotEmpty() && !serverUrl.startsWith("#") && !serverUrl.startsWith("javascript")) {
                val label = btn.attr("data-server-label").ifEmpty { btn.text().trim() }
                val numMatch = Regex("""\d+""").find(label)
                val epNum = numMatch?.value?.toIntOrNull() ?: (index + 1)

                if (!epMap.containsKey(epNum)) {
                    epMap[epNum] = mutableMapOf()
                }

                val lowerLabel = label.lowercase()
                when {
                    lowerLabel.contains("ss") || serverUrl.contains("ssplay.net") -> {
                        epMap[epNum]?.put("ss", serverUrl)
                    }
                    lowerLabel.contains("dl") || serverUrl.contains("dailymotion") -> {
                        epMap[epNum]?.put("dl", serverUrl)
                    }
                    lowerLabel.contains("ok") || serverUrl.contains("ok.ru") -> {
                        epMap[epNum]?.put("ok", serverUrl)
                    }
                    lowerLabel.contains("vk") || serverUrl.contains("vk.com") || serverUrl.contains("vkvideo.ru") -> {
                        epMap[epNum]?.put("vk", serverUrl)
                    }
                    else -> {
                        epMap[epNum]?.put("other", serverUrl)
                    }
                }
            }
        }

        if (epMap.isNotEmpty()) {
            epMap.entries.sortedBy { it.key }.forEach { (epNum, servers) ->
                val payload = EpisodePayload(
                    ss = servers["ss"],
                    dl = servers["dl"],
                    ok = servers["ok"],
                    vk = servers["vk"],
                    direct = servers["other"] ?: servers["ss"] ?: servers["dl"] ?: servers["ok"] ?: servers["vk"]
                )
                val dataJson = mapper.writeValueAsString(payload)

                val epName = if (epMap.size == 1 && (title.contains("FULL", ignoreCase = true) || !title.contains("Tập", ignoreCase = true))) {
                    "Full Movie (1080p FHD)"
                } else {
                    "Tập $epNum"
                }

                episodes.add(
                    newEpisode(dataJson) {
                        this.name = epName
                        this.episode = epNum
                    }
                )
            }
        } else {
            val iframes = doc.select("article iframe, .film-player iframe")
            iframes.forEachIndexed { index, iframe ->
                val src = iframe.attr("src").trim().replace("&amp;", "&")
                if (src.isNotEmpty()) {
                    val payload = mapper.writeValueAsString(EpisodePayload(direct = src))
                    episodes.add(
                        newEpisode(payload) {
                            this.name = "Tập ${index + 1}"
                            this.episode = index + 1
                        }
                    )
                }
            }
        }

        val distinctEpisodes = episodes.distinctBy { it.data }.sortedBy { it.episode }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, distinctEpisodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.trim().startsWith("{")) {
            try {
                val payload = mapper.readValue<EpisodePayload>(data)

                // 1. Nạp Server SS: SSPlay 1080p FHD (cho phim mới có SSPlay)
                if (!payload.ss.isNullOrEmpty()) {
                    val ssExtractor = SSPlayExtractor()
                    ssExtractor.getUrl(payload.ss, "$mainUrl/", subtitleCallback, callback)
                }

                // 2. Nạp Server DL: Dailymotion hoặc OK.ru 1080p FHD (cho phim mới có DL)
                if (!payload.dl.isNullOrEmpty()) {
                    val dlUrl = payload.dl
                    when {
                        dlUrl.contains("ok.ru") -> {
                            val okExtractor = OkRuExtractor()
                            okExtractor.getUrl(dlUrl, "$mainUrl/", subtitleCallback, callback)
                        }
                        dlUrl.contains("dailymotion") -> {
                            val dmExtractor = DailymotionExtractor()
                            dmExtractor.getUrl(dlUrl, "$mainUrl/", subtitleCallback, callback)
                        }
                        else -> {
                            loadExtractor(dlUrl, "$mainUrl/", subtitleCallback, callback)
                        }
                    }
                }

                // 3. Nạp Server OK: OK.ru 1080p FHD (cho phim cũ & mới có Server OK)
                if (!payload.ok.isNullOrEmpty() && payload.ok != payload.dl) {
                    val okExtractor = OkRuExtractor()
                    okExtractor.getUrl(payload.ok, "$mainUrl/", subtitleCallback, callback)
                }

                // 4. Nạp Server VK: VK 1080p FHD (cho phim cũ có Server VK)
                if (!payload.vk.isNullOrEmpty()) {
                    val vkExtractor = VkExtractor()
                    vkExtractor.getUrl(payload.vk, "$mainUrl/", subtitleCallback, callback)
                }

                // 5. Nếu không có cả 4 nguồn trên, thử link direct dự phòng
                if (payload.ss.isNullOrEmpty() && payload.dl.isNullOrEmpty() && payload.ok.isNullOrEmpty() && payload.vk.isNullOrEmpty() && !payload.direct.isNullOrEmpty()) {
                    return loadDirectLink(payload.direct, subtitleCallback, callback)
                }

                return true
            } catch (e: Exception) {}
        }

        return loadDirectLink(data, subtitleCallback, callback)
    }

    private suspend fun loadDirectLink(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val clean = url.replace("&amp;", "&")
        when {
            clean.contains("ssplay.net") || clean.contains("blvietsub") -> {
                val ssExtractor = SSPlayExtractor()
                ssExtractor.getUrl(clean, "$mainUrl/", subtitleCallback, callback)
                return true
            }
            clean.contains("ok.ru") -> {
                val okExtractor = OkRuExtractor()
                okExtractor.getUrl(clean, "$mainUrl/", subtitleCallback, callback)
                return true
            }
            clean.contains("vk.com") || clean.contains("vkvideo.ru") -> {
                val vkExtractor = VkExtractor()
                vkExtractor.getUrl(clean, "$mainUrl/", subtitleCallback, callback)
                return true
            }
            clean.contains("dailymotion") -> {
                val dmExtractor = DailymotionExtractor()
                dmExtractor.getUrl(clean, "$mainUrl/", subtitleCallback, callback)
                return true
            }
            else -> {
                return loadExtractor(clean, "$mainUrl/", subtitleCallback, callback)
            }
        }
    }
}
