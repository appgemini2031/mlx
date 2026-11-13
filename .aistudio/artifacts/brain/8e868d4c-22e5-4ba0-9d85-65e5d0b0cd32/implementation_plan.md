# Game Interaktif: BeyBattle Royale (Arena Gangsing Tempur TikTok Live)

Rencana perancangan dan implementasi Game Interaktif Battle Royale Gangsing berputar (*Spinning Top / Beyblade Arena*) **eksklusif dan fokus 100% untuk TikTok Live**. Integrasi Bigo Live dieliminasi sepenuhnya agar sistem tetap ringan, sederhana, dan tidak ribet. Game ini dirancang agar penonton gratisan dapat langsung bertanding sejak awal siaran (bahkan saat penonton belum ramai), sekaligus memicu penonton mengirim saweran koin TikTok secara berjenjang.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Penyesuaian Berdasarkan Umpan Balik (Revisi)**:
> 1. **Fokus Tunggal TikTok Live (Bigo Dihapus Total)**: Seluruh arsitektur penerimaan event, format koin, dan tipe hadiah disederhanakan khusus untuk ekosistem TikTok Live. Tidak ada dependensi atau alur login rumit Bigo.
> 2. **Mekanisme Pemain Otomatis (Spawn Instan)**: Semua penonton yang masuk live TikTok (`join`), tap-tap like di layar, atau mengirim chat otomatis langsung muncul menjadi gangsing di arena.
> 3. **Eliminasi Ganda**: Pemain terlempar keluar lingkaran ring arena (*Ring-Out*) atau kehabisan daya putaran (*Spin HP / RPM = 0*).
> 4. **Pemicu Gift Koin TikTok**: Hadiah gift koin TikTok (1 koin mawar hingga gift sultan) memberikan pembesaran ukuran gangsing, daya pantul raksasa (*Shockwave*), perisai api/petir, dan pemulihan stamina instan.

