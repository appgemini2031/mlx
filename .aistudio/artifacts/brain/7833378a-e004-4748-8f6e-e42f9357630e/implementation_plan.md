# [Revisi 5] Penataan Tab Musik: Menu Utama Murni Upload & Live Realtime Audio Editor (Plus-Minus Stepper) di Floating Control

Rencana implementasi untuk merampingkan **Menu Utama (Tab Musik)** agar **MURNI HANYA UPLOAD DAN DAFTAR LAGU SEDERHANA** persis seperti Tab Video, serta meningkatkan fitur edit audio menjadi **LIVE REALTIME AUDIO EDITOR BERBASIS PLUS-MINUS (➖/➕) DI FLOATING CONTROL** tanpa slider geser yang rawan salah sentuh.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Tanggapan atas Masukan Cerdas Pengguna**:
> *"bagus, ada yg janggal, edit audio hilang? mungkin langsung edit realtime apakah bagus difloating? jangan pakai geser geser,pakai plus minus edit audionya"*
> 
> Ide pengguna sangat tepat, modern, dan jauh lebih praktis untuk kebutuhan siaran live!
> 
> **Keputusan Arsitektur**:
> 1. **Edit Audio TIDAK HILANG, Tapi Di-Upgrade Menjadi Realtime di Floating**:
>    - Di menu utama sebelumnya, edit audio bersifat *offline processing* (memotong dan render ulang berkas ke disk) yang memakan waktu dan berisiko memberatkan RAM HP 2600MB.
>    - Kini, fitur edit audio dipindahkan ke **Floating Tab MP3** dan bekerja **SECARA REALTIME**: Host dapat mengubah tempo, nada, dan memotong intro/outro saat lagu sedang berputar live di TikTok/Shopee, dan hasilnya terdengar seketika!
> 2. **Bebas Geser-Geser (Zero Sliders - 100% Tombol Plus-Minus `➖` / `➕`)**:
>    - Slider geser pada layar HP kecil saat live streaming sangat licin dan rawan salah geser (*accidental touch*).
>    - Seluruh pengeditan audio menggunakan **Tombol Stepper `[ ➖ ] Nilai [ ➕ ]`** dengan target sentuh lega (48dp) dan display angka yang jelas.
> 3. **Menu Utama Tetap 100% Murni Upload (Persis Tab Video)**:
>    - Menu Utama bebas dari kartu edit yang menumpuk. Hanya berisi: Baris upload, unduhan Quran, banner info pemandu, dan daftar lagu bersih (Nomor, Nama, Ukuran, Ganti Nama ✏️, Reorder ▲/▼, Hapus 🗑️).

---

### 1. Overview & Core Concept

* **Konsep Live Realtime Audio Editor**:
  Menggunakan engine `StandaloneMusicPlayer` dan `PlaybackParams` Android yang dapat memanipulasi tempo dan nada frekuensi audio secara langsung di memori pemutaran tanpa mengubah berkas fisik asli.
* **Keuntungan Bagi Host Live**:
  - Lagu yang terlalu lambat bisa dicepatkan sedikit (+0.05x) dengan 1 ketukan `➕`.
  - Nada musik yang bentrok dengan suara host bisa diturunkan sedikit (-2% atau -4%) dengan tombol `➖`.
  - Intro lagu yang terlalu panjang (misal intro instrumen 5 detik) bisa langsung dilewati dengan tombol skip intro `➕ 1s`.
  - Seluruh perubahan terdengar instan di headphone/speaker tanpa perlu merender ulang berkas.

---

### 2. User Experience & Visual Design

#### A. Menu Utama (Tab Musik Murni Upload - Persis Tab Video)
1. **Header Sticky Action Bar**:
   - `Tambah Audio`: Unggah MP3 satuan dari penyimpanan HP.
   - `Kosongkan`: Bersihkan seluruh playlist dengan dialog konfirmasi aman.
   - `📦 Upload ZIP`: Ekstraksi berkas arsip ZIP berisi banyak lagu sekaligus.
   - `🎛️ Atur di Floating`: Buka jendela melayang langsung ke tab musik.
2. **Modul Aset Suara**:
   - `QuranAudioDownloaderCard` (opsional jika diaktifkan di pengaturan).
3. **Banner Informasi Edukasi Ringkas**:
   - Kotak halus: *"💡 Semua pemutar audio, mode putar (Urut/Acak/Loop/SV4), efek suara, volume, multi-layer & Edit Audio Realtime kini diatur penuh di Floating Control."*
4. **Daftar Playlist Audio Murni (Format Persis Tab Video)**:
   - Kolom Kiri: Nomor urut (`#1`, `#2`...) + Judul lagu + Ukuran berkas.
   - Kolom Kanan: Tombol Ganti Nama (✏️), Geser Naik (▲), Geser Turun (▼), dan Hapus (🗑️).

