# Arsitektur 2-Perangkat: Device 1 Server + Controller & Device 2 Mode Live Tanpa Floating

Rencana ini menata ulang sistem pengaturan 2-perangkat agar Anda dapat mengepaskan koordinat seluruh trigger secara bebas di layar Mobile Legends pada **Device 2** saat persiapan, lalu menghilangkan seluruh tombol floating (*Clean Live Mode*) saat siaran langsung dan mengendalikan seluruh engine, rule, preset, dan uji coba dari **Device 1 (Termux)**.

### User Review & Critical Decisions

> [!IMPORTANT]
> Karena pertanyaan klarifikasi pada tahap sebelumnya dilewati, rencana ini menggunakan konfigurasi bawaan yang direkomendasikan untuk skenario live streaming Mobile Legends. Harap tinjau keputusan berikut sebelum menyetujui rencana:

- **Mode Live Bersih di Device 2 (Default Direkomendasikan)**: Saat Mode Live Bersih aktif, jendela tombol kontrol HUD dan panel pengaturan dihapus total dari layar, sementara stage visual tembus sentuhan (`FLAG_NOT_TOUCHABLE`) tetap dapat menampilkan efek visual di koordinat yang sudah dipaskan. Tersedia pula saklar **Audio-Only / Senyap Visual** jika Anda ingin layar benar-benar 100% kosong dari popup saat live.
- **Kontroler Jarak Jauh Ganda di Device 1 (Default Direkomendasikan)**: Server Termux di Device 1 akan menyediakan **Web Dashboard LAN** (dibuka di browser Device 1 pada port yang sama `8765`) sekaligus **shortcut perintah cepat di terminal Termux** agar Anda bebas memilih antarmuka kontrol paling nyaman.
- **Jalur Sinkronisasi Dua Arah**: Perubahan posisi pin maupun rule di Device 2 otomatis dikirim ke Device 1 agar peta koordinat dan daftar rule di Controller Device 1 selalu sinkron secara *real-time*.

---

### 1. Overview & Core Concept

- **What It Does**: Memisahkan siklus penggunaan aplikasi menjadi dua fase tegas—**Fase Setup/Kalibrasi** di Device 2 (memunculkan pin crosshair yang dapat digeser ke posisi mana saja di atas Mobile Legends) dan **Fase Live Streaming** (menyembunyikan seluruh kontroler floating di Device 2 dan memindahkan kendali penuh ke Device 1 sebagai Server + Controller).
- **Target Audience / Persona**: Streamer Mobile Legends di TikTok Live / Bigo Live yang menggunakan dua HP (Device 1 sebagai penerima webcast Termux & meja kontroler operator, Device 2 khusus untuk bermain Mobile Legends tanpa gangguan UI tombol melayang).
- **Key Value**:
  - Layar Mobile Legends di Device 2 bersih total dari tombol kontrol melayang sehingga tidak ada salah pencet (*misclick*) saat war.
  - Koordinat trigger tetap akurat mengikuti posisi yang sudah dikalibrasi sebelumnya (pojok minimap, atas scoreboard, area recall, dll).
  - Operator atau host tetap bisa menyalakan/mematikan rule, mengganti preset, mengaktifkan proteksi match, atau memicu efek manual langsung dari Device 1.

---

### 2. User Experience & Visual Design

#### Key User Flows

1. **Alur Setup & Kalibrasi Posisi di Device 2 (Sebelum Live)**:
   - Pengguna membuka aplikasi di Device 2 ➔ Tab **MLBB** ➔ menyalakan saklar **Floating Setup Mode**.
   - Pengguna membuka Mobile Legends dan mengetuk **Atur Posisi** pada HUD melayang.
   - Pin crosshair (`⊕ 📍`) muncul untuk setiap rule dan dapat digeser bebas ke mana saja di atas layar Mobile Legends (mendukung orientasi *landscape* game penuh dan menyimpan koordinat ternormalisasi `0.03–0.97` pada sumbu X/Y serta skala `0.5x–2.0x`).
   - Setelah pas, pengguna mengetuk **Kunci & Masuk Mode Live Bersih** (atau memicu perintah dari Device 1). Seluruh tombol HUD dan pin langsung hilang dari layar Device 2.
