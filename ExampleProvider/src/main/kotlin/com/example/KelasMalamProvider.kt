package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.MovieLoadResponse
import org.jsoup.nodes.Element

class KelasMalamProvider : MainAPI() {
    override var mainUrl = "https://kelasmalam.net"
    override var name = "Kelas Malam"
    override val hasMainPage = true
    override var lang = "id"
    // Menggunakan TvType.Movies karena ini adalah platform video/streaming terpisah
    override val supportedTypes = setOf(TvType.Movie)

    // 1. FUNGSI HALAMAN UTAMA (HOME PAGE)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page == 1) mainUrl else "$mainUrl/page/$page/"
        val document = app.get(url).document
        
        // Mengambil semua elemen artikel video di dalam container list utama
        val videoElements = document.select("main#main .videos-list article.loop-video")
        
        val homeItems = videoElements.mapNotNull { element ->
            element.toSearchResult()
        }

        return newHomePageResponse(
            listOf(HomePageList("Latest Videos", homeItems)),
            hasNext = document.selectFirst("div.pagination a:contains(Next)") != null
        )
    }

    // 2. FUNGSI PENCARIAN (SEARCH)
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        
        return document.select("main#main .videos-list article.loop-video").mapNotNull { element ->
            element.toSearchResult()
        }
    }

    // 3. HELPER EXTENSION UNTUK MEMBACA DOM ELEMENT
    private fun Element.toSearchResult(): SearchResponse? {
        // Mengambil link tujuan video dari tag <a>
        val linkElement = this.selectFirst("a") ?: return null
        val href = linkElement.attr("href")
        
        // Mengambil judul dari span di dalam entry-header atau attribute title
        val title = linkElement.selectFirst(".entry-header span")?.text() 
            ?: linkElement.attr("title")
            ?: "No Title"
            
        // Mengambil poster dari data-src atau src asli di image thumbnail
        val imgElement = linkElement.selectFirst("img.video-main-thumb")
        val posterUrl = imgElement?.attr("data-src")?.ifEmpty { imgElement.attr("src") } 
            ?: this.attr("data-main-thumb")

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    // 4. DETAIL HALAMAN VIDEO (LOAD DATA)
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        // fallback jika di halaman dalam menggunakan struktur h1 standard wordpress
        val title = document.selectFirst("h1.entry-title")?.text() 
            ?: document.selectFirst("h1")?.text() 
            ?: return null

        val posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")

        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.Movie,
            dataUrl = url,
            posterUrl = posterUrl,
            plot = null
        )
    }

   // 5. EKSTRAKSI VIDEO PLAYER LINK (LOAD LINKS)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        // Melakukan request ke halaman post untuk mengambil DOM HTML
        val document = app.get(data).document

        // Mengambil semua elemen server content (.server-content)
        val serverElements = document.select(".video-servers .server-content")

        for (server in serverElements) {
            // Mengambil isi dari atribut data-embed yang berisi string iframe
            val embedData = server.attr("data-embed")
            
            if (embedData.isNotEmpty()) {
                // Regex untuk mengekstrak URL di dalam atribut src="..." dari data-embed
                val srcRegex = """src=\\"(https://[^"\s]+)\\"""".toRegex()
                val matchResult = srcRegex.find(embedData)
                
                // Jika URL ditemukan, bersihkan karakter backslash (\/) menjadi slash biasa (/)
                val serverUrl = matchResult?.groups?.get(1)?.value?.replace("\\/", "/")
                
                if (!serverUrl.isNullOrEmpty()) {
                    // Memuat URL server ke masing-masing extractor otomatis (Doodstream, Streamtape, dll)
                    loadExtractor(serverUrl, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
