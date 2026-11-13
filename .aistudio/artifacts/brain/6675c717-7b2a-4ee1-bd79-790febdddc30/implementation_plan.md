# Penataan Efisiensi Ruang UI & Eliminasi Duplikasi Kontrol (Revisi 1)

Optimalisasi komprehensif antarmuka pengguna (UI) di seluruh tab aplikasi agar hemat ruang, bebas dari kontrol duplikat, dan bersih dari teks penjelasan yang memakan tempat dengan memindahkannya ke tombol info interaktif (`FeatureInfoButton`), serta **menyembunyikan total kartu Al-Qur'an saat statusnya OFF di Pengaturan**.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Keputusan Kritis yang Dikonfirmasi**:
> 1. **Kartu Al-Qur'an Disembunyikan Total Saat OFF**: Jika modul Al-Qur'an dimatikan (`enableQuranAudioModule == false`), kartu Al-Qur'an **TIDAK AKAN DITAMPILKAN SAMA SEKALI** di Tab Musik (menghapus kartu placeholder saklar yang membuat layar terasa sempit). Modul dapat diaktifkan kembali kapan saja lewat Pengaturan / Modul Floating.
> 2. **Cakupan Penataan**: Dilakukan di **seluruh tab menu utama** (Video, Musik, RTMP, Sistem, dan Log).
> 3. **Lokasi Anti-Deteksi (Anti-Hash)**: Kartu Anti-Deteksi Video dan Audio **dipusatkan di Tab Sistem saja**, dan dihilangkan dari Tab Video & Tab Musik untuk mengeliminasi duplikasi serta menghemat ruang vertikal secara drastis.
> 4. **Format Teks Panjang**: Seluruh teks panjang, penjelasan mode, dan instruksi fitur dipindahkan ke **tombol info `!` interaktif** yang menampilkan kotak dialog ringkas dengan tombol "Mengerti".
> 5. **Jaminan Fungsi**: Seluruh event handler, ViewModel, dan persistensi `VcamConfig` tetap berfungsi 100% tanpa regresi.

---

## 1. Overview & Core Concept

