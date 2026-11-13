# Remote Control & Dashboard Monitoring Telegram Realtime (Anti-Hack)

Rencana arsitektur dan implementasi integrasi kendali jarak jauh (Remote Control) dua arah dan pemantauan perangkat (Live Dashboard) antara aplikasi Android CuanCame dan Bot Telegram menggunakan basis data realtime Firebase Firestore dengan proteksi kriptografi anti-tamper.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Keputusan Terkonfirmasi (Phase 1):**
> 1. **Metode Komunikasi Realtime**: Firebase Firestore sebagai basis data tersinkronisasi cepat antara APK Android dan script bot Telegram (`tools/telegram_bot.py`).
> 2. **Cakupan Informasi Dashboard**: Lengkap — Status online/heartbeat, model HP, jam & tanggal aktif, sisa masa berlaku lisensi, serta log bug/crash otomatis.
> 3. **Cakupan Perintah Remote**: Lengkap — Kunci APK (Kill-Switch), aktivasi mode decoy (micro-lag/fake warning), dan pengiriman pesan peringatan langsung ke layar live stream (Screen Banner).
> 
> **Persetujuan Firebase di Turn Eksekusi:**
> Karena Firestore dipilih sebagai jalur komunikasi realtime, dialog aktivasi Firebase akan dimunculkan saat eksekusi disetujui.

---

### 1. Overview & Core Concept

Sistem ini memberikan kontrol penuh 100% dari genggaman admin melalui aplikasi Telegram tanpa perlu menyentuh fisik perangkat yang sedang disewa atau dipakai:
- **Pantauan Status Aktif (Live Telemetry)**: Admin dapat melihat status semua perangkat yang sedang online, model HP, kapasitas RAM & baterai, nama video yang sedang diputar, dan sisa hari lisensi.
- **Deteksi Bug & Crash Log Otomatis**: Jika perangkat klien mengalami memori penuh (RAM 2600MB) atau error codec kamera, notifikasi instan langsung masuk ke chat Telegram admin beserta cuplikan log error.
- **Kendali Jarak Jauh Instan (Remote Control)**: Admin dapat menekan tombol inline di Telegram untuk mengunci APK seketika (`Kill-Switch`), menyabotase performa secara halus (`Decoy Mode`), atau memunculkan pesan peringatan di atas layar (`Emergency Screen Banner`).
- **Pertahanan Anti-Hack (Cryptographic Command Verification)**: Seluruh perintah dari Telegram ditandatangani secara kriptografis menggunakan HMAC-SHA256 dengan secret salt admin, sehingga pihak yang membongkar APK atau mencoba menginjeksi Firestore tidak dapat memalsukan perintah admin.

---

### 2. User Experience & Visual Design

#### A. Alur Dashboard di Telegram Admin (`tools/telegram_bot.py`)
1. **Menu Utama Dashboard (`/dashboard` atau Tombol Inline)**:
   - Tampilan ringkas: Jumlah perangkat aktif hari ini, perangkat kedaluwarsa, dan status sistem.
   - Tombol Navigasi: `[📱 Daftar Perangkat Aktif]`, `[⚠️ Log Bug & Crash]`, `[🛡️ Kontrol Darurat]`.
2. **Detail Perangkat (Device Inspector View)**:
   ```text
   ╭────────────────────────────────────────╮
   │ 📱 DEVICE: SAMSUNG SM-A125F            │
   │ 🆔 ID: a1b2c3d4-e5f6-7890-abcd        │
   │ 🟢 STATUS: ONLINE (Ping 12 detik lalu) │
   │ 📅 TANGGAL & JAM: 22 Okt 2026, 11:15  │
   │ ⏳ MASA AKTIF: 14 Hari Tersisa         │
   │ 📹 VIDEO AKTIF: master_live_01.mp4     │
   │ 🔋 BATERAI: 85% | 🌡️ SUHU: 37.2°C     │
   │ 🧠 RAM TERPAKAI: 1.8 GB / 2.6 GB      │
   ╰────────────────────────────────────────╯
   [ 🔒 Kunci APK ]  [ 👻 Mode Decoy ]
   [ 💬 Kirim Pesan ] [ 🔄 Refresh ]
   ```
3. **Eksekusi Perintah Seketika**:
   - Klik **[🔒 Kunci APK]** -> Meminta konfirmasi -> Mengirim sinyal revoke bertanda tangan -> Layar HP klien langsung terkunci permanen dengan pesan "Lisensi Ditangguhkan oleh Administrator".
   - Klik **[👻 Mode Decoy]** -> Memilih intensitas (Micro-lag 10 FPS, audio stutter, atau auto-exit acak) -> APK langsung merespons tanpa memunculkan pesan error yang mencurigakan.
   - Klik **[💬 Kirim Pesan]** -> Mengetik teks pesan -> Spanduk overlay langsung muncul di atas layar HP klien.

