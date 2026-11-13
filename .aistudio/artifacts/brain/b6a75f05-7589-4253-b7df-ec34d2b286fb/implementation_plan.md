# Pemindahan Analitik ke Floating Window Host & Banner Progres Al-Qur'an Penuh

Menutup tampilan metrik analitik siaran live dari kamera publik karena bersifat khusus konsumsi host, memindahkan dashboard analitik live ke Floating Control khusus layar host, dan menata ulang banner atas kartu Al-Qur'an menjadi banner progres sisa khataman 30 juz berformat penuh (full-width) yang rapi, simetris, dan elegan.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Keputusan Terkonfirmasi Berdasarkan Pilihan Pengguna:**
> - **Penutupan Kolom Analitik Kamera**: Kolom analitik (penonton, like, gift, diamond, chat, join, follow, share) ditutup total dari banner atas kartu Al-Qur'an agar siaran live murni fokus pada ayat suci dan penonton tidak terganggu oleh data statistik internal siaran.
> - **Banner Progres Al-Qur'an Penuh (Full-Width)**: Ruang atas kartu Al-Qur'an dialokasikan 100% untuk informasi Progres & Sisa Khataman 30 Juz secara proporsional dan terstruktur (Surah/Juz di header tengah, sisa huruf & ayat di baris pertama, sisa surat & sisa juz di baris kedua).
> - **Pemindahan ke Floating Window Khusus Host**: Host tetap memerlukan pemantauan interaksi penonton secara real-time. Oleh karena itu, metrik analitik live dipindahkan menjadi widget "📊 Dashboard Analitik Host" di dalam Floating Control (Tab Event), yang hanya tampil di layar fisik HP host dan tidak dirender ke Virtual Camera / siaran TikTok.
> - **Default Saklar Kamera OFF**: Opsi saklar analitik kamera diatur default mati (OFF), namun tombol kontrol tetap dipertahankan di Floating Control jika host sewaktu-waktu membutuhkan tampilan analitik di kamera.

---

### 1. Overview & Core Concept

- **Apa yang Dilakukan**:
  1. Di layer rendering kamera (`OverlayBannerBitmapGenerator.kt`), modul `generateUnifiedTopDashboardBitmap` diubah menjadi `generateQuranProgressDashboardBitmap`: menghapus kolom kiri analitik dan memformat kolom progres menjadi 1 panel penuh (full-width) yang proporsional, berlatar semi-transparan dengan aksen border emas/cyan lembut.
  2. Di Floating Window (`LiveCommentTabBuilder.kt`), menambahkan modul kartu analitik host yang membaca data `LiveEventReceiver` (viewers, likes, chats, gifts, diamonds, joins, follows, shares) dengan kartu ringkas berlatar gelap, font terbaca jelas, dan indikator status koneksi LAN.
  3. Mengatur konfigurasi default `enableEventStatsOverlay = false` di `LiveEventModels.kt` dan `LiveEventPreferences.kt`.
- **Target Pengguna**: Host live streaming TikTok yang ingin menjaga siaran tetap bersih dan sakral, namun tetap memiliki visibilitas penuh atas performa interaksi penonton melalui floating control.
- **Nilai Manfaat**: Siaran tampak profesional dan tidak berantakan, privasi data statistik host terjaga, dan tampilan kartu Al-Qur'an menjadi lebih lega dan khusyuk.

---

### 2. User Experience & Visual Design

#### A. Tampilan Kamera Siaran (Penonton)
- **Sebelum**: Banner atas kartu Al-Qur'an terbelah dua (50% kiri teks padat Analitik Live, 50% kanan Progres Al-Qur'an terhimpit).
- **Sesudah**: Banner atas kartu Al-Qur'an menjadi **Satu Kesatuan Panel Progres Penuh (Full-Width)**:
  ```text
  ┌────────────────────────────────────────────────────────────────────────┐
  │                   📖 QS. Al-Baqarah 2:185/286 • Juz 2                   │
  ├────────────────────────────────────────────────────────────────────────┤
  │         🔤 89.420 Huruf Tersisa    •    📜 1.024 Ayat Tersisa          │
  │         🕌 112 Surat Menuju Khatam  •    ✨ 28 Juz Menuju Khatam        │
  └────────────────────────────────────────────────────────────────────────┘
  ```
  - Warna aksen: Emas (`#FFD54F`), Emerald Green (`#34D399`), dan Cyan (`#00E5FF`).
  - Latar: Deep Navy semi-transparan (`#EB0B1322`) dengan sudut membulat rapi (`11dp`).
  - Bebas dari angka-angka metrik analitik yang tidak relevan bagi penonton.

