# Pemantau Tangkapan Layar Otomatis ke Telegram (Interval 2,5 Menit • Ringan & Anti-Lag)

Fitur ini mengirimkan **tangkapan layar (*screenshot*) HP Live beresolusi rendah (`~30–45 KB`)** ke Telegram pribadi setiap **2,5 menit (150 detik)** lengkap dengan info ringkas (**Baterai, Sisa RAM, Video Aktif, dan Status Auto-Tap**), di mana seluruh foto yang terkirim tetap tersimpan di ruang chat sehingga Anda bebas mengecek riwayat dan menghapusnya sendiri secara manual.

## User Review & Critical Decisions

> [!IMPORTANT]
> Rancangan ini telah diperbarui sesuai instruksi revisi Anda. Silakan klik **Proceed** untuk memulai pembangunan fitur.

- **Keputusan Terkonfirmasi 1 — Interval 2,5 Menit & Kompresi Ringan Anti-Lag**: Tangkapan layar diambil setiap **150 detik (2,5 menit)** di *background thread*, diperkecil ke lebar `~420px` dengan kompresi JPEG `45%` (`~30–45 KB`), sehingga tidak membebani CPU, RAM, maupun kelancaran kamera virtual Live.
- **Keputusan Terkonfirmasi 2 — Caption Telemetri Ringkas**: Setiap foto menyertakan *caption* berisi Nama Perangkat, Jam, Baterai (`%` & suhu), Sisa RAM (`MB`), Video Aktif, serta Status Auto-Tap & Koreografi.
- **Keputusan Terkonfirmasi 3 — Tanpa Hapus Otomatis (Kontrol Manual Penuh oleh Pengguna)**: Aplikasi **tidak akan menghapus foto yang sudah terkirim**. Semua foto tangkapan layar tetap tersimpan di ruang chat Telegram agar Anda dapat meninjau riwayat dari waktu ke waktu dan menghapusnya sendiri kapan pun diinginkan.

---

## 1. Overview & Core Concept

- **Apa yang Dilakukan Fitur Ini**:
  Secara otomatis memotret kondisi layar HP Live yang sedang berjalan setiap **2,5 menit** secara senyap (menggunakan API `AccessibilityService.takeScreenshot` pada Android 11+ atau *Root screencap fallback* tanpa *popup* izin rekam layar), mengompresnya menjadi gambar ringan di memori, lalu mengirimkannya ke Telegram.
- **Target Pengguna**:
  Pemilik / Host Live Streaming yang ingin memantau kondisi layar HP Live dari jarak jauh beserta riwayat tangkapan layarnya setiap 2,5 menit lewat Telegram.
- **Nilai Utama (*Key Value*)**:
  - **100% Tanpa Gangguan Performa (*Zero Frame Drop*)**: Tidak merekam video layar terus-menerus; hanya memotret 1 frame setiap 150 detik dengan ukuran `~30–45 KB`.
  - **Riwayat Visual Lengkap**: Semua foto tersimpan berurutan di chat Telegram sehingga Anda bisa melihat kronologi layar Live kapan saja dan menghapusnya sendiri.

---

## 2. User Experience & Visual Design

### Alur Penggunaan (*Key User Flows*)

1. **Pengaturan Mudah di Kartu "📸 Monitor Layar ke Telegram (2,5 Menit)"**:
   - Pengguna memasukkan **Bot Token** dan **Chat ID Telegram** (dilengkapi tombol **"🔍 Ambil Chat ID Otomatis"** saat pengguna mengirim pesan ke bot, dan tombol **"📸 Tes Kirim Sekarang"**).
   - Menyalakan saklar **"Aktifkan Kirim Layar Tiap 2,5 Menit"**.
   - Dapat mengisi **Label Nama HP** (misal: `HP Live #1 - Toko Utama`) agar mudah dibedakan di Telegram.
2. **Tampilan Pesan Foto di Chat Telegram**:
   Setiap 2,5 menit, bot mengirimkan foto tangkapan layar terbaru dengan keterangan:
   - `📱 HP Live #1 (Xiaomi Redmi ...)`
   - `🕒 Waktu: 14:32:10`
   - `🔋 Baterai: 84% (⚡ Charging • 36.5°C)`
   - `🧠 RAM Tersedia: 3.120 MB / 8.192 MB`
   - `🎬 Video: Katalog_Produk_A.mp4 (AKTIF)`
   - `👆 Auto-Tap: ON • 🎲 Acak #2 (Koreo Love)`

