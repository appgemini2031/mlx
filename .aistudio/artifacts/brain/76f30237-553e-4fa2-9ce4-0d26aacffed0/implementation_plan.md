# Upgrade Fitur Khataman: Mode Per-Ayat 3-Zona (Zona Aman Tengah) & Sinkronisasi Suara Imam Per-Ayat

Pembaruan ini meningkatkan fitur **Khatam Al-Qur'an 30 Juz** yang sudah ada di aplikasi agar mendukung **Mode Tayangan Per-Ayat (Frame Bagi 3 / Zona Aman Tengah)** yang disinkronkan langsung dengan **Audio Imam Per-Ayat (`SSSAAA.mp3`)**, transisi gulir naik ke atas yang mulus (*smooth vertical roll*), sambil **tetap mempertahankan seluruh sistem Progres Khatam 30 Juz, Doa Penonton, dan Kontribusi Event Termux** yang sudah ada.

## Perbandingan: Fitur Khataman Saat Ini vs. Pembaruan Per-Ayat 3-Zona

> [!IMPORTANT]
> Berikut adalah perbedaan antara fitur Khataman yang sudah ada di kode saat ini dengan pembaruan Per-Ayat 3-Zona yang akan kita bangun. Tekan tombol **Proceed** jika Anda sudah siap untuk mulai membangun fitur ini, atau tuliskan jika ada tambahan lagi.

| Aspek | Fitur Khataman Saat Ini (Sudah Ada) | Pembaruan Mode Per-Ayat 3-Zona (Target Baru) |
| :--- | :--- | :--- |
| **Tampilan Ayat di Layar** | **Sambung Banyak Ayat (4 Baris Penuh)**: Ayat saat ini digabung terus dengan ayat-ayat berikutnya dalam 1 kotak agar selalu 4 baris (`MAX_LINES_4`). | **Murni Per-Ayat (Frame Bagi 3)**: **Zona Atas** = 1 Ayat Sebelumnya (`N-1` redup), **Zona Tengah (Zona Aman)** = **1 Ayat Aktif (`N` fokus penuh)**, **Zona Bawah** = 1 Ayat Berikutnya (`N+1` antre redup). |
| **Suara Imam / Qari** | **Per-Surat Utuh (`001.mp3`)**: Hanya memutar MP3 1 surat penuh sebagai musik latar biasa, **tidak sinkron** dengan ayat yang sedang tampil di layar. | **Per-Ayat Sinkron (`001001.mp3`, `001002.mp3`)**: Memutar audio Imam **khusus per-ayat** sesuai ayat yang sedang berada di Zona Aman Tengah. |
| **Pergerakan Huruf & Ayat** | Hanya bergerak jika ada ketukan Like/Chat/Gift penonton; saat berpindah ayat, teks langsung berganti. | **Auto-Play Mengikuti Suara Imam + Transisi Naik Halus**: Huruf emas menyala mengikuti durasi bacaan Imam, lalu ayat **bergulir naik ke atas secara mulus** (tidak patah-patah) saat Imam selesai membaca 1 ayat. |
| **Progres Khatam & Termux** | Sudah memiliki **Bar Progres Khatam 30 Juz**, **Kolom Doa Penonton**, **Tanda Kontribusi (Like/Gift)**, dan **Leaderboard**. | **Tetap Dipertahankan 100%**: Seluruh sistem Progres Khatam 30 Juz, Doa, dan Kontribusi Penonton dari Termux tetap tampil dan terhubung penuh. |

---

## 1. Overview & Core Concept

