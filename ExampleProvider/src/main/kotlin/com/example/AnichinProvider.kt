package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.util.ArrayList

class AnichinProvider : MainAPI() {
    override var mainUrl = "https://anichin.cafe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    // Menggunakan struktur halaman utama standar versi stabil
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page > 1) "$mainUrl/page/$page/" else mainUrl
        val document = app.get(url).document
        val homePageList = ArrayList<HomePageList>()

        // Mengambil daftar Donghua Terbaru
        val latestElements = document.select("div.block:contains(Latest Release) div.listupd div.bs")
        val latestDonghua = latestElements.mapNotNull { it.toSearchResult() }
        if (latestDonghua.isNotEmpty()) {
            homePageList.add(HomePageList("Latest Release", latestDonghua))
        }

        // Mengambil daftar Donghua Populer
        val popularElements = document.select("div.block:contains(Popular Today) div.listupd div.bs")
        val popularDonghua = popularElements.mapNotNull { it.toSearchResult() }
        if (popularDonghua.isNotEmpty()) {
            homePageList.add(HomePageList("Popular Today", popularDonghua))
        }

        return HomePageResponse(homePageList)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.tt, h2, .film-name a")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        
        val img = this.selectFirst("img")
        val poster = if (img != null && img.hasAttr("data-src")) img.attr("data-src") else img?.attr("src")

        // Menggunakan AnimeSearchResponse standar yang kompatibel
        return AnimeSearchResponse(
            title,
            href,
            this@AnichinProvider.name,
            TvType.Anime,
            poster,
            null,
            null
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        
        return document.select("div.listupd div.bs, div.sorandbx div.bs").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title, .entry-title")?.text()?.trim() ?: return null
        val img = document.selectFirst("div.thumb img, .poster img")
        val poster = if (img != null && img.hasAttr("data-src")) img.attr("data-src") else img?.attr("src")
        val description = document.selectFirst("div.entry-content[itemprop=description], .entry-content")?.text()?.trim()

        val episodes = ArrayList<Episode>()
        val episodeElements = document.select("div.eplister ul li, .listeps ul li")
        
        if (episodeElements.isNotEmpty()) {
            episodeElements.forEach { li ->
                val epHref = li.selectFirst("a")?.attr("href") ?: return@forEach
                val epTitle = li.selectFirst("div.epl-num, .epnum")?.text() ?: "Episode"
                
                // Menggunakan constructor Episode dasar tanpa parameter deprecated
                episodes.add(Episode(epHref, epTitle))
            }
        } else {
            episodes.add(Episode(url, "Tonton Movie"))
        }

        // Menggunakan AnimeLoadResponse standar versi stabil
        return AnimeLoadResponse(
            title,
            title,
            url,
            this.name,
            TvType.Anime,
            poster,
            episodes.reversed(),
            description,
            null,
            null
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val mirrors = document.select("select.mirror option, ul.mirrorist li a, .player-embed iframe")
        
        if (mirrors.isEmpty()) {
            document.select("iframe[src]").forEach { iframe ->
                val embedUrl = iframe.attr("src")
                if (embedUrl.isNotEmpty()) {
                    loadExtractor(embedUrl, data, subtitleCallback, callback)
                }
            }
        } else {
            mirrors.forEach { mirror ->
                val embedUrl = if (mirror.tagName() == "option") {
                    mirror.attr("value")
                } else {
                    val dataEmbed = mirror.attr("data-embed")
                    if (dataEmbed.isNotEmpty()) dataEmbed else mirror.attr("href")
                }

                if (embedUrl.isNotEmpty() && embedUrl.startsWith("http")) {
                    loadExtractor(embedUrl, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
