# Integrasi Penangkapan Event Riil Bigo Live via Login Akun (Chat, Join, Like, Gift)

Rencana arsitektur dan eksekusi untuk menangkap seluruh interaksi riil room siaran Bigo Live (komentar chat penonton, join room, tap like, dan saweran gift) menggunakan autentikasi login (QR Code / Session Cookie di Termux) serta menghubungkannya secara instan dan tanpa jeda ke game interaktif Tarik Tambang dan Raja Singgasana.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Pilihan Terkonfirmasi dari Tanya Jawab Tahap 1:**
> - **Metode Penangkapan**: Login Akun Web Bigo via QR Code atau Cookie di Termux (didukung portal Web Danmaku Ingestion langsung).
> - **Cakupan Event yang Ditangkap**: Semua interaksi riil (Komentar Chat kubu, Penonton Masuk/Join, Tap Like layar, dan Hadiah Saweran/Gift).

---

## 1. Overview & Core Concept

### Masalah yang Diselesaikan
Sebelumnya, penangkapan event Bigo di Termux hanya mengandalkan polling profil web publik (`https://www.bigo.tv/<BigoID>`) yang hanya memperbarui total akumulasi Beans. Karena protokol live chat dan WebSocket Bigo dienkripsi dan membutuhkan sesi pengguna (session token), siaran tampak "sepi" dan game tidak bergerak meskipun penonton di room sedang ramai mengetik chat, bergabung (join), dan tap love.

### Solusi yang Dibangun
1. **Bigo Session & Login Manager di Termux (`tools/bigo_event_server.py`)**:
   - Menu Login Akun Bigo dengan opsi **Scan QR Code Terminal** atau **Input Session Cookie / Ticket Web Bigo**.
   - Menyimpan sesi aman secara lokal di `~/.tajasb_bigo_session.json` sehingga tidak perlu login ulang tiap sesi siaran.
2. **Kolektor Danmaku Live Riil (Real-Time Danmaku Client)**:
   - Menghubungkan sesi terautentikasi ke jalur streaming data room Bigo Live resmi.
   - Menangkap seluruh jenis paket interaksi secara presisi:
     - **Komentar Chat**: Membaca teks penonton secara instan (kata kunci `1`, `2`, `kiri`, `kanan`, atau nama tim).
     - **Penonton Masuk (Join / Enter Room)**: Mencatat penonton baru yang hadir, menyapa di layar arena, dan memberi dorongan energi.
     - **Tap Like (Hati / Love Layar)**: Mengonversi frekuensi tap like penonton menjadi gaya tarikan tali atau perisai takhta.
     - **Saweran Gift (Diamonds & Beans)**: Menghitung nilai gift dan langsung memicu tarikan masif serta kudeta takhta.
3. **Portal Web Danmaku Relay (`http://<IP>:8765/live`)**:
   - Sebagai jalur alternatif instan, server Termux menyediakan halaman jembatan web ringan yang bisa dibuka di browser HP/PC jika pengguna ingin login interaktif via browser web tanpa mengetik cookie manual.
4. **Respon Cepat di Game Engine Android (Device 2)**:
   - Menerima dan memproses seluruh event baru (`chat`, `join`, `like`, `gift`) dengan feedback visual, haptic, dan animasi papan skor 60 FPS.

---

## 2. User Experience & Visual Design

### Alur Pengguna (User Flow)
```
[Buka Termux HP 1] ───► Pilih [L] Login Akun Bigo (QR Code / Cookie)
                             │
                             ▼
               [Sesi Bigo Terautentikasi Aktif]
                             │
                             ▼
         [Buka Menu 1: Mulai Event Broker Bigo Live]
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
   [Danmaku Stream Aktif]           [APK Android CuanCame Tersambung]
   • Chat mengalir real-time        • Indikator: 🟢 TERHUBUNG
   • Join penonton tercatat         • Tali Tambang bergerak dinamis
   • Tap like menambah poin         • Takhta Sultan diserang/dibela
   • Gift memicu tarikan besar      • Ticker: Riwayat aksi interaktif
```

### Feedback Visual & Interaksi di Layar Game
- **Banner Aksi Real-Time (Ticker)**:
  - Chat: `💬 [Nama Penonton]: Tarik Tim Kiri!`
  - Join: `👋 [Nama Penonton] Bergabung ke Arena!`
  - Like: `❤️ [Nama Penonton] Tap Like x10!`
  - Gift: `🎁 [Nama Penonton] Sawer [Nama Gift] (+Poin Besar)!`
