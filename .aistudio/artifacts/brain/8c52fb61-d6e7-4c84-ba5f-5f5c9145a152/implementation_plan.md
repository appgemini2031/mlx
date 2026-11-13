# Konsep & Arsitektur: Khatam Al-Qur'an 30 Juz Interaktif (Sedekah Huruf Per-Event)

Konsep revolusioner dan bernilai ibadah tinggi untuk siaran langsung: **Menuliskan ayat suci Al-Qur'an 30 Juz huruf demi huruf (*Typewriter Effect*) secara realtime di layar kamera siaran**, di mana setiap huruf, kata, atau ayat yang terketik dipicu langsung oleh interaksi penonton (Tap Like, Chat Doa, Gift Saweran, Follow, dan Share).

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Jawaban atas Pertanyaan Pengguna: *"Bisa gak? Bagaimana konsepnya?"***
> - **SANGAT BISA!** Secara teknis, sistem ini 100% memungkinkan dan berjalan sangat ringan di Android & OpenGL Kamera Virtual.
> - **Filosofi & Daya Tarik Penonton (Viralitas Berkah)**:
>   - Mengangkat hadits Rasulullah SAW: *"Barangsiapa membaca satu huruf dari Kitab Allah, maka baginya satu kebaikan, dan satu kebaikan dilipatgandakan sepuluh..."*
>   - Penonton berlomba-lomba tap-tap like dan chat agar namanya tercatat sebagai **"Penyumbang Huruf / Pembuka Ayat"** berikutnya di layar siaran.
> - **Bobot Per-Event (Dapat Diatur Host di Floating Window)**:
>   - **1 Tap Like** ➔ Membuka **1 Huruf Arab berikutnya** (Typewriter per-huruf).
>   - **1 Chat / Doa** ➔ Membuka **1 Kata Arab** (atau 3–5 huruf sekaligus).
>   - **1 Follow / Share** ➔ Membuka **Setengah Ayat**.
>   - **1 Gift Saweran (Mawar/Koin)** ➔ Membuka **1 Ayat Penuh** + Efek Kilau Selebrasi Emas!
> - **Progres Khatam Permanen (Anti-Reset)**: Progres penulisan (Surat ke-berapa, Ayat ke-berapa, Huruf ke-berapa) tersimpan otomatis di penyimpanan lokal HP, sehingga siaran bisa dilanjutkan keesokan harinya tanpa terulang dari awal sampai tuntas 30 Juz.

---

## 1. Overview & Core Concept

- **Apa yang Ditampilkan di Layar Kamera (HUD Virtual Camera)**:
  1. **Teks Arab Kaligrafi Emas (Rasm Utsmani)**:
     - Huruf-huruf ayat Al-Qur'an terketik satu per satu dari kanan ke kiri.
     - Huruf yang sudah terbuka menyala dengan warna emas bercahaya (`#FFD700`) / hijau zamrud (`#00E676`).
     - Terdapat kursor kedip halus di huruf berikutnya yang menunggu interaksi penonton.
  2. **Info Surat & Terjemahan Indonesia**:
     - Contoh: `QS. Al-Baqarah [2]: 255 (Ayat Kursi)`
     - Terjemahan Bahasa Indonesia otomatis muncul di baris bawah setelah ayat selesai terbuka.
  3. **Penyumbang Terakhir (Donor Badge)**:
     - Badge elegan di pojok ayat: *"Huruf ke-14 oleh @Ahmad (+1 Like)"* atau *"Ayat dibuka oleh @Sultan (Gift Mawar x10)"*.
  4. **Indikator Khatam 30 Juz**:
     - Bar progres kecil: `Juz 1 • Surat 2/114 • Ayat 255 • Total 4.820 Huruf Tertulis`.
- **Target Pengguna**:
  - Siaran live streaming Mekkah/Madinah 24 jam, live sholawat, live mengaji bersama, dan akun religi yang ingin membangun komunitas penonton yang setia dan aktif berpartisipasi setiap hari.