### Identitas Visual di Aplikasi (*Visual Identity & Theme*)
- **Aesthetic Direction**: Mengikuti tema *Cyber-Telemetry Dark UI* (`DarkSurface #0B0F19`, border `NeonCyan #00F5D4`, badge aktif `EmeraldGreen #10B981`).
- **Layout Kompak & Sejajar**: Kartu pengaturan dapat dibuka-tutup (*collapsible*) di halaman Pengaturan Kamera dengan tombol aksi rata lebar (*equal-width*).

---

## 3. Key Product Decisions & Trade-Offs

- **Keputusan 1: Tangkapan Layar Senyap via *AccessibilityService* (`takeScreenshot`) + *Root Fallback***
  - *Pendekatan Terpilih*: Menggunakan `VcamAccessibilityService.takeScreenshot()` (API 30+) dengan *bitmap downscaling* di memori dan *fallback* perintah root `screencap -p` jika diperlukan.
  - *Alasan*: Tidak memerlukan *MediaProjection* yang memunculkan *popup* konfirmasi setiap kali HP di-restart dan tidak membebani GPU encoder selama Live.
- **Keputusan 2: Integrasi ke *Backup & Restore Checklist (.ZIP)***
  - *Pendekatan Terpilih*: Menyimpan Bot Token, Chat ID, Label Perangkat, dan status aktif di dalam sistem penyimpanan yang ikut ter-backup dan ter-restore dalam paket `.ZIP`.
  - *Alasan*: Memudahkan pemulihan pengaturan tanpa perlu mengetik ulang Token Telegram.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Diagram Arsitektur & Alur Kerja

```
┌────────────────────────────────────────────────────────────────────────┐
│           TIMER MONITOR LAYAR (Setiap 2,5 Menit / 150 Detik)           │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│          SILENT SCREENSHOT CAPTURE & LOW-RES COMPRESSOR (IO)           │
│  1. VcamAccessibilityService.takeScreenshot() / Root Screencap         │
│  2. Downscale ke Lebar ~420px (Aspect Ratio Tetap)                     │
│  3. Compress JPEG Quality 45% (~30–45 KB di RAM ByteArrayOutputStream) │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│              KOLEKTOR TELEMETRI RINGKAS (Baterai/RAM/Tap)              │
│  • BatteryManager (% & Suhu °C) + ActivityManager (Sisa RAM MB)        │
│  • VcamRepository (Nama Video Aktif) + AutoTapManager (Status & Koreo) │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    TELEGRAM BOT API (Multipart HTTP)                   │
│  • POST /sendPhoto (Kirim Foto + Caption Telemetri ke Chat ID)         │
│  • Tanpa auto-delete: seluruh foto tetap tersimpan untuk dicek manual  │
└────────────────────────────────────────────────────────────────────────┘
```

### Pemetaan Komponen & State (*Interactive Component Mapping*)

1. **Pengelola Tangkapan Layar Telegram (`TelegramScreenMonitorManager`)**:
   - Menyimpan state reaktif (`StateFlow`): `isEnabled`, `botToken`, `chatId`, `deviceLabel`, `lastSentTimeMs`, `sentCount`, `lastStatusText`, dan `isSending`.
   - Menjalankan loop pengiriman setiap **150.000 ms (2,5 menit)** selama saklar aktif.
   - Menyediakan fungsi `sendSnapshotNow(isManualTest: Boolean)` untuk pengujian 1-klik kapan saja dan `autoDetectChatId()` untuk mengambil Chat ID otomatis dari pesan terakhir yang dikirim pengguna ke Bot.
2. **Ekstensi Tangkapan Layar Senyap (`VcamAccessibilityService`)**:
   - Menambahkan fungsi `captureLowResScreenJpeg(maxWidthPx: Int = 420, quality: Int = 45): ByteArray?` yang menangkap layar secara asinkron tanpa mengganggu thread utama maupun kamera virtual.
3. **Kartu UI Pengaturan (`TelegramScreenMonitorCard`)**:
   - Menampilkan status koneksi & penghitung foto terkirim, input **Bot Token**, **Chat ID**, dan **Label HP**, serta tombol **"🔍 Deteksi Chat ID"** dan **"📸 Tes Kirim Sekarang"** yang sejajar rata lebar.