- **What It Does**: Menambahkan/menyempurnakan fitur Khatam Al-Qur'an yang sudah ada menjadi tayangan **Per-Ayat 3-Zona**. Setiap ayat ditampilkan satu per satu secara utuh (tidak digabung dengan ayat lain dalam satu paragraf), difokuskan tepat di **1/3 Zona Aman Tengah**, dibacakan oleh Imam secara per-ayat, dan ketika selesai dibaca akan **naik ke Zona Atas secara mulus** sementara ayat berikutnya naik dari Zona Bawah ke Zona Aman Tengah.
- **Preservasi Fitur Eksisting**:
  - **Bar Progres Khatam 30 Juz** (`generateUnifiedTopDashboardBitmap`: Nama Surat/Juz, Sisa Huruf, Sisa Ayat, Sisa Surat, Sisa Juz) tetap tampil.
  - **Tanda Kontribusi Penonton & Doa dari Termux** (`LiveEventReceiver` & `QuranKhatamLeaderboardTracker`: Kolom Doa Penonton di kiri, Kolom Saweran/Like di kanan, dan Footer Kontributor Huruf Terakhir) tetap aktif dan mencatat amal penonton.

---

## 2. User Experience & Visual Design

- **Pembagian 3 Zona Vertikal pada Kartu Khatam**:
  1. **Bagian Header Progres Khatam (Eksisting - Dipertahankan)**:
     - Menampilkan progres Khatam 30 Juz (`📖 QS. Nama Surat Nomor:Ayat/Total • Juz X`, `🔤 Sisa Huruf`, `📜 Sisa Ayat`, `🕌 Sisa Surat`, `✨ Sisa Juz`).
  2. **3 Zona Vertikal Ayat (Pembaruan Utama)**:
     - **Zona 1 — Atas (1/3 Atas Area Ayat)**: Menampilkan **1 Ayat Sebelumnya (`Ayat N-1`)** dengan warna redup (`25%` opasitas) dan ukuran teks lebih kecil (`80%`) sebagai jejak ayat yang baru saja selesai dibaca Imam.
     - **Zona 2 — Tengah / Zona Aman (1/3 Tengah Area Ayat)**: Fokus utama menampilkan **1 Ayat Aktif (`Ayat N`)** secara utuh dengan bingkai fokus tipis emas, teks Arab besar dan tajam, **highlight huruf emas (`#FFD700`)** yang menyala mengikuti suara Imam dari awal hingga akhir ayat, serta terjemahan Bahasa Indonesia di bawahnya.
     - **Zona 3 — Bawah (1/3 Bawah Area Ayat)**: Menampilkan **1 Ayat Berikutnya (`Ayat N+1`)** dengan warna redup (`25%` opasitas) yang mengantre untuk naik ke Zona Aman Tengah.
  3. **Bagian Bawah: Pita Doa & Tanda Kontribusi Penonton Termux (Eksisting - Dipertahankan)**:
     - **Kolom Kiri (`🤲 Doa Penonton`)**: Menampilkan doa penonton terakhir dari chat live Termux.
     - **Kolom Kanan (`🎁 Saweran & ❤️ Interaksi Terakhir`)**: Menampilkan nama penonton yang baru saja mengirim Gift atau mengetuk Like dari Termux.
     - **Baris Footer Kontributor**: Menampilkan penyumbang terakhir beserta aksinya dan total huruf amal sesi.