- **Tujuan Utama**: Menghadirkan antarmuka studio yang lapang, ringkas, dan fokus pada aksi tanpa ada elemen tak terpakai atau kontrol berulang yang memadati layar.
- **Prinsip Efisiensi Ruang**:
  - Jika suatu fitur opsional sedang nonaktif (seperti modul audio Al-Qur'an), tidak perlu ada kartu placeholder yang menyita tinggi layar.
  - Kartu-kartu inti ditata padat dengan padding ergonomis.
  - Teks deskripsi dan instruksi operasional disimpan di balik tombol `FeatureInfoButton` (`!`).
- **Target Tab**:
  - **Tab Video**: Bebas dari kartu anti-deteksi raksasa; fokus pada pemilihan video, kontrol pemutaran, alur siklus (Standar, Spill, Infinity), dan Studio Foto yang rapi.
  - **Tab Musik**: Bersih total jika Al-Qur'an nonaktif; bebas dari kartu audio anti-deteksi raksasa; fokus pada pemutar MP3, playlist audio, dan layer suara.
  - **Tab RTMP**: Header ringkas, form input URL & Key padat dengan preset cepat, indikator status siaran.
  - **Tab Sistem**: Pusat kendali tunggal terpadu untuk Anti-Deteksi Video/Audio, Aplikasi Target, AdBlocker, Booster, Jendela Melayang, Codec Guard, dan Cadangan.
  - **Tab Log**: Panel diagnostik bersih dengan tombol aksi cepat.

---

## 2. User Experience & Visual Design

### Struktur & Hirarki Visual Baru

```
┌────────────────────────────────────────────────────────┐
│  🎬 TAJASB PRO - Preview & Header Studio (Kompak)      │
├────────────────────────────────────────────────────────┤
│  [🎬 Video (N)] [🎵 Musik (N)] [📡 RTMP] [⚙️ Sistem] [📋 Log] │
├────────────────────────────────────────────────────────┤
│                                                        │
│  TAB VIDEO:                                            │
│  ├─ ✨ Studio Foto & Animasi (Accordion Collapsed)     │
│  ├─ 🔄 Pengatur Alur Siklus (Standar / Spill / Inf) ! │
│  └─ 📁 Daftar Putar Video & Kontrol Pemutaran          │
│                                                        │
│  TAB MUSIK:                                            │
│  ├─ 📖 Murottal Al-Qur'an (HANYA MUNCUL JIKA ON) !     │
│  ├─ 🎵 Pemutar MP3 & Dual Audio Layer !               │
│  └─ 🎼 Daftar Audio & Preset Cepat                    │
│                                                        │
│  TAB SISTEM (Pusat Pengaturan Tunggal):                │
│  ├─ 🛡️ Anti-Deteksi Video & DNA Sesi (Dipusatkan) !   │
│  ├─ 🎧 Anti-Deteksi Audio & Mutator Suara !           │
│  ├─ 🎯 Aplikasi Target & Pemblokir Iklan              │
│  ├─ ⚡ Device & Network Booster                       │
│  └─ 🪟 Menu Melayang & Kodek Guard                    │
│                                                        │
├────────────────────────────────────────────────────────┤
│  ⚡ Dock Status Bawah (Notifikasi Cepat & Non-Blocking) │
└────────────────────────────────────────────────────────┘
```

### Panduan Desain & Efisiensi Ruang
- **Sembunyikan Placeholder Al-Qur'an**: Mengubah blok `if (config.enableQuranAudioModule)` pada Tab Musik agar tidak lagi merender blok `else { item { Card(...) } }`. Tab Musik langsung menampilkan pemutar MP3 dan daftar audio saat modul Al-Qur'an OFF.
- **Pengurangan Tinggi Elemen**: Memangkas padding vertikal berlebih dari 12–16dp menjadi 6–8dp pada kartu-kartu ringkas.
- **Tombol Info (!) Bersih**: Menggantikan blok teks penjelasan 3–5 baris dengan komponen `FeatureInfoButton` berdiameter 18dp yang membuka AlertDialog bertema gelap (`DarkSurface`) dengan aksen `NeonCyan`.
- **Eliminasi Duplikasi Total**:
  - Menghapus pemanggilan `AntiHashShieldCard(viewModel, config)` dari `VideoTabContent` di `MediaManagerScreen.kt`.
  - Menghapus pemanggilan `AudioAntiHashShieldCard(viewModel, config)` dari `AudioTabContent` di `MediaManagerScreen.kt`.
  - Memastikan kedua kartu tersebut terpusat dan tertata rapi di dalam Tab Sistem (`CameraSettingsScreen.kt`).

---

## 3. Key Product Decisions & Trade-Offs

- **Keputusan 1: Modul Al-Qur'an Tidak Ditampilkan Saat OFF**
  - *Pendekatan*: Jika `enableQuranAudioModule` bernilai `false`, tidak ada kartu atau placeholder apapun di Tab Musik.
  - *Alasan*: Pengguna merasa layar sempit jika ada placeholder yang tidak aktif. Pengaktifan modul dapat dilakukan langsung melalui saklar di Tab Sistem atau Menu Melayang.
- **Keputusan 2: Pemusatan Anti-Deteksi di Tab Sistem**
  - *Pendekatan*: Menghilangkan kartu Anti-Deteksi dari Tab Video dan Musik, menjadikannya satu kesatuan di Tab Sistem.
  - *Alasan*: Pengguna tidak perlu melihat panel kalkulator DNA framing dan stepper agresifitas berulang kali saat sedang mengelola playlist video/musik. Memusatkannya di Tab Sistem memotong tinggi scroll tab media hingga lebih dari 40%.
- **Keputusan 3: Pemindahan Teks ke FeatureInfoButton Dialog**
  - *Pendekatan*: Semua teks panduan panjang dan tips penggunaan dipindahkan ke dalam atribut `description` milik `FeatureInfoButton`.
  - *Alasan*: Menjaga antarmuka tetap bersih untuk pengguna tingkat lanjut, namun tetap menyediakan bantuan instan bagi pengguna baru hanya dengan 1 ketukan.
- **Keputusan 4: Menjaga 100% Kontrak Data & ViewModel**
  - *Pendekatan*: Tidak ada fungsi ViewModel atau nilai di `VcamConfig` yang diubah, dihapus, atau diubah perilakunya. Seluruh mutasi state tetap terhubung ke backend repository yang sama.

---

## 4. Technical Architecture & Implementation Steps

```
┌──────────────────────────────────────────────────────────────┐
│                    MediaManagerScreen.kt                     │
├──────────────────────────────────────────────────────────────┤
│  1. VideoTabContent                                          │
│     - Hapus AntiHashShieldCard                               │
│     - Padatkan alur siklus & PhotoStudioCard                 │
│  2. AudioTabContent                                          │
│     - Hapus AudioAntiHashShieldCard                          │
│     - Hapus blok 'else' placeholder Al-Quran saat OFF        │
│     - Padatkan pemutar MP3                                   │
│  3. CameraSettingsScreen.kt (Tab Sistem)                     │
│     - Pertahankan AntiHashShieldCard & AudioAntiHashShield   │
│     - Ringkas kartu pengaturan & pindahkan teks ke '!'       │
│  4. FeatureInfoButton.kt                                     │
│     - Pastikan dialog minimalis, responsif, dan mudah dibaca │
└──────────────────────────────────────────────────────────────┘
```

### Langkah Kerja Spesifik:
1. **Modifikasi `MediaManagerScreen.kt`**:
   - Di `VideoTabContent`: Hapus pemanggilan `AntiHashShieldCard`.
   - Di `AudioTabContent`:
     - Hapus pemanggilan `AudioAntiHashShieldCard`.
     - Ubah logika Al-Qur'an menjadi hanya:
       ```kotlin
       if (config.enableQuranAudioModule) {
           item {
               QuranAudioDownloaderCard(onUserMessage = { viewModel.showUserMessage(it) })
           }
       }
       // TIDAK ADA blok else placeholder
       ```
   - Ringkas teks penjelasan alur siklus dan simpan di `FeatureInfoButton`.
2. **Optimalisasi `CameraSettingsScreen.kt`**:
   - Tata letak `AntiHashShieldCard` dan `AudioAntiHashShieldCard` di Tab Sistem agar tersusun ergonomis.
   - Pangkas teks deskripsi panjang pada kartu-kartu pengaturan sistem dan alihkan ke `FeatureInfoButton`.
3. **Pemeriksaan Kompilasi (`compile_applet`)**:
   - Jalankan kompilasi Gradle untuk memverifikasi aplikasi berhasil dikompilasi tanpa error.
