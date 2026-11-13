# Sistem Leaderboard Terpadu 4 Kategori Khatam Al-Qur'an (Tampil Bersamaan Tanpa Bergantian)

Revisi rencana berdasarkan arahan pengguna: **Meniadakan sistem rotasi bergantian**. Keempat kategori Top 3 kini **ditampilkan secara serentak/bersamaan (Simultan)** di layar siaran sehingga penonton tidak perlu menunggu giliran waktu untuk melihat nama mereka di papan peringkat:
1. **Top 3 Penulis Al-Qur'an** (Juz/Khatam yang diselesaikan)
2. **Top 3 Penulis Surat** (Surat yang diselesaikan)
3. **Top 3 Penulis Ayat** (Ayat yang diselesaikan)
4. **Top 3 Penulis Huruf** (Huruf yang dituliskan)

Format mendukung **Horizontal (4 Kolom Kategori Sejajar)** dan **Vertikal (4 Seksi Bertingkat)**, lengkap dengan saklar on/off individual untuk tiap kategori jika host hanya ingin menampilkan 1, 2, 3, atau ke-4 kategori sekaligus.

---

## User Review & Keputusan Kritis

> [!IMPORTANT]
> **Keputusan Utama Revisi**:
> 1. **Tampil Bersamaan (Zero-Waiting)**: Sistem rotasi bergantian dihapus total. Ke-4 kategori tampil secara bersamaan dalam satu kesatuan papan peringkat yang harmonis dan rapi.
> 2. **Layout Dua Mode**:
>    - **Mode Horizontal**: 4 kolom kategori berdampingan (Kolom 1: Al-Qur'an | Kolom 2: Surat | Kolom 3: Ayat | Kolom 4: Huruf). Masing-masing kolom memuat Rank #1, #2, #3.
>    - **Mode Vertikal**: 4 baris/seksi bertingkat dari atas ke bawah.
> 3. **Granular Switches (Fleksibilitas Penuh)**: Disediakan saklar on/off untuk tiap kategori (`[Qur'an]`, `[Surat]`, `[Ayat]`, `[Huruf]`). Jika salah satu dimatikan, sisa kategori yang aktif akan otomatis melebar memenuhi ruang secara proporsional.
> 4. **Penyimpanan Permanen**: Data skor tersimpan aman di SharedPreferences dan tidak hilang saat aplikasi ditutup, dengan tombol **Reset Data** manual di menu.

---

## 1. Overview & Konsep Utama

- **Mengapa Harus Tampil Bersamaan?**:
  - Penonton di live streaming memiliki rentang atensi singkat (*attention span*). Menunggu 15-30 detik hanya untuk melihat nama mereka di rotasi kategori tertentu sering membuat penonton kehilangan antusiasme atau merasa kontribusinya tidak tercatat.
  - Tampilan serentak memberikan kesan kompetisi ibadah yang hidup dan transparan: penonton di kolom Huruf dapat melihat peluang untuk naik ke papan Ayat, Surat, hingga Al-Qur'an secara langsung.
- **Keseimbangan Tata Letak**:
  - Papan peringkat dirancang padat, anggun, dan proporsional (density-aware) menggunakan lencana kompak (badge emas/perak/perunggu, foto profil bulat mini, nama ringkas, dan angka capaian) sehingga tidak menghalangi video utama.

---

## 2. Pengalaman Pengguna & Desain Visual (UX & Design)

### Visual Tampilan di Kamera Virtual:

```
MODE HORIZONTAL (4 Kolom Kategori Berdampingan):
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ 🏆 PAPAN PERINGKAT PENULIS KHATAM AL-QUR'AN 30 JUZ                                                     │
├────────────────────────────┬────────────────────────────┬────────────────────────────┬─────────────────┤
│ 🕌 TOP 3 AL-QUR'AN         │ 📖 TOP 3 SURAT             │ 📜 TOP 3 AYAT              │ 🔤 TOP 3 HURUF  │
├────────────────────────────┼────────────────────────────┼────────────────────────────┼─────────────────┤
│ 🥇 #1 [Foto] Sultan • 2Juz │ 🥇 #1 [Foto] Ahmad • 8Srt  │ 🥇 #1 [Foto] Siti • 45Ayat │ 🥇 #1 Budi•1250H│
│ 🥈 #2 [Foto] Fatimah• 1Juz │ 🥈 #2 [Foto] Siti  • 5Srt  │ 🥈 #2 [Foto] Budi • 32Ayat │ 🥈 #2 Siti•980H │
│ 🥉 #3 [Foto] Hamba  • 1Juz │ 🥉 #3 [Foto] Budi  • 3Srt  │ 🥉 #3 [Foto] Umar • 20Ayat │ 🥉 #3 Ali •720H │
└────────────────────────────┴────────────────────────────┴────────────────────────────┴─────────────────┘

MODE VERTIKAL (4 Seksi Bertingkat):
┌──────────────────────────────────────────────────────────────┐
│ 🏆 PAPAN PERINGKAT PENULIS KHATAMAN                          │
├──────────────────────────────────────────────────────────────┤
│ 🕌 TOP AL-QUR'AN : 🥇 Sultan (2 Juz) 🥈 Fatimah 🥉 Hamba      │
│ 📖 TOP SURAT     : 🥇 Ahmad (8 Srt) 🥈 Siti (5 Srt) 🥉 Budi  │
│ 📜 TOP AYAT      : 🥇 Siti (45 Ayat) 🥈 Budi 🥉 Umar         │
│ 🔤 TOP HURUF     : 🥇 Budi (1,250 H) 🥈 Siti 🥉 Ali          │
└──────────────────────────────────────────────────────────────┘
```

- **Elemen Desain Visual**:
  - Palet Islami Elegan: Latar semi-transparan `#EB0B1322`, border aksen toska `#00E5FF` / emas `#FFD700`.
  - Peringkat 1: Medali Emas 🥇 (`#FACC15`)
  - Peringkat 2: Medali Perak 🥈 (`#E2E8F0`)
  - Peringkat 3: Medali Perunggu 🥉 (`#F97316`)
  - Avatar Penonton: Foto bulat mini dengan border cincin peringkat.

### Panel Kontrol di Floating Window (Tab Notif / Event):

```
┌───────────────────────────────────────────────────────────────┐
│ 🏆 LEADERBOARD PENULIS KHATAMAN (4 KATEGORI SERENTAK)         │
│ [ SAKLAR UTAMA: ON / OFF ]                                    │
│ Status: Tampil Bersamaan (4 Kategori) | Orientasi: Horiz      │
├───────────────────────────────────────────────────────────────┤
│ Orientasi: [ ↔️ Horizontal (4 Kolom) ]  [ ↕️ Vertikal (4 Seksi) ]│
│ Kategori Aktif:                                               │
│ [☑️ Al-Qur'an]   [☑️ Surat]   [☑️ Ayat]   [☑️ Huruf]          │
│ Tipografi & Skala:                                            │
│ [➖/➕ Skala]  [➖/➕ Font Latin]  [➖/➕ Font Angka]  [↺ Reset]  │
│ Posisi: [ ⬅️ ] [ ⬆️ ] [ ⬇️ ] [ ➡️ ]                             │
│ Tindakan: [ 🗑️ Reset Data Peringkat ]                         │
└───────────────────────────────────────────────────────────────┘
```

---

## 3. Keputusan Teknis & Arsitektur Sistem

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      LiveEventReceiver (TikTok Webcast)                     │
│  - Menerima event Like, Chat Doa, dan Gift secara realtime                  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     InteractiveQuranKhatamEngine                            │
│  - recordLetters(name, count, avatar)                                       │
│  - recordAyahCompleted(name, avatar)                                        │
│  - recordSurahCompleted(name, avatar)                                       │
│  - recordJuzCompleted(name, avatar)                                         │
│  - Memperbarui SharedPreferences secara permanen                            │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│               QuranKhatamLeaderboardTracker (Penyimpanan & Ranking)         │
│  - Memelihara map kontributor dengan 4 metrik independen                    │
│  - Mengurutkan Top 3 untuk masing-masing kategori secara serentak           │
│  - Menyusun unified payload 4-in-1 (Qur'an, Surat, Ayat, Huruf)             │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                 OverlayBannerBitmapGenerator                                │
│  - generateQuranLeaderboardUnifiedBitmap(...)                               │
│  - Menggambar ke-4 kategori sekaligus tanpa rotasi timer                    │
│  - Horizontal: membagi lebar kanvas menjadi 4 kolom bergaris pemisah rapi   │
│  - Vertikal: membagi tinggi kanvas menjadi 4 baris bertingkat               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Langkah Implementasi (Execution Plan)

### Langkah 1: Model Data & Tracker 4 Kategori Permanen (`QuranKhatamLeaderboardTracker`)
- Model `QuranContributorStats`:
  - `senderName: String`
  - `avatarUrlOrB64: String`
  - `juzCompleted: Int`
  - `surahCompleted: Int`
  - `ayahCompleted: Int`
  - `lettersWritten: Long`
- Class `QuranKhatamLeaderboardTracker`:
  - Penyimpanan permanen di `quran_leaderboard_prefs`.
  - Fungsi agregasi:
    - `recordLetters(sender, count, avatar)`
    - `recordAyahCompleted(sender, avatar)`
    - `recordSurahCompleted(sender, avatar)`
    - `recordJuzCompleted(sender, avatar)`
    - `resetAllData()`
  - Fungsi serializer unified:
    - `buildUnified4CategoryPayload(activeCategoriesMask: Int): String`
    - Format: `QURAN:::top3Data|||SURAH:::top3Data|||AYAH:::top3Data|||HURUF:::top3Data`

### Langkah 2: Integrasi ke Lifecycle Ayat di `InteractiveQuranKhatamEngine`
- Panggil `recordLetters` saat grapheme huruf Arab diselesaikan.
- Panggil `recordAyahCompleted` saat ayat selesai.
- Panggil `recordSurahCompleted` saat nomor surat bertambah.
- Panggil `recordJuzCompleted` saat nomor juz bertambah.
- Siarkan banner `QURAN_LEADERBOARD` langsung ke overlay setiap ada pembaruan capaian.

### Langkah 3: Pengaturan Konfigurasi (`LiveEventPreferences` & IPC)
- Simpan setelan:
  - `quranLeaderboardEnabled: Boolean = true`
  - `quranLeaderboardOrientation: String = "HORIZONTAL"` ("HORIZONTAL" vs "VERTICAL")
  - `showTopQuran: Boolean = true`
  - `showTopSurah: Boolean = true`
  - `showTopAyah: Boolean = true`
  - `showTopHuruf: Boolean = true`
  - `quranLeaderboardScale: Float = 1.0f`
  - `quranLeaderboardLatinFontSp: Float = 11.5f`
  - `quranLeaderboardNumberFontSp: Float = 12.0f`

### Langkah 4: Desain Renderer Simultan di `OverlayBannerBitmapGenerator`
- Buat fungsi `generateQuranLeaderboardUnifiedBitmap`:
  - Jika **Horizontal**:
    - Hitung jumlah kategori yang aktif (1 s/d 4).
    - Bagi kanvas menjadi `N` kolom yang sama rata dengan garis pembatas vertikal.
    - Setiap kolom memiliki header kategori (`🕌 AL-QUR'AN`, `📖 SURAT`, `📜 AYAT`, `🔤 HURUF`).
    - Render 3 baris di bawah tiap kolom: 🥇 Rank 1, 🥈 Rank 2, 🥉 Rank 3 dengan foto profil bulat, nama, dan nilai capaian.
  - Jika **Vertikal**:
    - Render 4 seksi baris bertingkat.
    - Tiap baris memuat header di kiri dan 3 juara berjajar horizontal di kanannya.
  - Seluruh teks Latin dan angka patuh terhadap parameter ukuran font yang ditentukan pengguna.

### Langkah 5: Panel Kendali UI di Floating Control
- Tambahkan card kontrol khusus di Floating Menu:
  - Toggle Master ON/OFF.
  - Pilihan Orientasi: `↔️ Horizontal (4 Kolom)` vs `↕️ Vertikal (4 Baris)`.
  - 4 Tombol Toggle Kategori: `[Al-Qur'an]` `[Surat]` `[Ayat]` `[Huruf]`.
  - Stepper D-Pad Posisi, Skala, Font Latin, dan Font Angka.
  - Tombol Bahaya `🗑️ Reset Data Peringkat`.

### Langkah 6: Verifikasi Unit Test & Kompilasi
- Perbarui `PhotoStudioAndQuranTest.kt`:
  - Test pencatatan serentak ke-4 kategori.
  - Test layout simultan Horizontal vs Vertikal tanpa ketergantungan timer rotasi.
  - Test fungsi reset data.
- Eksekusi `compile_applet` dan `gradle :app:testDebugUnitTest`.