- **Animasi Transisi Naik ke Atas (*Smooth Upward Roll*)**:
  - Saat suara Imam selesai membacakan `Ayat N`:
    - Selama `~550ms` (`30 FPS` dengan kurva *Ease-In-Out Cubic*), posisi vertikal ketiga zona bergulir naik secara simultan:
      - `Ayat N-1` di Zona Atas bergeser keluar ke atas sambil memudar (`fade out`).
      - `Ayat N` di Zona Aman Tengah bergeser naik ke posisi Zona Atas sambil meredup menjadi `25%`.
      - `Ayat N+1` di Zona Bawah bergeser naik masuk ke tepat tengah **Zona Aman Tengah** sambil menyala terang (`100%`).
    - Begitu `Ayat N+1` tiba di Zona Aman Tengah, audio Imam untuk `Ayat N+1` langsung berbunyi dan highlight huruf emas mulai berjalan dari huruf pertama.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Pemutar Audio Imam Per-Ayat (`QuranPerAyahAudioPlayer`) dengan *Preload* Ayat Berikutnya**
  - *Chosen Approach*: Sementara `QuranAudioDownloadManager` yang lama mengunduh MP3 per-surat utuh (`001.mp3`), kita menambahkan mesin pemutar khusus **per-ayat** yang mengambil audio format `SSSAAA.mp3` (misalnya `001001.mp3` = Al-Fatihah ayat 1, `001002.mp3` = Al-Fatihah ayat 2) sesuai Imam yang dipilih dan menyimpannya di cache lokal. Saat `Ayat N` sedang diputar, `Ayat N+1` sudah di-*preload* di latar belakang sehingga perpindahan antar-ayat berlangsung tanpa jeda.
  - *Why*: Agar suara bacaan Imam benar-benar cocok 100% per-ayat dengan teks yang sedang berada di Zona Aman Tengah.

- **Decision 2: Penggabungan Mulus antara Suara Imam & Event Kontribusi Termux**
  - *Chosen Approach*: Ketika Auto-Play Imam Per-Ayat menyala, progres nyala huruf emas di Zona Aman Tengah berjalan otomatis mengikuti durasi suara Imam dari `0%` sampai `100%`. Setiap kali ada event masuk dari **Termux** (`onLikeEvent`, `onChatEvent`, `onGiftEvent`, `onFollowEvent`, `onShareEvent`), sistem tetap mencatat amal huruf penonton ke `QuranKhatamLeaderboardTracker`, menambah `totalLettersWrittenSession`, dan memperbarui tampilan **Tanda Kontribusi Penonton & Doa** di layar tanpa memotong atau melompati suara Imam yang sedang membaca.
  - *Why*: Penonton tetap melihat kontribusi Like/Gift/Doa mereka langsung tercantum di layar dan di papan peringkat, sementara bacaan Imam dan gerakan ayat naik ke atas tetap tenang, utuh per-ayat, dan tidak patah-patah.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│          FITUR EKSISTING (DIPERTAHANKAN) + PEMBARUAN PER-AYAT 3-ZONA         │
