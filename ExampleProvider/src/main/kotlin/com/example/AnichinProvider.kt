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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page > 1) "$mainUrl/page/$page/" else mainUrl
        val document = app.get(url).document
        val homePageList = ArrayList<HomePageList>()

        // 1. Mengambil section Popular Today berdasarkan struktur bixbox terbaru
        val popularElements = document.select(".bixbox:contains(Popular Today) .listupd article.bs")
        val popularDonghua = popularElements.mapNotNull { it.toSearchResult() }
        if (popularDonghua.isNotEmpty()) {
            homePageList.add(HomePageList("Popular Today", popularDonghua))
        }

        // 2. Mengambil section Latest Release berdasarkan struktur bixbox terbaru
        val latestElements = document.select(".bixbox:contains(Latest Release) .listupd article.bs")
        val latestDonghua = latestElements.mapNotNull { it.toSearchResult() }
        if (latestDonghua.isNotEmpty()) {
            homePageList.add(HomePageList("Latest Release", latestDonghua))
        }

        return newHomePageResponse(homePageList)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Berdasarkan HTML, judul utama berada di dalam tag h2 milik class .tt
        val title = this.selectFirst(".tt h2, h2[itemprop=headline]")?.text()?.trim() ?: return null
        // Tautan detail donghua berada di elemen anchor pertama dalam .bsx
        val href = this.selectFirst(".bsx a, a")?.attr("href") ?: return null
        
        // Poster gambar menggunakan src standar WordPress
        val img = this.selectFirst("img")
        val poster = if (img != null && img.hasAttr("data-src")) img.attr("data-src") else img?.attr("src")

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        
        return document.select(".listupd article.bs").mapNotNull {
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
                
                episodes.add(newEpisode(epHref) {
                    this.name = epTitle
                })
            }
        } else {
            episodes.add(newEpisode(url) {
                this.name = "Tonton Movie"
            })
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            addEpisodes(DubStatus.Subbed, episodes.reversed())
        }
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