#### B. Floating Control: Panel Edit Audio Realtime (Plus-Minus)
Di dalam Tab Musik Floating Window, terdapat panel lipat rapi: **`🎛️ Edit Suara Realtime (Plus-Minus) ▾/▴`**:
Ketika dibuka, menyajikan 4 baris stepper tanpa slider:

1. **⏱️ Tempo / Kecepatan Suara**:
   - Baris: `⏱️ Tempo:` `[ ➖ ]` **`1.00x`** `[ ➕ ]` (Rentang: `0.80x` – `1.30x`, step `0.05x`).
   - Mengubah kecepatan lagu secara instan tanpa membuat suara menjadi pitch shift distorsi.
2. **🎼 Nada / Fine-Pitch**:
   - Baris: `🎼 Pitch:` `[ ➖ ]` **`0%`** `[ ➕ ]` (Rentang: `-10%` s/d `+10%`, step `2%`).
   - Menyelaraskan nada latar belakang secara halus agar tidak menenggelamkan vokal host.
3. **✂️ Lewati Intro (Potong Awal)**:
   - Baris: `✂️ Lewati Intro:` `[ ➖ ]` **`0s`** `[ ➕ ]` (Rentang: `0s` – `15s`, step `1 detik`).
   - Tiap kali lagu diputar atau berganti, pemutar langsung melompat melewati intro sesuai detik yang ditentukan.
4. **✂️ Potong Outro (Selesai Lebih Cepat)**:
   - Baris: `✂️ Potong Outro:` `[ ➖ ]` **`0s`** `[ ➕ ]` (Rentang: `0s` – `15s`, step `1 detik`).
   - Mempercepat pergantian lagu sebelum musik hening di bagian akhir.
5. **↩️ Tombol Reset 1-Ketukan**:
   - Mengembalikan seluruh nilai tempo, pitch, dan trim ke posisi normal `1.00x / 0% / 0s` seketika.

#### C. Fitur Lain di Floating Control (Tetap Lengkap)
- **Pemutar Standalone Mini**: Play, Pause, Seekbar, Prev, Next, Accordion Quick List.
- **4 Mode Audio**: `🔁 Urut`, `🎲 SR+AX`, `🔂 Loop 1`, dan `🧬 VSA`.
- **SV4 Audio & Tombol Atur Slot LA**: Tombol `⚙️ Atur Slot LA ▾` + Dialog Overlay `showAudioLaSlotPickerOverlay` untuk memilih lagu LA1..LAn saat live.
- **5 Karakter Suara Realtime**: `Normal`, `👨 Cowok`, `👩 Cewek`, `🐿️ Chipmunk`, `🤖 Robot`.
- **Volume Stepper**: Tombol `➖` dan `➕` 10% (0% - 100%) + `🔇 Mute`.
- **Anti-Deteksi Audio & Dinamika Mic Drift**: Pilihan intensitas 10%, 20%, 30%.
- **Multi-Layer Tipe 2 & Tipe 3**: Lengkap dengan dialog overlay pemilih trek.

---

### 3. Matriks Komparasi Fitur Audio

