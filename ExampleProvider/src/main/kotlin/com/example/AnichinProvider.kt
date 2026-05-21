package com.example // Sesuaikan dengan package name proyek Anda

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.ArrayList

class AnichinProvider : MainAPI() {
    override var mainUrl = "https://anichin.cafe" // Ganti jika domain utama berubah (ex: anichin.team) sebagai alternativ
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    // Konfigurasi Tab Menu Utama di Aplikasi Cloudstream
    override val mainPage = mainPageOf(
        Pair("latest", "Latest Release"),
        Pair("popular", "Popular Today"),
        Pair("movie", "New Movie")
    )

    // 1. MEMROSES HALAMAN UTAMA BERDASARKAN TAB YANG DIPILIH
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        // Mendukung pagination jika pengguna men-scroll ke bawah (/?page=2)
        val url = if (page > 1) "$mainUrl/page/$page/" else mainUrl
        val document = app.get(url).document
        val homeItems = ArrayList<SearchResponse>()

        val selector = when (request.data) {
            "latest" -> "div.block:contains(Latest Release) div.listupd div.bs"
            "popular" -> "div.block:contains(Popular Today) div.listupd div.bs"
            "movie" -> "div.block:contains(NEW MOVIE) div.listupd div.bs, div.block:contains(NEW MOVIE) div.flw-item" 
            else -> "div.listupd div.bs"
        }

        document.select(selector).forEach { element ->
            val searchResult = element.toSearchResult()
            if (searchResult != null) {
                homeItems.add(searchResult)
            }
        }

        return NewHomePageResponse(request.name, homeItems, hasNext = true)
    }

    // Elemen Parser Global dari HTML ke Format Object Cloudstream
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.tt, h2, .film-name a")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        
        // Menangani lazyload image atau source gambar standar
        val posterUrl = this.selectFirst("img")?.let { img ->
            if (img.hasAttr("data-src")) img.attr("data-src") else img.attr("src")
        }

        return NewShowSearchResponse(title, href, this@AnichinProvider.name, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    // 2. PROSES FITUR PENCARIAN DONGHUA
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        
        return document.select("div.listupd div.bs, div.sorandbx div.bs").mapNotNull {
            it.toSearchResult()
        }
    }

    // 3. MENGAMBIL INFORMASI DETAIL & DAFTAR EPISODE
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title, .entry-title")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.thumb img, .poster img")?.let {
            if (it.hasAttr("data-src")) it.attr("data-src") else it.attr("src")
        }
        val description = document.selectFirst("div.entry-content[itemprop=description], .entry-content")?.text()?.trim()
        val rating = document.selectFirst(".rating strong, .imdb rating")?.text()?.trim()?.toRatingInt()

        val episodes = ArrayList<Episode>()

        // Mengurai daftar episode jika struktur menggunakan tabel/list standar (.eplister)
        val episodeElements = document.select("div.eplister ul li, .listeps ul li")
        if (episodeElements.isNotEmpty()) {
            episodeElements.forEach { li ->
                val epHref = li.selectFirst("a")?.attr("href") ?: return@forEach
                val epTitle = li.selectFirst("div.epl-num, .epnum")?.text() ?: "Episode"
                
                episodes.add(Episode(epHref, epTitle))
            }
        } else {
            // Fallback jika halaman berupa single post video langsung (biasanya tipe Movie)
            episodes.add(Episode(url, "Tonton Movie"))
        }

        // Membalikkan urutan list agar Episode 1 berada di urutan paling atas
        val orderedEpisodes = episodes.reversed()

        return NewShowLoadResponse(title, url, this.name, TvType.Anime, orderedEpisodes) {
            this.posterUrl = poster
            this.plot = description
            this.rating = rating
        }
    }

    // 4. MENGEKSTRAK LINK STREAMING VIDEO (PLAYER MIRROR)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Mengambil semua opsi server mirror video yang ada di dalam combobox/select option
        val mirrors = document.select("select.mirror option, ul.mirrorist li a, .player-embed iframe")
        
        if (mirrors.isEmpty()) {
            // Mengambil langsung dari iframe default jika tidak ada menu pilihan server
            document.select("iframe[src]").forEach { iframe ->
                val embedUrl = iframe.attr("src")
                loadExtractor(embedUrl, data, subtitleCallback, callback)
            }
        } else {
            mirrors.forEach { mirror ->
                val embedUrl = if (mirror.tagName() == "option") {
                    mirror.attr("value")
                } else {
                    mirror.attr("data-embed") ?: mirror.attr("href")
                }

                if (embedUrl.isNotEmpty() && embedUrl.startsWith("http")) {
                    // loadExtractor secara otomatis mencocokkan skrip decryptor bawaan Cloudstream 
                    // seperti Mp4Upload, Krakenfiles, Filemoon, Sendcm, dll.
                    loadExtractor(embedUrl, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}
