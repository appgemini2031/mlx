# Rencana Strategi & Pengembangan Game Interaktif TikTok Live (Dual-Mode Standby)

Rencana ini merangkum strategi implementasi dan pengaturan kontrol di mana **Game Tarik Tambang maupun Khataman Al-Qur'an KEDUANYA DISETEL DEFAULT OFF**. Layar kamera virtual akan 100% bersih saat awal siaran, memberikan kendali penuh kepada host untuk mengaktifkan salah satu fitur secara manual sewaktu-waktu.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Keputusan Revisi Terkini (User Feedback)**:
> 1. **Keduanya DEFAULT OFF (Zero-Auto-Start)**:
>    - **Game Tarik Tambang**: Disetel **DEFAULT OFF** saat inisialisasi.
>    - **Khataman Al-Qur'an**: Disetel **DEFAULT OFF** saat inisialisasi.
>    - Layar siaran kamera virtual awal 100% bersih tanpa gangguan overlay teks/game.
> 2. **Kendali Manual Host 1-Klik**:
>    - Host bebas memilih momen kapan ingin menghidupkan game interaktif (tombol `GAME: ON` di Tab Notif -> Tambang).
>    - Host juga bebas menyalakan Khataman Al-Qur'an (tombol `KHATAM: ON`) jika ingin siaran bertema ibadah.
> 3. **Perlindungan Data & Riwayat Permanen**:
>    - Skor kemenangan ronde Tarik Tambang dan riwayat ayat Khataman tetap tersimpan di SharedPreferences lokal sehingga tidak akan hilang saat dinonaktifkan.
> 4. **Mekanisme Gamifikasi Seimbang Siap Pakai**:
>    - Begitu Game diaktifkan oleh host: Tap Like (+1), Chat Komentar (+10), dan Gift Koin (+10 s/d +500) langsung aktif menggerakkan tali secara real-time.

---

## 1. Overview & Core Concept

### Filosofi "Bersih di Awal, Siaga Saat Dibutuhkan"

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       STATUS AWAL APLIKASI (DEFAULT OFF)                    │
│                                                                             │
│   • Game Tarik Tambang : [OFF]  (Abu-abu, hemat daya & memori)              │
│   • Khataman Al-Qur'an : [OFF]  (Abu-abu, hemat daya & memori)              │
│   • Layar Live Stream  : 100% Bersih menampilkan video siaran host murni    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                      Host Memilih Mode Siaran di Floating Window
                                       │
            ┌──────────────────────────┴──────────────────────────┐
            ▼                                                     ▼
┌───────────────────────────────────────┐ ┌───────────────────────────────────────┐
│        HOST MENEKAN [GAME: ON]        │ │       HOST MENEKAN [KHATAM: ON]       │
├───────────────────────────────────────┤ ├───────────────────────────────────────┤
│ • Tarik Tambang AKTIF di overlay      │ │ • Khataman Al-Qur'an AKTIF di overlay │
│ • Khataman otomatis standby (OFF)     │ │ • Tarik Tambang otomatis standby (OFF)│
│ • Penonton bersaing tap/chat/gift     │ │ • Progres ayat berlanjut              │
└───────────────────────────────────────┘ └───────────────────────────────────────┘
```

- **Nilai Inti (Key Value)**: Memberikan ketenangan bagi host agar tidak panik dengan overlay yang tiba-tiba muncul di live stream. Host menyalakan game hanya saat audiens sudah mulai berkumpul dan siap berpartisipasi.
- **Nol Beban Resource**: Saat kedua modul bernilai OFF, pemrosesan rendering bitmap dan event routing langsung dilewati (zero CPU/GPU overhead).

---

## 2. User Experience & Visual Design

### A. Tampilan Panel Kontrol Host di Floating Window (Tab Notif)

1. **Sub-Tab Tambang (Game Interaktif)**:
   - Tombol status awal: `[GAME: OFF]` berlatar gelap `#334155`.
   - Sekali klik berubah menjadi: `[GAME: AKTIF]` berlatar hijau terang `#059669`.
   - Mengaktifkan Game otomatis memastikan modul Khataman dalam status OFF (tidak ada benturan overlay).
   - Dilengkapi tombol simulator untuk pengujian mandiri (`+1 Tap`, `+10 Chat`, `+50 Gift`).
   - Kontrol ronde: `↺ Reset Tali`, `▶ Ronde Baru`, `0 Skor`.