- **Status Integrasi**: Bersifat *mutual-exclusive* dengan modul game lain (Tug of War, King of Throne, dan Khataman Al-Qur'an). Saat BeyBattle Royale aktif, modul lain otomatis standby secara aman tanpa beban ganda.
- **Setelan Default Awal**: Default **OFF** saat aplikasi dibuka agar layar kamera tetap bersih murni sampai host mengaktifkannya dari Floating Window atau menu utama.

---

## 1. Overview & Core Concept

### Konsep Utama
Arena piringan stadium 2D futuristik berkecepatan 60 FPS di mana gangsing-gangsing penonton TikTok saling berbenturan dan bertarung hingga tersisa satu pemenang (*Last Top Standing*). Nama username dan foto profil penonton TikTok otomatis terpasang di atas cakram gangsing yang berputar kencang.

### Strategi Menghidupkan Siaran Live Sepi
1. **Semua Penonton Langsung Jadi Pemain**: Penonton baru yang baru membuka siaran langsung TikTok langsung kaget dan senang melihat avatar profil dan namanya tiba-tiba terjun (*drop spawn*) ke tengah arena dan mulai berputar.
2. **Keterlibatan Gratisan yang Seru**:
   - Mengetuk layar (Tap-Tap Like) menjaga gangsingnya tetap berputar kencang dan menariknya ke arah tengah arena agar tidak mudah terlempar keluar.
   - Mengirim komentar di chat TikTok memicu *Turbo Dash / Tackle Strike*, membuat gangsingnya melesat menabrak lawan terdekat!
3. **Pemicu Saweran Alami (Gift Trigger)**: Saat penonton gratisan mulai saling serang dan bertahan sengit, penonton yang ingin menang mutlak atau gengsi akan langsung terdorong mengirim gift koin TikTok (Mawar, Donat, Parfum, Singa, dll.) untuk mengubah gangsingnya menjadi raksasa tak terkalahkan.

---

## 2. Mekanisme Gameplay & Pemetaan Kekuatan

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ARENA BEYBATTLE ROYALE (TIKTOK LIVE)                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   [Penonton A]                 [Penonton B]                 [Penonton C]    │
│    (Gratisan)                   (Gratisan)                  (Gift 10 Koin)  │
│     🌀 Putar                     🌀 Putar                     💥 TITAN TOP  │
│  (Tap: +RPM/HP)               (Komen: Dash)                (Shockwave Aura) │
│                                                                             │
│                 ─────────► [ Benturan Fisik ] ◄─────────                    │
│                                                                             │
│   ❌ TERLEMPAR KELUAR RING (RING OUT)    ==>  ELIMINASI!                     │
│   ❌ DAYA PUTARAN HABIS (HP = 0)         ==>  ELIMINASI!                     │
│                                                                             │
│   👑 TERAKHIR BERDIRI                    ==>  JUARA BATTLE ROYALE + TROFI   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### A. Penonton Gratisan (Tanpa Gift)
- **Tap-Tap Like TikTok**:
  - Memberikan +1.5 HP Stamina Putaran (RPM naik).
  - Memberikan gaya tarik mikro ke arah titik pusat (*Center Gravity Pull*), membantu gangsing bertahan di dekat tengah ring dan menjauh dari bibir jurang.
  - Memancarkan percikan api gesekan emas (*golden sparks*).
- **Komentar Chat TikTok**:
  - Setiap chat yang masuk langsung memicu *Turbo Dash / Tubrukan Kilat* ke arah gangsing lawan terdekat.
  - Benturan keras akibat komentar mengurangi HP lawan dan mendorong lawan ke arah tepi ring.
  - Memancarkan efek jejak angin meluncur (*wind trail*) dan getaran mikro (*micro-shake*).

### B. Tingkatan Efek Saweran Gift Koin TikTok

| Tier Koin TikTok | Contoh Gift Populer | Efek Pertarungan di Arena | Efek Visual & Transformasi |
| :--- | :--- | :--- | :--- |
| **Tier 1 (1 - 9 Koin)** | Mawar (Rose), Donat, Jari Hati, Kopi | **Stamina Pulih 100% + Anti-Mental**: HP putaran pulih maksimal, menepis dorongan lawan selama 3 detik. | Ukuran membesar +15%, aura pelindung biru melingkari gangsing. |
| **Tier 2 (10 - 99 Koin)** | Parfum, Balon Udara, Confetti, Kacamata | **Titan Blade + Shockwave Blast**: Menghasilkan gelombang kejut yang mementalkan seluruh gangsing di sekitarnya mundur sejauh 30% ring; daya dorong benturan berlipat ganda. | Ukuran membesar +40%, bilah api merah berkobar (*Inferno Blades*) dengan jejak api di lantai arena. |
| **Tier 3 (100 - 499 Koin)** | Paus Kecil, Topi Karnaval, Hati Kasih | **Mega Thunder Juggernaut**: Menjadi gangsing raksasa kebal dorongan; benturan dapat langsung mementalkan gangsing lawan keluar ring (*Instant Knockout*). | Ukuran membesar +75%, pusaran petir ungu elektrik (*Lightning Vortex*) menyambar-nyambar di atas avatar. |
| **Tier 4 (500+ Koin)** | Singa (Lion), Paus Terbang, Interstellar, Nebula | **Galaxy Emperor (Dewa Ring TikTok)**: Efek hisapan lubang hitam (*Black Hole*) selama 1 detik yang menyedot lawan, lalu meledakkan gelombang kosmik dahsyat yang menyapu bersih seluruh arena! | Ukuran raksasa 2.2x lipat berbalut emas murni, mahkota berputar, dan ledakan kosmik spektakuler. |

### C. Alur Ronde & Penentuan Pemenang
1. **Kapasitas Arena**: Menampung hingga 25 gangsing aktif sekaligus di arena. Jika penonton lebih banyak, pemain yang tereliminasi digantikan oleh antrean penonton lain secara bergilir.
2. **Kondisi Kalah**: Terlempar keluar batas lingkaran piringan (*Ring-Out*) atau kehabisan daya putaran (*HP = 0*).
3. **Kondisi Menang**: Tersisa 1 gangsing terakhir di arena.
4. **Selebrasi Juara**: Layar menampilkan banner perayaan *"VICTORY ROYALE: [USERNAME]"* selama 8 detik lengkap dengan piala emas, avatar juara di podium, dan efek kembang api.
5. **Auto-Respawn Ronde Baru**: Setelah perayaan juara, arena otomatis memulai ronde baru tanpa jeda manual, me-respawn seluruh penonton aktif untuk duel berikutnya.

---

## 3. Arsitektur Komponen Teknis

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   ARSITEKTUR BEYBATTLE ROYALE (TIKTOK ONLY)                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   [ TikTok Live Bridge / Relay ] (Local Event Node / WebSocket)             │
│          │                                                                  │
│          ▼                                                                  │
│   [ LiveEventReceiver ]                                                     │
│          ├── onJoinEvent   ──► spawn gangsing baru (nama + avatar)          │
│          ├── onLikeEvent   ──► +RPM / +HP / dorongan ke tengah ring         │
│          ├── onChatEvent   ──► Turbo Dash dorongan ke lawan terdekat        │
│          └── onGiftEvent   ──► Mega Titan Transform / Shockwave sesuai koin │
│                                                                             │
│   [ InteractiveBeyBattleEngine ] (Singleton Game Engine 60 FPS)             │
│          ├── Loop Fisika 2D (Tabrakan elastis, gesekan RPM, gravitasi)      │
│          ├── Manajemen Pemain (StateFlow<BeyBattleState>)                   │
│          └── Simulator Offline (Tes bot, like, chat, gift tanpa siaran)     │
│                                                                             │
│   ┌──────────────────────────────┴──────────────────────────────┐           │
│   ▼                                                             ▼           │
│ [ BeyBattleArenaScreen ]                           [ OverlayBannerBitmap ]  │
│ Layar Penuh Device 2 (Full Game)                   VCam Overlay Transparan  │
│ (Hardware Canvas 60 FPS, UI Neon, Efek)            (Kamera Virtual Android) │
│                                                                 ▲           │
│                                                                 │           │
│   [ FloatingControlService & Tab Game ] ────────────────────────┘           │
│   Saklar ON/OFF, Buka Layar Penuh, Reset Ronde, Posisi X/Y, Zoom            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Rencana Eksekusi Bertahap

### Tahap 1: Pembuatan Data Model & Game Engine
- Membuat `com/tajasb/game/BeyBladeModels.kt`:
  - `BeyBladePlayer`: ID, nama akun TikTok, avatar URL/Base64, posisi koordinat `(x, y)`, vektor kecepatan `(vx, vy)`, radius fisik, sudut putaran, RPM/HP stamina, status gift tier, durasi perisai, efek partikel, dan K.O count.
  - `BeyBattleState`: status aktif, ronde saat ini, daftar pemain di ring, daftar eliminasi, juara ronde, timer selebrasi, posisi dan skala VCam overlay.
- Membuat `com/tajasb/game/InteractiveBeyBattleEngine.kt`:
  - Singleton pengelola fisika tabrakan elastis 2D dan hukum kekekalan momentum.
  - Integrasi auto-spawn penonton TikTok saat join/chat/like/gift.
  - Simulator interaktif mandiri (bisa spawn bot penguji, tes like, chat, dan gift tanpa perlu terhubung ke TikTok live nyata).
  - Proteksi *mutual-exclusive* terhadap modul game lain.

### Tahap 2: Integrasi Penerimaan Event TikTok di `LiveEventReceiver.kt`
- Mengaitkan event TikTok murni ke `InteractiveBeyBattleEngine`:
  - `onJoinEvent` -> `engine.onJoinEvent(name, avatarUrl)`
  - `onLikeEvent` -> `engine.onLikeEvent(name, count)`
  - `onChatEvent` -> `engine.onChatEvent(name, text)`
  - `onGiftEvent` -> `engine.onGiftEvent(name, giftName, count, coins, avatarUrl)`

### Tahap 3: Layar Penuh Arena Device 2 (`BeyBattleArenaScreen.kt`)
- Antarmuka visual game layar penuh 60 FPS untuk HP ke-2 menggunakan Jetpack Compose `Canvas`:
  - Piringan stadium futuristik berilusi kedalaman 3D dengan pembatas bercahaya neon.
  - Gangsing berputar dengan efek bilah berputar kencang dan foto avatar penonton di tengah.
  - Efek partikel tabrakan, efek gelombang *Shockwave* saat gift masuk, dan indikator HP melingkar.
  - Panel info atas: Jumlah pemain bertahan (*Survivors*), Juara Terakhir, dan notifikasi aksi live.
  - Tombol aksi simulator cepat untuk pengujian langsung oleh host.

### Tahap 4: Rendering Kamera Virtual VCam (`OverlayBannerBitmapGenerator.kt`)
- Menambahkan fungsi `renderBeyBattleOverlay(...)` pada generator bitmap overlay VCam:
  - Merender ring arena gangsing secara transparan ke dalam feed kamera virtual.
  - Mendukung penyesuaian posisi bebas (X, Y) dan ukuran (Scale/Zoom) agar pas di sudut atau tengah layar siaran.

### Tahap 5: Panel Kontrol di Floating Window & Navigasi Utama
- Menambahkan menu kontrol di Floating Window (`FloatingControlService` / Tab Game):
  - Saklar ON / OFF game.
  - Tombol Buka Layar Penuh (Device 2 Arena).
  - Tombol Reset Ronde / Mulai Ronde Baru.
  - Slider Posisi X/Y dan Ukuran Zoom untuk kamera virtual.
  - Tombol cepat Uji Simulator (Tambah Bot, Tes Komen, Tes Gift).
- Menambahkan rute dan tombol game pada `VcamMainScreen.kt` dan `MainActivity.kt`.

### Tahap 6: Pengujian Unit Test & Verifikasi Kompilasi
- Membuat unit test fungsional di `app/src/test/java/com/tajasb/BeyBattleInteractiveGameTest.kt`:
  - Pengujian auto-spawn penonton TikTok saat join/like/chat.
  - Pengujian kalkulasi tabrakan fisika dan eliminasi Ring-Out saat keluar dari radius arena.
  - Pengujian eskalasi tier koin gift TikTok (efek Shockwave dan pertambahan massa/ukuran).
  - Pengujian penentuan juara dan auto-reset ronde berikutnya.
- Menjalankan `compile_applet` untuk memverifikasi seluruh kode berhasil dikompilasi 100% tanpa error.