#### B. Pengalaman di Sisi Aplikasi Android (APK)
- **Status Jaringan di Menu Utama & Floating Bubble**: Terdapat indikator titik hijau/merah halus bertuliskan "Cloud Sync Aktif" pada header menu.
- **Penerima Pesan Layar (Overlay Alert View)**: Menampilkan banner elegan semi-transparan dengan animasi slide-down jika ada pesan darurat dari admin.
- **Penguncian Seketika**: Jika status terkunci diterima, floating bubble tertutup otomatis, kamera virtual dihentikan, dan layar kembali ke halaman aktivasi lisensi.

---

### 3. Key Product Decisions & Trade-Offs

- **Firestore Realtime Snapshot Listener vs. Polling HTTP**:
  - *Keputusan*: Menggunakan Firestore `addSnapshotListener` pada dokumen `/devices/{deviceId}` di Android.
  - *Alasan*: Latensi respons di bawah 500 milidetik saat admin menekan tombol di Telegram, sekaligus sangat hemat daya baterai dibandingkan polling HTTP terus-menerus.
- **Tanda Tangan Kriptografi HMAC-SHA256 pada Setiap Perintah**:
  - *Keputusan*: Payload instruksi remote berisi `command`, `timestamp`, dan `signature`.
  - *Alasan*: Mencegah serangan *man-in-the-middle* atau modifikasi lokal oleh pengguna yang ingin memalsukan status atau menyabotase perintah admin.
- **Pemisahan Jalur Telemetri & Log**:
  - *Keputusan*: Telemetri status ringan (heartbeat) disimpan dalam satu dokumen per perangkat, sedangkan log crash disimpan di sub-koleksi `/devices/{deviceId}/crash_logs` dengan batasan rotasi otomatis 10 log terakhir agar tidak membebani kuota data.

---

### 4. Technical Architecture & Data Strategy (Technical Reference)

```
┌───────────────────────────────────────────────────────────┐
│                    ADMIN TELEGRAM BOT                     │
│                  (tools/telegram_bot.py)                  │
└─────────────────────────────┬─────────────────────────────┘
                              │
               (Kirim Perintah & Baca Status)
                              ▼
┌───────────────────────────────────────────────────────────┐
│                 FIREBASE FIRESTORE CLOUD                  │
│                                                           │
│  📁 devices/{deviceId}                                    │
│     ├── telemetry: { model, battery, ram, last_seen, ... }│
│     ├── command:   { type: "LOCK", sig: "a8f9...", ts }   │
│     └── logs/      { timestamp, tag, message, trace }     │
└─────────────────────────────┬─────────────────────────────┘
                              │
                    (Snapshot Listener)
                              ▼
┌───────────────────────────────────────────────────────────┐
│                  ANDROID APK (CuanCame)                   │
│                                                           │
│  1. RemoteTelemetryService: Kirim Heartbeat (60s)         │
│  2. RemoteCommandHandler: Validasi HMAC -> Eksekusi       │
│  3. CrashReporterHandler: Tangkap uncaught exception & log│
│  4. FloatingControlService & MainHook: Terapkan Kunci     │
└───────────────────────────────────────────────────────────┘
```

#### Struktur Koleksi Firestore:
1. `devices/{deviceId}`:
   - `model`: Nama pabrikan & model perangkat (misal: "Samsung Galaxy A12").
   - `androidVersion`: Versi OS Android.
   - `appVersion`: Versi rilis CuanCame.
   - `lastSeen`: ServerTimestamp (waktu terakhir online).
   - `batteryLevel`: Persentase baterai saat ini.
   - `ramUsageMb`: Konsumsi memori RAM terkini.
   - `licenseExpiry`: Tanggal kedaluwarsa lisensi sewa.
   - `currentVideo`: Nama file MP4 yang sedang diputar.
   - `activeCommand`: Objek perintah terkini (`{ action: "LOCK"|"DECOY"|"MESSAGE", payload: "...", signature: "...", timestamp: 1729... }`).
2. `devices/{deviceId}/logs/{logId}`:
   - `timestamp`: Waktu pencatatan.
   - `level`: `INFO`, `WARN`, atau `CRASH`.
   - `tag`: Modul pelapor (misal: `GlVideoRenderer`, `CodecGuard`, `LiveCommentParser`).
   - `message`: Deskripsi kesalahan atau ringkasan exception.

#### Komponen yang Akan Dibuat/Dimodifikasi:
1. **Sisi Android (Kotlin)**:
   - `com.tajasb.remote.RemoteTelemetryManager.kt`: Bertanggung jawab mengirim heartbeat periodik, status baterai/RAM, dan mendengarkan instruksi remote via Firestore.
   - `com.tajasb.remote.RemoteCommandValidator.kt`: Memverifikasi tanda tangan HMAC perintah agar anti-hack sebelum instruksi diteruskan ke sistem.
   - `com.tajasb.debug.CrashReporter.kt`: Mengaitkan `Thread.setDefaultUncaughtExceptionHandler` dan filter log kritis untuk dikirim ke Firestore.
2. **Sisi Bot Telegram (Python)**:
   - `tools/telegram_bot.py`: Penambahan modul Dashboard interaktif lengkap dengan Inline Keyboard untuk melihat status seluruh perangkat aktif, tombol aksi cepat (Lock/Decoy/Message), dan notifikasi otomatis saat ada perangkat yang mengalami crash.