2. **Alur Saat Live Berlangsung (Device 2 Bersih, Device 1 Mengontrol)**:
   - Di Device 1 (Termux), pengguna menjalankan TikTok/Bigo Event Server seperti biasa, lalu membuka alamat Web Controller lokal (mis. `http://localhost:8765/controller` di browser Device 1 atau IP LAN dari perangkat lain).
   - Setiap event penonton (`Chat`, `Gift`, `Like`, `Join`, `Follow`, `Share`, `Order`) dikirim ke Device 2 via WebSocket dan dieksekusi di posisi X/Y yang telah dikalibrasi—tanpa menampilkan satu pun tombol floating di layar Device 2.
   - Dari Web Controller Device 1, operator dapat: melihat pratinjau peta titik koordinat seluruh rule, menyalakan/mematikan rule tertentu, mengganti preset, mengaktifkan *Match-Safe Mode*, memicu tombol **Stop Darurat**, atau menguji/menembakkan trigger secara manual.
   - Jika suatu saat perlu menggeser posisi pin lagi di tengah live, operator cukup menekan **Buka HUD Setup di Device 2** dari Device 1 (atau lewat tombol notifikasi Android di Device 2).

#### Visual Identity & Theme

- **Aesthetic Direction**: *Dark Tactical Broadcast Control* — tampilan gelap berpresisi tinggi yang kontras, cepat dipindai saat pertandingan berlangsung, dan selaras dengan tema aplikasi saat ini.
- **Color Palette & Mood**:
  - *Device 2 (Android Material 3)*: Menggunakan token tema utama `DarkBackground` (`#070B14`), `DarkSurface` (`#0F172A`), `DarkSurfaceVariant` (`#1E293B`), serta aksen fungsional `NeonCyan` (`#22D3EE`) untuk kontrol aktif, `EmeraldGreen` (`#10B981`) untuk status terhubung/aman, dan `NeonOrange` (`#F97316`) untuk peringatan/Match-Safe.
  - *Device 1 (Web Controller Dashboard)*: Mengadopsi palet senada (*slate dark* `#070B14` / `#0F172A`, aksen cyan `#22D3EE` dan emerald `#10B981`) agar perpindahan perhatian antar kedua perangkat terasa menyatu.
- **Typography & Hierarchy**:
  - Tipografi tubuh menggunakan font lokal `Inter` dengan hirarki tegas: label status koneksi ukuran kompak, angka koordinat dan statistik menggunakan monospace/tabular, serta tombol aksi utama berukuran besar agar mudah disentuh operator.
- **Component Styling & Layout**:
  - Pada Device 2 (Tab **MLBB**): Pemisahan kartu saklar menjadi **Mode Operasi Saat Live** (*Tanpa Floating HUD / Tampilkan Efek Saja / Audio-Only*) dan **Alat Setup Kalibrasi** (*Buka HUD & Pin Posisi*). Mendukung *edge-to-edge* penuh dengan `WindowInsets.safeDrawing` / `navigationBarsPadding()` dan target sentuh minimal `48.dp`.
  - Pada Device 1 (Web Controller): Layout responsif satu kolom di HP dan grid 2-kolom di layar lebar, dilengkapi *Live Stage Mini-Map* rasio `16:9` (landscape Mobile Legends) yang memplot titik koordinat setiap rule secara visual.
- **Interactive Feedback & Motion**:
  - Transisi status mode (`Setup HUD` ↔ `Live Bersih`) di Device 2 menampilkan umpan balik ringkas via toast/status bar dan langsung menyinkronkan badge status ke Device 1 dalam hitungan milidetik.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Pemisahan Layer Window Overlay di Device 2 (`LiveModeProfile`)**
  - *Chosen Approach*: Menambahkan profil tampilan overlay (`SETUP_WITH_HUD`, `LIVE_EFFECTS_ONLY`, dan `LIVE_AUDIO_ONLY`) di samping status layanan latar depan. Pada mode `LIVE_EFFECTS_ONLY`, jendela tombol HUD (`controlHudView`) dan panel pengaturan dilepas total dari `WindowManager`, menyisakan hanya layer stage non-interaktif (`FLAG_NOT_TOUCHABLE`). Pada mode `LIVE_AUDIO_ONLY`, seluruh window overlay dilepas sehingga Device 2 hanya memutar audio/aksi tanpa elemen visual apa pun.
  - *Why*: Menjawab kebutuhan "ketika live gamau ada floating" tanpa mematikan kemampuan engine untuk memproses event Termux atau menampilkan efek di koordinat yang sudah dipaskan.
  - *Alternatives Considered*: Memperkecil HUD menjadi ikon mini mengambang. Ditolak sebagai mode live utama karena ikon mini pun masih berisiko menutupi skill/minimap atau tersentuh tidak sengaja saat bermain Mobile Legends.

