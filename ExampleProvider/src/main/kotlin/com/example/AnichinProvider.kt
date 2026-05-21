package com.example

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
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

        val popularElements = document.select(".bixbox:contains(Popular Today) .listupd article.bs")
        val popularDonghua = popularElements.mapNotNull { it.toSearchResult() }
        if (popularDonghua.isNotEmpty()) {
            homePageList.add(HomePageList("Popular Today", popularDonghua))
        }

        val latestElements = document.select(".bixbox:contains(Latest Release) .listupd article.bs")
        val latestDonghua = latestElements.mapNotNull { it.toSearchResult() }
        if (latestDonghua.isNotEmpty()) {
            homePageList.add(HomePageList("Latest Release", latestDonghua))
        }

        return newHomePageResponse(homePageList)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".tt h2, h2[itemprop=headline]")?.text()?.trim() ?: return null
        val href = this.selectFirst(".bsx a, a")?.attr("href") ?: return null
        
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

        // Mengambil judul bersih dengan aman apakah ada kata "Episode" atau tidak
        val rawTitle = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val title = if (rawTitle.contains("Episode")) {
            rawTitle.replace("Subtitle Indonesia", "").split("Episode")[0].trim()
        } else {
            rawTitle.replace("Subtitle Indonesia", "").trim()
        }

        val img = document.selectFirst(".thumb img, .poster img")
        val poster = if (img != null && img.hasAttr("data-src")) img.attr("data-src") else img?.attr("src")
        val description = document.selectFirst(".entry-content, .desc")?.text()?.trim()

        val episodes = ArrayList<Episode>()
        
        // Membaca daftar rincian episode dari sidebar (.episodelist)
        val episodeElements = document.select(".episodelist ul li a")
        if (episodeElements.isNotEmpty()) {
            episodeElements.forEach { a ->
                val epHref = a.attr("href") ?: return@forEach
                val epTitle = a.selectFirst("h4")?.text()?.trim() ?: "Episode"
                
                episodes.add(newEpisode(epHref) {
                    this.name = epTitle
                })
            }
        } else {
            episodes.add(newEpisode(url) {
                this.name = "Tonton"
            })
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            // Mengurutkan dari episode terkecil/lama ke episode terbaru di aplikasi
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
        
        // Mendekripsi server video terenkripsi Base64 dari elemen dropdown mirror
        val options = document.select("select.mirror option")
        options.forEach { option ->
            val base64Value = option.attr("value")
            if (!base64Value.isNullOrEmpty()) {
                try {
                    val decodedBytes = Base64.decode(base64Value, Base64.DEFAULT)
                    val decodedHtml = String(decodedBytes, Charsets.UTF_8)
                    
                    val iframeDoc = Jsoup.parse(decodedHtml)
                    val embedUrl = iframeDoc.selectFirst("iframe")?.attr("src")
                    
                    if (!embedUrl.isNullOrEmpty() && embedUrl.startsWith("http")) {
                        loadExtractor(embedUrl, data, subtitleCallback, callback)
                    }
                } catch (e: Exception) {
                    // Abaikan server jika proses dekripsi gagal
                }
            }
        }

        // Cadangan: Ambil langsung jika iframe sudah tertanam di pemutar utama
        document.select("#embed_holder iframe, .player-embed iframe").forEach { iframe ->
            val embedUrl = iframe.attr("src")
            if (!embedUrl.isNullOrEmpty() && embedUrl.startsWith("http")) {
                loadExtractor(embedUrl, data, subtitleCallback, callback)
            }
        }

        return true
    }
}