- **Animasi Tali & Efek Kudeta**:
  - Tali bergetar dengan partikel kilat saat menerima rentetan chat penonton.
  - Efek goncangan perisai dan ledakan koin emas saat gift masuk.

---

## 3. Key Product Decisions & Trade-Offs

| Keputusan | Pendekatan Terpilih | Alasan & Keuntungan | Alternatif yang Ditinggalkan |
|---|---|---|---|
| **Metode Autentikasi** | Hybrid: Terminal QR Code / Cookie + Web Danmaku Relay | Praktis bagi pengguna Termux, tidak memerlukan root atau modifikasi APK Bigo resmi | Reverse engineering protokol biner proprietary yang rentan banned |
| **Penyimpanan Kredensial** | File lokal terenkripsi di sandbox Termux (`~/.tajasb_bigo_session.json`) | Aman, privat di perangkat sendiri, dan otomatis tersambung saat restart Termux | Menyimpan di cloud publik (berisiko privasi) |
| **Pemrosesan Komentar** | Toleransi Fuzzy Chat (angka `1`/`2`, nama tim, emoji, atau vote bebas) | Memastikan 100% komentar penonton terhitung menjadi pergerakan game, bukan diabaikan | Hanya mencocokkan teks kaku yang membuat room terasa sepi |

---

## 4. Technical Architecture & Data Strategy

### Diagram Arsitektur Sistem
```
┌────────────────────────────────────────────────────────────────────────┐
│                          SERVER BIGO LIVE                              │
│       • Web Danmaku WebSocket (wss://ta.bigo.tv)                       │
│       • Session Auth Endpoint (QR / Cookie Verify)                     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Sesi Terautentikasi
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   TERMUX BROKER (DEVICE 1: Python)                     │
│  ┌───────────────────────┐   ┌──────────────────────────────────────┐  │
│  │ Bigo Session Manager  │   │ Real-time Danmaku Parser             │  │
│  │ (QR Code & Cookies)   │──►│ • Chat • Join • Tap Like • Gift      │  │
│  └───────────────────────┘   └──────────────────┬───────────────────┘  │
│                                                 │                      │
│                                                 ▼                      │
│                                  ┌───────────────────────────────┐     │
│                                  │ WebSocket Server (Port 8765)  │     │
│                                  └──────────────┬────────────────┘     │
└─────────────────────────────────────────────────┼──────────────────────┘
                                                  │ ws://IP:8765
                                                  ▼
┌────────────────────────────────────────────────────────────────────────┐
│                 APLIKASI ANDROID CUANCAME (DEVICE 2)                   │
│  ┌───────────────────────────────┐   ┌──────────────────────────────┐  │
│  │ LiveEventReceiver (WS Client) │──►│ Interactive Game Engines     │  │
│  └───────────────────────────────┘   │ • Tug of War (Tarik Tambang) │  │
│                                      │ • King of Throne (Singgasana)│  │
│                                      └──────────────┬───────────────┘  │
│                                                     │ StateFlow (60 FPS)
│                                                     ▼                  │
│                                      ┌──────────────────────────────┐  │
│                                      │ Fullscreen Arena / VCam UI   │  │
│                                      └──────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Komponen yang Dimodifikasi & Ditambahkan
1. **`tools/bigo_event_server.py`**:
   - Menambahkan modul `BigoSessionAuth` (generate QR terminal, simpan/muat cookies, validasi status login).
   - Menambahkan worker `BigoDanmakuWorker` yang memproses aliran event live room secara real-time.
   - Memperluas menu CLI Termux dengan opsi `[Q] Login QR / Cookie Bigo` dan status indikator sesi login di header dashboard.
2. **`LiveEventReceiver.kt` (Android)**:
   - Memastikan event tipe `join`, `member_enter`, dan `like_batch` dipetakan ke event internal.
3. **`InteractiveTugOfWarEngine.kt` & `InteractiveKingOfThroneEngine.kt`**:
   - Menambahkan handler khusus `onViewerJoinEvent()` untuk memunculkan sambutan dan memberi dorongan dinamis.
   - Mengoptimalkan pembobotan `onLikeEvent` agar rentetan tap like penonton langsung terasa menggeser tali.
4. **Verifikasi & Pengujian**:
   - Uji kompilasi kode (`py_compile` dan `compile_applet`).
   - Uji unit testing JVM (`gradle :app:testDebugUnitTest`).
