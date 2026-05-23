dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Gunakan angka bulat untuk versi rilis plugin Anda
version = 1

cloudstream {
      
    description = "Ekstensi resmi untuk menonton video di Kelas Malam"
    authors = listOf("vstreamstack")

    /**
    * Status kode:
    * 0: Down
    * 1: Ok (Aktif)
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1

    // Menentukan jenis konten yang disediakan (Anime/Donghua)
    tvTypes = listOf("Movie")

    requiresResources = true
    language = "id"

    // Ikon opsional (Bisa Anda ganti nanti jika punya URL gambar sendiri)
    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