│                                                                              │
│  ┌────────────────────────────┐              ┌─────────────────────────────┐ │
│  │ LiveEventReceiver (Termux) │              │ QuranPerAyahAudioPlayer     │ │
│  │ • Like, Chat Doa, Gift,    │              │ • Audio Imam per-ayat       │ │
│  │   Follow, Share, Join      │              │   (Format SSSAAA.mp3)       │ │
│  │ • Leaderboard 4-Kategori   │              │ • Preload Ayat N+1 di cache │ │
│  └─────────────┬──────────────┘              └──────────────┬──────────────┘ │
│                │                                            │                │
│                ▼                                            ▼                │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                  InteractiveQuranKhatamEngine (Diperluas)               │ │
│  │  • Menyediakan 3 Ayat terpisah: Ayat N-1 (Atas), Ayat N (Tengah Aman),  │ │
│  │    dan Ayat N+1 (Bawah) — tidak lagi digabung menjadi 1 paragraf        │ │
│  │  • Menggerakkan highlight huruf emas mengikuti posisi ms suara Imam     │ │
│  │  • Menjalankan animasi scrollTransitionPhase (0.0 -> 1.0) saat          │ │
│  │    Imam selesai membaca 1 ayat agar teks naik ke atas dengan mulus      │ │
│  │  • Tetap menyertakan Progres Khatam 30 Juz, Doa & Kontributor Termux    │ │
│  └────────────────────────────────────┬────────────────────────────────────┘ │
└───────────────────────────────────────┼──────────────────────────────────────┘
                                        │
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│            OverlayBannerBitmapGenerator & GlVideoRenderer (VCam)             │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ 📖 BAR PROGRES KHATAM 30 JUZ (Surat/Juz • Sisa Huruf/Ayat/Surat/Juz)   │  │
│  ├────────────────────────────────────────────────────────────────────────┤  │
│  │ [1/3 ZONA ATAS]   Ayat Sebelumnya (N-1) • Redup 25% (Naik & Memudar)   │  │
│  ├────────────────────────────────────────────────────────────────────────┤  │
│  │ [1/3 ZONA TENGAH] ★ ZONA AMAN FOKUS UTAMA: 1 AYAT AKTIF (N) ★          │  │
│  │                   • Highlight Huruf Emas Berjalan Mengikuti Suara Imam │  │
│  │                   • Terjemahan Indonesia Ayat Aktif                    │  │
│  ├────────────────────────────────────────────────────────────────────────┤  │
│  │ [1/3 ZONA BAWAH]  Ayat Berikutnya (N+1) • Antrean Redup 25% (Siap Naik)│  │
│  ├───────────────────────────────────┬────────────────────────────────────┤  │
│  │ 🤲 DOA PENONTON (Termux Chat)     │ 🎁/❤️ KONTRIBUSI PENONTON (Termux) │  │
│  ├───────────────────────────────────┴────────────────────────────────────┤  │
│  │ ❤️ Penyumbang Terakhir: @Nama • Aksi Kontribusi • Total Huruf Amal     │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- **Penambahan pada `QuranKhatamState` & Engine**:
  - `perAyahThreeZoneMode: Boolean` (Default: `true`): Mengaktifkan tampilan 3-Zona Per-Ayat (Atas `N-1`, Tengah Zona Aman `N`, Bawah `N+1`) menggantikan tampilan gabung banyak ayat.
  - `imamAutoPlayEnabled: Boolean`: Mengaktifkan pembacaan otomatis per-ayat oleh Imam beserta sinkronisasi highlight huruf emas dan transisi gulir naik ke atas.
  - `selectedImamId: String`: Pilihan Qari/Imam (terhubung dengan daftar Imam Al-Qur'an).
  - `scrollTransitionPhase: Float`: Nilai interpolasi `0.0f .. 1.0f` untuk menggeser ketiga ayat naik ke atas secara halus (*sub-pixel*) saat pergantian ayat.
  - `prevVerse: QuranVerseItem?`, `currentVerse: QuranVerseItem`, `nextVerse: QuranVerseItem`: Data ayat terpisah untuk masing-masing zona.

### Interactive Component & State Mapping

1. **Sinkronisasi Audio Imam Per-Ayat (`SSSAAA.mp3`)**:
   - Saat `imamAutoPlayEnabled` diaktifkan pada Surat `S` dan Ayat `A`, `QuranPerAyahAudioPlayer` memutar audio spesifik ayat tersebut dan langsung mengunduh/menyiapkan ayat berikutnya (`A+1`) di cache.
   - Selama audio berputar, persentase posisi waktu audio (`currentPosition / duration`) menggerakkan `revealedCharCount` pada `Ayat N` di Zona Aman Tengah sehingga huruf emas menyala seirama dengan bacaan Imam.
2. **Transisi Gulir Naik Halus Antar-Ayat**:
   - Saat audio `Ayat N` selesai (atau ketika host menekan tombol *Ayat Berikut*), mesin menjalankan animasi `scrollTransitionPhase` dari `0.0f` ke `1.0f` dengan kurva *Ease-In-Out Cubic* sebelum mengaktifkan `Ayat N+1` di Zona Aman Tengah.
3. **Kontribusi Penonton dari Termux Tetap Aktif**:
   - Saat penonton mengirim Like, Chat Doa, atau Gift yang ditangkap Termux, `InteractiveQuranKhatamEngine` memperbarui kolom **Doa Penonton**, **Saweran/Interaksi Terakhir**, **Penyumbang Terakhir**, **Leaderboard 4-Kategori**, dan **Bar Progres Khatam 30 Juz** secara *real-time*.
