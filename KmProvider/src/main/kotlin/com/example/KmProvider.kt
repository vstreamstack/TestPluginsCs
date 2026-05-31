package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class KelasMalamProvider : MainAPI() {
    override var mainUrl = "https://kelasmalam.net"
    override var name = "Kelas Malam"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        val home = ArrayList<HomePageList>()
        
        // Mengambil list video dari halaman utama
        val elements = document.select("article.loop-video")
        val items = elements.mapNotNull { it.toSearchResponse() }
        
        if (items.isNotEmpty()) {
            home.add(HomePageList("Latest Videos", items))
        }
        return newHomePageResponse(home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.loop-video").mapNotNull { it.toSearchResponse() }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = this.selectFirst(".entry-header span")?.text() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        
        // Memperbaiki ekstraksi thumbnail jika menggunakan lazy-load dari plugin optimasi jejaring
        val posterUrl = this.selectFirst("article")?.attr("data-main-thumb")
            ?: this.selectFirst("img")?.attr("data-src")
            ?: this.selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val posterUrl = document.selectFirst("meta[itemprop=thumbnailUrl]")?.attr("content")
        val plot = document.selectFirst(".video-description .desc p")?.text()
        val tags = document.select(".tags-list a").map { it.text() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = posterUrl
            this.plot = plot
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // STRATEGI 1: Ambil dari elemen tab server (.video-servers)
        val serverElements = document.select(".video-servers .server-content, .server-content")
        for (server in serverElements) {
            val embedData = server.attr("data-embed")
            if (embedData.isNotEmpty()) {
                // Regex fleksibel mendukung tanda kutip biasa ataupun dengan backslash
                val srcRegex = """src=\\?"(https://[^"\s\\]+)\\?"""".toRegex()
                val matchResult = srcRegex.find(embedData)
                
                // Bersihkan escape backslash (\/) dari data JSON/HTML entities
                val serverUrl = matchResult?.groups?.get(1)?.value?.replace("\\/", "/")
                if (!serverUrl.isNullOrEmpty()) {
                    loadExtractor(serverUrl, data, subtitleCallback, callback)
                }
            }
        }

        // STRATEGI 2: Scan langsung semua iframe (Ditambahkan penanganan LiteSpeed Lazyload)
        val iframes = document.select("iframe")
        for (iframe in iframes) {
            // Dahulukan data-litespeed-src, jika kosong baru gunakan src biasa
            val src = iframe.attr("data-litespeed-src").ifEmpty { iframe.attr("src") }
            
            if (src.isNotEmpty() && src != "about:blank") {
                // Pastikan url mengarah ke server video populer agar tidak mengekstrak iklan
                if (src.contains("dood") || src.contains("streamtape") || src.contains("embed") || src.contains("player")) {
                    val cleanSrc = src.replace("\\/", "/")
                    loadExtractor(cleanSrc, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