#### B. Tampilan Layar HP Host (Floating Window - Tab Event)
- Di dalam Floating Control Tab Event, ditambahkan komponen kartu khusus host:
  ```text
  ┌────────────────────────────────────────────────────────────────────────┐
  │ 📊 ANALITIK HOST (REAL-TIME PRIVAT)                     [🟢 TERHUBUNG] │
  ├────────────────────────────────────────────────────────────────────────┤
  │ 👥 Penonton : 1.420       ❤️ Total Like : 35.800                       │
  │ 💬 Komentar : 640         🎁 Total Gift : 85                           │
  │ 💎 Koin/Dmd : 1.250       👋 Total Join : 3.100                        │
  │ 👤 Follower : 142         🔄 Dibagikan  : 78                           │
  ├────────────────────────────────────────────────────────────────────────┤
  │ [↺ Reset Counter Sesi]               [🎛️ Sembunyikan Detail]          │
  └────────────────────────────────────────────────────────────────────────┘
  ```
  - Kartu ini hanya berada di Floating Window Android Host, tidak terekam ke kamera virtual.
  - Nilai diperbarui secara instan (<5ms) saat event masuk dari server broker.

---

### 3. Key Product Decisions & Trade-Offs

- **Keputusan 1: Full-Width Quran Progress Banner**:
  - *Pendekatan*: Mengubah tata letak banner atas menjadi full-width single card yang terpusat (centered hierarchy) daripada mempertahankan pembagian 2-kolom yang salah satunya kosong.
  - *Alasan*: Menghindari ruang kosong ganjil di sisi kiri, memperbesar ukuran teks progres agar mudah dibaca di layar HP penonton kecil, dan memberikan proporsi visual yang seimbang di atas kartu ayat.
- **Keputusan 2: Floating Window Metric Cards**:
  - *Pendekatan*: Menempatkan metrik analitik di bagian atas Tab Event Floating Control dengan layout grid 2 kolom / 4 baris yang padat dan terorganisir.
  - *Alasan*: Host dapat melihat perkembangan viewers dan like kapan saja tanpa perlu membuka dashboard live TikTok yang memakan layar penuh.
- **Keputusan 3: Kompatibilitas Flag & Default Setting**:
  - *Pendekatan*: Menjadikan `enableEventStatsOverlay = false` secara default di data model dan preferensi.
  - *Alasan*: Memastikan pengguna baru maupun sesi live baru langsung mendapatkan tampilan bebas analitik di kamera secara otomatis tanpa konfigurasi manual.

---

### 4. Technical Architecture & Data Strategy

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                    Device 1 (Termux TikTok Broker)                      │
│            Webcast Live Event Stream (Viewers, Likes, Gifts)            │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ WebSocket LAN (<5ms)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               Device 2: LiveEventReceiver & Preferences                │
├────────────────────────────────────┬────────────────────────────────────┤
│                                    │                                    │
│  [Jalur Layar Privat Host]          │  [Jalur Kamera Siaran Publik]      │
│  Floating Window (Tab Event)       │  GlVideoRenderer & Bitmaps         │
│  ┌──────────────────────────────┐  │  ┌──────────────────────────────┐  │
│  │ 📊 Panel Analitik Host       │  │  │ 📖 Full-Width Progress       │  │
│  │ • Viewers, Likes, Gifts      │  │  │ • Surat & Juz Title          │  │
│  │ • Diamonds, Joins, Follows   │  │  │ • Sisa Huruf, Ayat, Juz      │  │
│  │ • Tombol Reset Sesi          │  │  │ • (Bebas Angka Analitik)     │  │
│  └──────────────────────────────┘  │  └──────────────┬───────────────┘  │
│                                    │                 │                  │
│                                    │                 ▼                  │
│                                    │  ┌──────────────────────────────┐  │
│                                    │  │ 🕌 Kartu Ayat Al-Qur'an      │  │
│                                    │  │ • Teks Arab Rasm Utsmani     │  │
│                                    │  │ • Terjemahan & Kontributor   │  │
│                                    │  └──────────────────────────────┘  │
└────────────────────────────────────┴────────────────────────────────────┘
```

#### Alur Modifikasi Kode:
1. **`OverlayBannerBitmapGenerator.kt`**:
   - Refactor fungsi `generateUnifiedTopDashboardBitmap` menjadi banner satu kolom lebar penuh yang fokus pada judul Surat, Juz, dan rincian Sisa Huruf, Ayat, Surat, Juz dengan visual typography elegan.
   - Perbarui integrasi pemanggil di `generateQuranKhatamBitmap` agar menggunakan layout progres penuh yang bebas dari data analitik.
2. **`LiveCommentTabBuilder.kt`**:
   - Tambahkan section `HostAnalyticsCard` di bagian atas Tab Event Floating Control dengan pembacaan langsung dari state flow `LiveEventReceiver` (viewers, likes, gifts, diamonds, comments, joins, follows, shares).
   - Sediakan tombol refresh / reset counter sesi live.
3. **`LiveEventModels.kt` & `LiveEventPreferences.kt`**:
   - Pastikan nilai default `enableEventStatsOverlay` adalah `false`.
4. **Verifikasi & Build**:
   - Jalankan unit tests yang relevan (`LiveEventBrokerTest.kt`, `PhotoStudioAndQuranTest.kt`) untuk memastikan integrasi data tetap 100% lulus tanpa regresi.
   - Jalankan `compile_applet` untuk memastikan build APK bersih dan siap pakai.