- **Decision 2: Device 1 Berperan Sebagai Server + Controller Lewat Port Tunggal (`8765`)**
  - *Chosen Approach*: Memperluas server WebSocket bawaan pada skrip Termux TikTok dan Bigo agar juga melayani halaman HTTP **Web Controller** dan endpoint kontrol di port yang sama (`8765`), sambil menambahkan pintasan keyboard di menu terminal Termux.
  - *Why*: Streamer tidak perlu membuka banyak port, tidak perlu instalasi web framework eksternal di Termux, dan cukup terhubung ke satu koneksi WebSocket antara Device 1 dan Device 2.
  - *Alternatives Considered*: Membuat aplikasi Android controller terpisah untuk Device 1. Ditolak karena Device 1 umumnya sudah menjalankan skrip Python di Termux; menyajikan Web Controller lokal langsung dari skrip Termux jauh lebih ringan, instan, dan bisa diakses dari browser mana pun di jaringan lokal.

- **Decision 3: Protokol Sinkronisasi State & Perintah Dua Arah (`mlbb_state_sync` & `mlbb_control`)**
  - *Chosen Approach*: Memanfaatkan jalur WebSocket LAN yang sudah aktif (`LiveEventReceiver`) untuk mengirim snapshot ringkas daftar rule, posisi `(posX, posY, scale)`, preset aktif, status engine, dan mode floating dari Device 2 ke Device 1, serta menerima perintah remote (`set_live_mode`, `toggle_rule`, `update_rule_pos`, `apply_preset`, `set_engine`, `set_match_safe`, `test_rule`, `emergency_stop`) dari Device 1 ke Device 2.
  - *Why*: Menjamin koordinat yang baru saja dipaskan di layar Mobile Legends Device 2 langsung muncul di peta koordinat Controller Device 1, dan kontrol dari Device 1 berlaku seketika (<5ms LAN) tanpa menyentuh layar Device 2.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│ DEVICE 1 (TERMUX: EVENT SERVER + REMOTE CONTROLLER)                        │
│                                                                            │
│  ┌──────────────────────────┐       ┌───────────────────────────────────┐  │
│  │ TikTok / Bigo Webcast    │       │ Remote Controller UI              │  │
│  │ Stream Reader            │       │ • Web Dashboard (:8765/controller)│  │
│  └────────────┬─────────────┘       │ • Shortcut Terminal Termux        │  │
│               │                     └─────────────────┬─────────────────┘  │
│               ▼                                       │                    │
│  ┌────────────────────────────────────────────────────▼─────────────────┐  │
│  │ Unified Termux HTTP + WebSocket Broker (Port 8765)                   │  │
│  │ • Broadcast Live Event ➔ Device 2                                    │  │
│  │ • Simpan Cache State MLBB Terbaru (Rules, Presets, Mode, Koordinat)  │  │
│  │ • Teruskan Perintah Kontrol (`mlbb_control`) ➔ Device 2              │  │
│  └──────────────────────────────────┬───────────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────────┘
                                      │ WebSocket LAN (<5ms, dua arah)