2. **Sub-Tab Khatam (Al-Qur'an 30 Juz)**:
   - Tombol status awal: `[KHATAM: OFF]` berlatar gelap `#334155`.
   - Sekali klik berubah menjadi: `[KHATAM: AKTIF]` berlatar hijau/emas.
   - Mengaktifkan Khataman otomatis memastikan modul Game dalam status OFF.

### B. Tampilan Layar Siaran (Virtual Camera Overlay) Saat Game Aktif

1. **Header Papan Skor & Ronde**:
   - Menampilkan `RONDE 1` (atau ronde berjalan) dan skor kemenangan (`COWOK 2 - 1 CEWEK`).
   - Badge nama penarik terkuat (Top Puller / MVP).
2. **Tali Tarik Tambang Dinamis**:
   - Visual tali horizontal dengan warna kubu A (Biru) dan kubu B (Merah).
   - Bendera penanda tengah yang bergeser dinamis mengikuti perolehan tarikan.
   - Bar persentase dominasi real-time (`50% - 50%`).
3. **Selebrasi Kemenangan Ronde Otomatis**:
   - Spanduk selebrasi saat salah satu kubu mencapai garis finish, disusul hitung mundur transisi ke ronde berikutnya.

---

## 3. Key Product Decisions & Trade-Offs

### Keputusan 1: Keduanya Default OFF (Pure Opt-In)
- **Pendekatan yang Dipilih**: Variabel inisialisasi `is_enabled` pada kedua engine disetel `false` secara default di SharedPreferences dan runtime state.
- **Alasan**: Menghormati privasi dan keleluasaan host; layar siaran selalu bersih saat pertama kali dibuka sampai host secara sadar memutuskan untuk memulai sesi game.
- **Alternatif yang Ditolak**: Auto-start game saat live terhubung (ditolak karena dapat mengganggu persiapan host sebelum siaran siap).

### Keputusan 2: Mutual-Exclusive Toggle (Anti-Tabrakan Overlay)
- **Pendekatan yang Dipilih**:
  - Saat `InteractiveTugOfWarEngine.setEnabled(true)` dipanggil, ia otomatis memanggil `InteractiveQuranKhatamEngine.setEnabled(false)`.
  - Sebaliknya, saat `InteractiveQuranKhatamEngine.setEnabled(true)` dipanggil, ia otomatis memanggil `InteractiveTugOfWarEngine.setEnabled(false)`.
- **Alasan**: Mencegah tabrakan render bitmap di OpenGL ES, mencegah perebutan posisi overlay di layar, dan menghindari kebingungan interpretasi event penonton.

### Keputusan 3: Keseimbangan Bobot Poin Permainan
- **Pendekatan yang Dipilih**:
  - Tap Like: +1 poin (menggerakkan tali secara perlahan, ramah penonton gratisan).
  - Chat Komentar Tim: +10 poin (memicu algoritma engagement live TikTok).
  - Gift Koin Saweran: +10 s/d +500 poin (alat pembalik keadaan / penentu kemenangan dramatis).

---

## 4. Technical Architecture & Data Strategy

### Diagram Alur Kontrol & Status

```
                          ┌───────────────────────────┐
                          │   Aplikasi / Floating     │
                          │   Control Service Dimuat  │
                          └─────────────┬─────────────┘
                                        │
                         Inisialisasi Nilai Default
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 ▼                                             ▼
  ┌─────────────────────────────┐               ┌─────────────────────────────┐
  │  InteractiveTugOfWarEngine  │               │ InteractiveQuranKhatamEngine│
  │     isEnabled = false       │               │      isEnabled = false      │
  │        (DEFAULT OFF)        │               │        (DEFAULT OFF)        │
  └──────────────┬──────────────┘               └──────────────┬──────────────┘
                 │                                             │
                 │                 [Kamera Bersih]             │
                 └──────────────────────┬──────────────────────┘
                                        ▼
                 ┌─────────────────────────────────────────────┐
                 │       Host Memilih Aktivasi via Panel       │
                 └──────────────────────┬──────────────────────┘
                                        │
           ┌────────────────────────────┴────────────────────────────┐
           ▼ (Jika Tombol Game Ditekan)                              ▼ (Jika Tombol Khatam Ditekan)
┌───────────────────────────────────────┐         ┌───────────────────────────────────────┐
│ • TugOfWarEngine.setEnabled(true)     │         │ • QuranKhatamEngine.setEnabled(true)  │
│ • QuranKhatamEngine.setEnabled(false) │         │ • TugOfWarEngine.setEnabled(false)    │
│ • Render Tali & Skor di Kamera        │         │ • Render Ayat di Kamera               │
└───────────────────────────────────────┘         └───────────────────────────────────────┘
```

### File yang Diselaraskan:
1. `app/src/main/java/com/tajasb/game/InteractiveTugOfWarEngine.kt`:
   - Pastikan default `is_enabled` bernilai `false` di SharedPreferences dan constructor state.
2. `app/src/main/java/com/tajasb/quran/InteractiveQuranKhatamEngine.kt`:
   - Pastikan default `is_enabled` bernilai `false` di SharedPreferences dan constructor state.
   - Tambahkan pemanggilan `InteractiveTugOfWarEngine.peekInstance()?.setEnabled(false)` saat Khatam diaktifkan.
3. `app/src/main/java/com/tajasb/floating/tabs/TugOfWarTabBuilder.kt`:
   - Pastikan visual tombol awal menampilkan `GAME: OFF` dengan warna latar `#334155`.
4. `app/src/main/java/com/tajasb/floating/tabs/QuranKhatamTabBuilder.kt`:
   - Pastikan visual tombol awal menampilkan status `OFF`.

---

## Rencana Langkah Selanjutnya (Next Steps)
1. Memperbarui `InteractiveTugOfWarEngine.kt` agar default state selalu `false` (OFF).
2. Memperbarui `InteractiveQuranKhatamEngine.kt` agar default state selalu `false` (OFF) dan menyematkan proteksi mutual-exclusive saat diaktifkan.
3. Menyelaraskan tampilan tombol saklar di `TugOfWarTabBuilder.kt` dan `QuranKhatamTabBuilder.kt`.
4. Menjalankan verifikasi kompilasi (`compile_applet`) untuk memastikan aplikasi bebas dari error.
