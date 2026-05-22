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

        return处 HomePageResponse(home, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.loop-video").mapNotNull { 
            it.toSearchResponse() 
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = this.selectFirst(".entry-header span")?.text() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img")?.attr("data-src") 
            ?: this.selectFirst("img")?.attr("src")

        return MovieSearchResponse(
            title,
            href,
            this@KelasMalamProvider.name,
            TvType.Movie,
            posterUrl,
            null
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val posterUrl = document.selectFirst("meta[itemprop=thumbnailUrl]")?.attr("content")
        val plot = document.selectFirst(".video-description .desc p")?.text()

        val tags = document.select(".tags-list a").map { it.text() }

        // Menggunakan newMovieLoadResponse untuk menghindari error deprecated constructor
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
        // Mengambil seluruh kontainer server video baik yang aktif maupun tersembunyi
        val serverElements = document.select(".video-servers .server-content")

        for (server in serverElements) {
            val embedData = server.attr("data-embed")
            
            if (embedData.isNotEmpty()) {
                // Regex untuk menangkap URL di dalam tag src iframe yang ter-escape
                val srcRegex = """src=\\"(https://[^"\s]+)\\"""".toRegex()
                val matchResult = srcRegex.find(embedData)
                
                // Menghapus backslash (\/) dari URL agar menjadi alamat yang valid
                val serverUrl = matchResult?.groups?.get(1)?.value?.replace("\\/", "/")
                
                if (!serverUrl.isNullOrEmpty()) {
                    // loadExtractor secara otomatis mengenali Doodstream, Streamtape, dll.
                    loadExtractor(serverUrl, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
