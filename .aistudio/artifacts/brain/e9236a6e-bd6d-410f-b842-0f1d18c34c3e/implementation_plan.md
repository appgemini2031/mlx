# Implementasi Pengaturan Font Latin Terpisah & Orientasi Fleksibel Top 3 Koin

Rencana ini merinci penambahan kontrol pengatur ukuran font khusus teks Latin/terjemahan secara terpisah pada Overlay Khatam Al-Qur'an, serta kontrol fleksibel orientasi Top 3 Gift Koin (Horizontal 3 kolom atau Vertikal 3 baris) yang independen.

---

## User Review & Keputusan Kritis

> [!IMPORTANT]
> Berdasarkan konfirmasi pada tahap klarifikasi:
> 1. **Font Selain Arab (Latin/Terjemahan/Notif)**: Disediakan tombol pengatur ukuran font khusus Latin yang terpisah langsung di Tab Khatam, sehingga pengguna tidak lagi bingung dan bebas menyesuaikan ukuran teks terjemahan tanpa mengganggu ukuran font teks Arab.
> 2. **Orientasi Top 3 Koin**: Disediakan tombol toggle independen untuk memilih mode **Horizontal (3 kolom)** atau **Vertikal (3 baris)** di atas Top 10 Tap Love.

- **Keputusan 1**: Menambahkan state `latinFontSizeSp` (default 12.0sp, range 7.0sp .. 24.0sp) ke dalam `InteractiveQuranKhatamEngine` & `QuranKhatamState` dengan tombol stepper `🔤- Latin`, `🔤+ Latin`, dan `🔄 Reset (12sp)` di Floating Menu.
- **Keputusan 2**: Menambahkan konfigurasi `topGiftOrientation` ("HORIZONTAL" vs "VERTICAL") di `LiveEventConfig` & `LiveEventPreferences`, dengan tombol pemilih mode di tab Live Comment.

---

## 1. Overview & Konsep Utama

- **Masalah Pengguna**:
  - Pengguna merasa bingung (*pusing dan serba salah*) saat ingin mengatur font selain Arab karena saat ini hanya ada satu tombol ukuran font yang terikat pada teks Arab, sedangkan teks terjemahan dan notifikasi dihitung secara kaku atau tidak memiliki tombol khusus.
  - Top 3 Koin yang berada di atas Top 10 Tap Love sebelumnya terikat pada mode layout induk (hanya horizontal atau vertikal sesuai mode Top 10), sehingga pengguna menginginkan fleksibilitas agar Top 3 Koin bisa di-set Horizontal (3 kolom berjajar) atau Vertikal (3 baris bertingkat) secara mandiri.
- **Solusi**:
  1. Kontrol terpisah untuk ukuran Font Latin dengan indikator status yang jelas dan tombol stepper yang intuitif.
  2. Pilihan orientasi independen untuk Top 3 Gift Koin (Horizontal 3 Kolom / Vertikal 3 Baris).

---

## 2. Pengalaman Pengguna & Desain Visual (UX & Design)

```
┌───────────────────────────────────────────────────────────────┐
│ TAB 3: KHATAM QUR'AN - KONTROL TIPOGRAFI LENGKAP              │
│ Status: Font Arab: 28sp | Font Latin: 12sp | Baris: 4 Baris  │
├───────────────────────────────────────────────────────────────┤
│ [➖ Arab] [➕ Arab]  │  [🔤- Latin] [🔤+ Latin]  │  [🔄 Reset]  │
└───────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────┐
│ TAB 4: LIVE EVENT - OVERLAY TOP 3 KOIN & TOP 10 TAP LOVE      │
│ [↔️ Top 3: Horizontal (3 Kolom)] [↕️ Top 3: Vertikal (3 Baris)]│
├───────────────────────────────────────────────────────────────┤
│ Mode Top 10: [↔️ Horizontal (5x2)] [↕️ Vertikal (1x10)]       │
│ [🖼️- Foto Top 3] [🖼️+ Foto Top 3] [🔄 Reset Foto]             │
└───────────────────────────────────────────────────────────────┘
```

### Visual Output pada Video Stream:
1. **Overlay Khataman**:
   - Teks Arab dirender tajam dengan `arabicFontSizeSp` (misal 28sp).
   - Teks Terjemahan Indonesia dirender terpisah dengan `latinFontSizeSp` (misal 12sp).
   - Kolom Doa & Hadiah di deck bawah ayat mengadopsi ukuran font Latin proporsional yang rapi dan serasi.
2. **Top 3 Gift Koin**:
   - **Mode Horizontal**: 3 kolom sejajar ke samping (Rank 1 Emas di kiri/tengah, Rank 2 Perak, Rank 3 Perunggu) dengan foto profil bulat, nama, dan jumlah diamond `💎 1500`.
   - **Mode Vertikal**: 3 baris bertingkat ke bawah dengan foto profil bulat, cincin peringkat, nama, dan koin `💎 1500`.