┌─────────────────────────────────────▼──────────────────────────────────────┐
│ DEVICE 2 (ANDROID: MOBILE LEGENDS + MLBB TRIGGER STUDIO)                   │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ LiveEventReceiver (WS Client)                                        │  │
│  │ • Terima Event Penonton ➔ UniversalTriggerEngine                     │  │
│  │ • Terima Perintah `mlbb_control` ➔ Eksekusi / Ubah Mode / Rule       │  │
│  │ • Kirim `mlbb_state_sync` ➔ Device 1 tiap kali posisi/rule berubah   │  │
│  └──────────────┬────────────────────────────────────────┬──────────────┘  │
│                 ▼                                        ▼                 │
│  ┌──────────────────────────────┐         ┌─────────────────────────────┐  │
│  │ UniversalTriggerEngine       │         │ Overlay Window Service      │  │
│  │ • Evaluasi Rule & Counter    │────────▶│ Mode LIVE_NO_HUD:           │  │
│  │ • Simpan Posisi X/Y & Preset │         │  • Kontrol HUD = MATI       │  │
│  └──────────────────────────────┘         │  • Pin Edit    = MATI       │  │
│                                           │  • Stage Efek  = Pass-Thru  │  │
│                                           │ Mode SETUP_FLOATING:        │  │
│                                           │  • HUD + Pin Drag = AKTIF   │  │
│                                           └─────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

#### Data Model & State

- **LiveDisplayMode (Profil Tampilan Device 2)**:
  - `SETUP_FLOATING`: Memunculkan HUD kontrol kecil, panel pengaturan melayang, dan pin drag posisi di atas Mobile Legends untuk kalibrasi.
  - `LIVE_EFFECTS_ONLY`: Melepas seluruh HUD kontrol dan pin edit dari layar; hanya menyisakan stage visual non-interaktif (`FLAG_NOT_TOUCHABLE`) di koordinat target yang sudah dipaskan.
  - `LIVE_AUDIO_ONLY`: Melepas semua window overlay visual dari layar (layar 100% bersih), hanya menjalankan pemicu audio/logika di latar belakang.
- **Remote Sync Snapshot (`mlbb_state_sync`)**:
  - Berisi: `liveDisplayMode`, `isEngineEnabled`, `isMlbbMatchActive`, `activeSceneName`, `isFloatingOverlayRunning`, `presets` (`id`, `name`), dan `rules` (`id`, `enabled`, `triggerSource`, `triggerName`, `overlayPosX`, `overlayPosY`, `overlayScale`, `assignedPresetId`).
- **Remote Control Payload (`mlbb_control`)**:
  - Aksi standar: `set_live_mode`, `set_engine_enabled`, `set_match_safe`, `toggle_rule`, `update_rule_position`, `apply_preset`, `trigger_rule_test`, `inject_event`, `emergency_stop`, dan `request_state_sync`.

#### Interactive Component & State Mapping

- **Kontrol Mode Live di Layar Utama Device 2 (Tab MLBB)**:
  - Menyediakan pemilih mode cepat: **Setup Posisi (Floating)** vs **Live Bersih (Tanpa HUD)** vs **Live Audio-Only**.
  - Saat pengguna mengetuk **Mulai Mode Live Bersih**, aplikasi mengaktifkan engine + service tanpa HUD kontrol dan mengizinkan pengguna langsung beralih ke Mobile Legends (atau meluncurkannya).
  - Notifikasi latar depan menampilkan dua aksi cepat: **Munculkan HUD Setup** (mengembalikan mode ke `SETUP_FLOATING`) dan **Stop Darurat**.
- **Panel Floating Setup di Device 2**:
  - Menambahkan tombol satu-ketuk **Kunci & Sembunyikan HUD (Masuk Mode Live)** di bar aksi cepat HUD/Panel agar setelah selesai menggeser pin di atas Mobile Legends, pengguna bisa langsung menghilangkan HUD tanpa keluar dari game.
- **Web Controller & Menu Terminal di Device 1**:
  - Ketika Device 2 terhubung ke WebSocket Termux, Device 2 langsung mengirim `mlbb_state_sync`.
  - Operator di Device 1 melihat status real-time dan dapat mengklik toggle rule, menggeser koordinat X/Y pada preview 16:9, mengganti preset, menekan tombol tes event, atau mengubah `liveDisplayMode` Device 2 dari jarak jauh.
- **Keamanan & Ketahanan Koneksi**:
  - Jika koneksi LAN sempat terputus lalu tersambung kembali (*auto-reconnect*), Device 2 langsung mengirimkan snapshot terbaru ke Device 1 dan tetap menjaga agar floating HUD tidak muncul tiba-tiba selama `liveDisplayMode` terkunci pada Mode Live Bersih.
