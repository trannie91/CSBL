package com.blvietsub

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class VkExtractor : ExtractorApi() {
    override val name = "VK"
    override val mainUrl = "https://vk.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url
            .replace("&amp;", "&")
            .replace("vkvideo.ru", "vk.com")

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer" to (referer ?: "https://blvietsub.com/")
        )

        val response = try {
            app.get(cleanUrl, headers = headers).text
        } catch (e: Exception) {
            return
        }

        // 1. Quét tìm url1080 trực tiếp trong JSON/script của VK (Ưu tiên chất lượng cao nhất 1080p FHD)
        val url1080Match = Regex("""["']url1080["']\s*[:=]\s*["']([^"']+)["']""").find(response)?.groupValues?.get(1)
            ?: Regex("""\\?"url1080\\?"\s*:\s*\\?"([^"\\]+)\\?"""").find(response)?.groupValues?.get(1)

        if (!url1080Match.isNullOrEmpty()) {
            val videoUrl = url1080Match.replace("\\/", "/")
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "Server VK - 1080p FHD",
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.quality = Qualities.P1080.value
                    this.referer = "https://vk.com/"
                }
            )
            return
        }

        // 2. Tìm luồng HLS m3u8 trong VK
        val hlsMatch = Regex("""["']hls["']\s*[:=]\s*["']([^"']+)["']""").find(response)?.groupValues?.get(1)
            ?: Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(response)?.value

        if (!hlsMatch.isNullOrEmpty()) {
            val hlsUrl = hlsMatch.replace("\\/", "/")
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "Server VK - 1080p FHD",
                    url = hlsUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.quality = Qualities.P1080.value
                    this.referer = "https://vk.com/"
                }
            )
            return
        }

        // 3. Fallback: Nếu nguồn video chỉ upload tối đa 720p HD
        val url720Match = Regex("""["']url720["']\s*[:=]\s*["']([^"']+)["']""").find(response)?.groupValues?.get(1)
            ?: Regex("""\\?"url720\\?"\s*:\s*\\?"([^"\\]+)\\?"""").find(response)?.groupValues?.get(1)

        if (!url720Match.isNullOrEmpty()) {
            val videoUrl = url720Match.replace("\\/", "/")
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "Server VK - 720p HD",
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.quality = Qualities.P720.value
                    this.referer = "https://vk.com/"
                }
            )
            return
        }
    }
}