---

## 3. Keputusan Teknis & Arsitektur Sistem

```
┌──────────────────────────────────────┐     ┌────────────────────────────────────┐
│   QuranKhatamTabBuilder (UI Float)   │     │   LiveCommentTabBuilder (UI Float) │
│   - Stepper Font Arab (20..60sp)     │     │   - Toggle Top 3 H/V               │
│   - Stepper Font Latin (7..24sp)     │     │   - Toggle Top 10 H/V              │
│   - Reset Preset Best Practice       │     │   - Stepper Ukuran Foto Top 3      │
└──────────────────┬───────────────────┘     └──────────────────┬─────────────────┘
                   │                                            │
                   ▼                                            ▼
┌──────────────────────────────────────┐     ┌────────────────────────────────────┐
│   InteractiveQuranKhatamEngine       │     │   LiveEventPreferences & Config    │
│   - state.latinFontSizeSp            │     │   - cfg.topGiftOrientation         │
│   - setLatinFontSize / adjustLatin   │     │   - cfg.topGiftAvatarScale         │
└──────────────────┬───────────────────┘     └──────────────────┬─────────────────┘
                   │                                            │
                   ▼                                            ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                       OverlayBannerBitmapGenerator                              │
│ - generateQuranKhatamBitmap: render terjemahan & latin via latinFontSizeSp       │
│ - generateTopLikersBitmap: render Top 3 Koin (Horizontal vs Vertikal)          │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Langkah Implementasi (Execution Plan)

### Langkah 1: State & Preferences Font Latin pada InteractiveQuranKhatamEngine
- Tambahkan `val latinFontSizeSp: Float = 12.0f` pada `QuranKhatamState`.
- Simpan dan pulihkan `latin_font_size_sp` di `SharedPreferences`.
- Tambahkan helper `setLatinFontSize(size: Float)` dan `adjustLatinFontSize(delta: Float)`.
- Sertakan `latinFontSizeSp` ke dalam broadcast banner payload (`customPrefix = "$showTrans:::$latinFontSizeSp"`).

### Langkah 2: State & Preferences Orientasi Top 3 Koin pada LiveEvent
- Tambahkan `val topGiftOrientation: String = "HORIZONTAL"` pada `LiveEventConfig`.
- Simpan `key_top_gift_orientation` pada `LiveEventPreferences` dengan fungsi `setTopGiftOrientation()` dan `toggleTopGiftOrientation()`.
- Kirimkan informasi orientasi Top 3 melalui provider dan broadcast payload (`customPrefix = "$topLikersOrientation:::$topGiftAvatarScale:::$topGiftOrientation"`).

### Langkah 3: Antarmuka UI Pengguna (Floating Menu Tabs)
- **Tab Khatam (`QuranKhatamTabBuilder`)**:
  - Perbarui baris status tipografi: `Font Arab: 28sp | Font Latin: 12sp | Baris: 4 Baris`.
  - Tambahkan baris kontrol khusus: `🔤- Latin (-1sp)`, `🔤+ Latin (+1sp)`, dan tombol `🔄 Standar (12sp)`.
- **Tab Live Comment (`LiveCommentTabBuilder`)**:
  - Tambahkan tombol pemilih mode Top 3 Koin: `↔️ Top 3: Horiz (3 Kolom)` vs `↕️ Top 3: Vert (3 Baris)`.
  - Pertahankan kontrol ukuran foto profil Top 3 (`🖼️- Foto` / `🖼️+ Foto`) dan mode Top 10 Love.

### Langkah 4: Renderer Bitmap (`OverlayBannerBitmapGenerator`)
- Pada `generateQuranKhatamBitmap`:
  - Baca `latinFontSizeSp` dari `customPrefix`.
  - Gunakan `latinFontSizeSp` untuk mengukur dan merender teks terjemahan bahasa Indonesia, teks doa, dan informasi huruf tanpa mengubah teks Arab.
- Pada `generateTopLikersBitmap`:
  - Baca `topGiftOrientation` dari `customPrefix`.
  - Jika `topGiftOrientation == "HORIZONTAL"`, render 3 kolom berdampingan.
  - Jika `topGiftOrientation == "VERTICAL"`, render 3 baris bertingkat.
  - Pisahkan dengan garis pemisah sebelum merender Top 10 Tap Love di bawahnya.

### Langkah 5: Verifikasi & Pengujian Unit
- Tambahkan pengujian di `PhotoStudioAndQuranTest.kt` dan `LiveEventBrokerTest.kt` untuk:
  - Validasi persistensi dan clamping `latinFontSizeSp` (7.0sp .. 24.0sp).
  - Validasi mode Horizontal vs Vertikal pada Top 3 Gift Koin.
- Jalankan `compile_applet` dan verifikasi seluruh unit test lolos.