| Fitur / Kemampuan | Menu Utama (Tab Musik) | Floating Control (Tab Musik) |
| :--- | :---: | :---: |
| **Upload File & ZIP** | ✅ HANYA DI SINI | ❌ |
| **Ganti Nama, Reorder, Hapus** | ✅ HANYA DI SINI (Bersih) | ❌ |
| **Unduh Murottal Quran** | ✅ HANYA DI SINI (Aset) | ❌ |
| **Pemutar Play/Pause/Seek** | ❌ (Dibersihkan) | ✅ Lengkap di Floating |
| **Volume Stepper (➖/➕ 10%)** | ❌ (Dibersihkan) | ✅ Lengkap di Floating |
| **4 Mode (Urut, SR, Loop, VSA)** | ❌ (Dibersihkan) | ✅ 4 Tombol Cepat di Floating |
| **SV4 Audio & Atur Slot LA** | ❌ (Dibersihkan) | ✅ Tombol `⚙️ Slot LA ▾` + Dialog Overlay |
| **Edit Audio Tempo (➖/➕)** | ❌ **(Bebas Slider)** | ✅ **Realtime Stepper `[ ➖ 1.00x ➕ ]`** |
| **Edit Audio Pitch (➖/➕)** | ❌ **(Bebas Slider)** | ✅ **Realtime Stepper `[ ➖ 0% ➕ ]`** |
| **Lewati Intro & Outro (➖/➕)**| ❌ **(Bebas Slider)** | ✅ **Realtime Stepper `[ ➖ 0s ➕ ]`** |
| **5 Karakter Suara Realtime** | ❌ (Dibersihkan) | ✅ 5 Tombol Emoji di Floating |
| **Anti-Deteksi & Mic Drift** | ❌ (Dibersihkan) | ✅ Lengkap di Floating |
| **Multi-Layer Tipe 2 & Tipe 3** | ❌ (Dibersihkan) | ✅ Lengkap di Floating + Dialog Overlay |

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                   TAJASB AUDIO ENGINE ARSITEKTUR                       │
├───────────────────────────────────┬────────────────────────────────────┤
│      MENU UTAMA (TAB MUSIK)       │     FLOATING CONTROL (TAB MUSIK)   │
│   (Murni Upload - Persis Video)   │    (Kendali Live Penuh & Realtime) │
├───────────────────────────────────┼────────────────────────────────────┤
│ • Upload Audio (+ Picker)         │ • Play / Pause / Seek / Prev / Next│
│ • Upload ZIP Batch Ingestion      │ • 4 Mode Audio (Urut/SR/Loop/VSA)  │
│ • Quran 30 Juz Downloader         │ • ⚙️ TOMBOL & DIALOG ATUR SLOT LA  │
│ • Info Banner (Atur di Floating)  │ • 🎛️ EDIT SUARA REALTIME (PLUS/MIN):│
│ • Clean Playlist Row:             │   - Tempo: [ ➖ ] 1.00x [ ➕ ]     │
│   - Nomor, Judul & Ukuran Berkas  │   - Pitch: [ ➖ ] 0% [ ➕ ]        │
│   - Ganti Nama (✏️)                │   - Intro: [ ➖ ] 0s [ ➕ ]        │
│   - Reorder Urutan (▲/▼)          │   - Outro: [ ➖ ] 0s [ ➕ ]        │
│   - Hapus Berkas (🗑️)             │   - Tombol ↩️ Reset Asli           │
│                                   │ • 5 Karakter Suara Realtime        │
│                                   │ • Stepper Volume 0-100% (±10%)     │
│                                   │ • Mute Semua & Eye On/Off          │
│                                   │ • Anti-Deteksi Audio & Mic Drift   │
│                                   │ • Tipe 2: Toggle/Vol/Overlay Picker│
│                                   │ • Tipe 3: Toggle/Vol/Tes/Jeda/     │
│                                   │           Overlay Multi-Picker     │
│                                   │ • Quick Searchable Playlist        │
└─────────────────┬─────────────────┴──────────────────┬─────────────────┘
                  │                                    │
                  ▼                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   VcamRepository & StandaloneMusicPlayer               │
│                   - Realtime PlaybackParams: speed, pitch              │
│                   - Dynamic Intro/Outro Seek Trimming                  │
│                   - IfsManager (Rolling VSA JIT Assembler)             │
│                   - SharedPreferences & Persistent State               │
└────────────────────────────────────────────────────────────────────────┘
```

#### Langkah Perubahan File:
1. `MediaManagerScreen.kt` (`AudioTabContent`):
   - Membersihkan seluruh pemutar manual, slider volume, kontrol nada, kartu SV4, dan kartu edit batch.
   - Menampilkan baris upload bersih (`Tambah Audio`, `Kosongkan`, `📦 Upload ZIP`, `🎛️ Atur di Floating`).
   - Menyimpan `QuranAudioDownloaderCard` (opsional jika aktif).
   - Menampilkan banner edukasi pemandu.
   - Menyajikan daftar lagu murni persis Tab Video: Nomor urut, judul lagu, ukuran berkas, tombol rename ✏️, reorder ▲/▼, dan delete 🗑️.
2. `StandaloneMusicPlayer.kt`:
   - Menyediakan state & setter untuk **Realtime Edit Parameter**:
     - `realtimeTempo: Float = 1.0f` (rentang 0.80f..1.30f, step 0.05f).
     - `realtimeFinePitch: Float = 0.0f` (rentang -0.10f..+0.10f, step 0.02f).
     - `realtimeSkipIntroSec: Int = 0` (rentang 0..15 detik).
     - `realtimeCutOutroSec: Int = 0` (rentang 0..15 detik).
   - Menerapkan tempo & pitch secara dinamis ke `PlaybackParams` saat lagu berputar, dan melompati intro saat lagu dimulai.
3. `SpillTabBuilder.kt`:
   - Menambahkan panel lipat **`🎛️ Edit Suara Realtime ▾/▴`** dengan 4 baris stepper `[ ➖ ] [ ➕ ]` (Tempo, Pitch, Intro, Outro) + Tombol `↩️ Reset`.
   - Menambahkan tombol **`⚙️ Atur Slot LA ▾`** dan dialog overlay **`showAudioLaSlotPickerOverlay`**.
   - Menambahkan tombol pemilih trek Tipe 2 & Tipe 3, serta kontrol Mic Drift.
4. Verifikasi:
   - Kompilasi melalui `compile_applet` untuk menjamin seluruh kode terintegrasi rapi dan build 100% sukses.