- **Fleksibilitas Host**:
  - Bebas memilih: Mulai dari Surat Al-Fatihah (Juz 1), langsung ke Juz 30 (Juz 'Amma), atau surat pilihan harian (Al-Kahfi hari Jumat, Yasin malam Jumat, Al-Mulk sebelum tidur).

---

## 2. User Experience & Visual Design

### Alur Tampilan Siaran (Screen Flow)
```
┌─────────────────────────────────────────────────────────────┐
│  LAYAR LIVE STREAMING TIKTOK (KAMERA VIRTUAL CUANCAME)      │
│                                                             │
│       [ Video Siaran Ka'bah / Pemandangan Berjalan ]        │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 🕌 KHATAM QUR'AN BERSAMA PENONTON • JUZ 1 (AL-BAQARAH) │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │                                                       │  │
│  │     ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ ۚ ...       │  │
│  │     [═══════ Terketik 28 dari 45 Huruf ═══════] ▌     │  │
│  │                                                       │  │
│  │  📖 "Allah, tidak ada tuhan selain Dia. Yang Mahahidup"│  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ ❤️ Huruf terakhir diketik oleh: @budi_santoso (+1 Like)│  │
│  │ 🎁 Ayat dibuka penuh oleh: @hamba_allah (10 Mawar)   │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Palet Warna & Tipografi
- **Kaligrafi Arab**: Font Naskh/Amiri Uthmani lokal tajam dan jelas di resolusi live TikTok.
- **Warna Teks Arab**: Gradient Emas Mengkilap (`#FFF8E1` ke `#FFD54F`) dengan bayangan halus (drop-shadow gelap) agar terbaca sempurna di atas video apapun (terang maupun gelap).
- **Warna Kontributor Badge**: Emerald Green `#00E676` untuk like/chat, dan Royal Amber `#FFA000` untuk donatur gift.
- **Background Container**: Kartu Rounded Glassmorphism semi-transparan (`#1A1A1A` dengan transparansi 75%) yang elegan dan tidak menghalangi video latar belakang.

---

## 3. Key Product Decisions & Trade-Offs

- **Keputusan 1: Dataset Al-Qur'an 30 Juz Offline di APK (Zero-Quota)**:
  - *Pilihan*: Membundel dataset teks lengkap 6.236 ayat Al-Qur'an 30 Juz dalam format JSON terkompresi di folder `assets/quran/` (~1.5 MB).
  - *Alasan*: Menjamin pengetikan huruf 100% instan (<1ms) tanpa membutuhkan koneksi internet tambahan atau ketergantungan API luar yang bisa down atau terkena rate-limit.
- **Keputusan 2: Penanganan Teks Arab Huruf-Per-Huruf (Grapheme Cluster Safe)**:
  - *Pilihan*: Menggunakan penanganan karakter Unicode bahasa Arab yang memperhitungkan harakat (fathah, kasrah, dhommah, sukun, tasydid).
  - *Alasan*: Menghindari huruf terpotong tanpa harakat atau kerusakan ligatur sambung Arab. 1 huruf dihitung sebagai 1 konsonan beserta harakat pelengkapnya.
- **Keputusan 3: Anti-Lag Buffer & Animasi Halus**:
  - *Pilihan*: Menggunakan antrean buffer event (FIFO). Jika saat live terjadi serbuan 500 tap like dalam 1 detik, huruf-huruf akan di-render secara mengalir cepat dan mulus (*smooth rapid typewriter*) tanpa membuat GPU HP drop FPS atau macet.
- **Keputusan 4: Opsi Suara Pelengkap (Opsional)**:
  - *Pilihan*: Setiap kali 1 ayat selesai diketik penuh oleh penonton, dapat memutar potongan tilawah qari (dari modul quran audio yang sudah ada di aplikasi) atau suara denting bell selebrasi yang menyejukkan hati.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│        DEVICE 1 (Termux Event Broker: Webcast WebSocket)        │
│    Mendeteksi: Tap Like, Chat Doa, Gift Saweran, Follow, Share  │
└───────────────────────────────┬─────────────────────────────────┘
                                │ WebSocket LAN (ws://IP:8765)
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│        DEVICE 2: LiveEventReceiver.kt (Aplikasi Android)        │
└───────────────────────────────┬─────────────────────────────────┘
                                │ Event JSON masuk
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              InteractiveQuranKhatamEngine.kt                    │
│                                                                 │
│  - Membaca Surat & Ayat saat ini dari QuranDatabase             │
│  - Mengambil antrean huruf berikutnya                           │
│  - Memajukan pointer: +1 huruf (Like), +1 kata (Chat),          │
│    +1 ayat (Gift)                                               │
│  - Mencatat nama user kontributor terakhir                      │
│  - Menyimpan posisi: SharedPreferences (Surah, Ayah, CharIndex) │
└───────────────────────────────┬─────────────────────────────────┘
                                │ StateFlow<QuranLiveDisplayState>
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│               QuranOverlayBitmapGenerator.kt                    │
│                                                                 │
│  - Menggambar kaligrafi Arab, kotak terjemahan, & badge         │
│  - Merender ke Dynamic Bitmap Surface                           │
│  - Disuntikkan langsung ke GlVideoRenderer / EglSurfaceManager │
│    pada kamera virtual siaran langsung                          │
└─────────────────────────────────────────────────────────────────┘
```

### Data Model (`QuranLiveDisplayState`)
- `currentSurahNumber: Int` (1 s/d 114)
- `currentSurahNameArabic: String` (misal: "البقرة")
- `currentSurahNameLatin: String` (misal: "Al-Baqarah")
- `currentAyahNumber: Int` (nomor ayat dalam surat)
- `fullAyahArabicText: String` (teks lengkap ayat Arab)
- `fullAyahIndonesianText: String` (terjemahan ayat)
- `revealedCharsCount: Int` (jumlah huruf yang sudah terbuka)
- `totalCharsCount: Int` (total huruf pada ayat saat ini)
- `lastContributorName: String` (nama penonton yang baru tap/chat/gift)
- `lastActionDescription: String` (misal: "Huruf ke-15 via 1 Like")
- `totalLettersWrittenSession: Long` (akumulasi total huruf siaran ini)
- `isAyahCompleted: Boolean` (indikator ayat baru saja rampung)

---

## Tahapan Implementasi

1. **Dataset & Parser Al-Qur'an 30 Juz**:
   - Menyiapkan repository data ayat dan surat Al-Qur'an terstruktur (Arab Utsmani, Latin, dan Terjemahan Indonesia).
2. **Interactive Quran Typewriter Engine (`InteractiveQuranKhatamEngine.kt`)**:
   - Logika pengetikan per-huruf berbasis event TikTok.
   - Manajemen pembagian event: Like ➔ Huruf, Chat ➔ Kata, Gift ➔ Lompat Ayat.
   - Penyimpanan dan pemulihan progres (Surat, Ayat, Indeks Huruf) secara persisten.
3. **Overlay Renderer Kamera Virtual (`QuranLiveOverlayRenderer.kt`)**:
   - Pembuatan tampilan kaligrafi indah bergradasi emas di atas surface kamera virtual.
   - Animasi transisi pergantian ayat yang halus.
4. **Panel Kontrol Floating Window & Menu Utama**:
   - Tab "Khatam Qur'an" di Floating Control:
     - Saklar ON/OFF Mode Khatam Al-Qur'an.
     - Pilihan Surat Awal (Al-Fatihah, Juz 'Amma, Yasin, dll.).
     - Bobot Event (Pilih berapa like untuk 1 huruf).
     - Tombol manual: Lompat Ayat, Ulang Ayat, Reset Progres.
     - Tombol Uji Coba: Tambah 1 Huruf, Tambah 1 Kata, Selesaikan Ayat.
5. **Verifikasi Kompilasi & Pengujian Unit**:
   - Memverifikasi build dengan `compile_applet` untuk menjamin tidak ada crash pada rendering teks Arab dan performa rendering tetap 60 FPS.
