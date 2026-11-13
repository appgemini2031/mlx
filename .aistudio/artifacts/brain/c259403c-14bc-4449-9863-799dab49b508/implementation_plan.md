# Rencana Perbaikan Bug Kritis & Pusat Pengaturan Fleksibel BeyBattle Royale

Memperbaiki bug arena membeku saat siaran ramai (*live rame*), gagal *auto-reset* setelah ronde selesai, kebocoran suara ke luar game, serta merombak antarmuka menjadi layar atas yang bersih total dengan **satu Pusat Pengaturan fleksibel di bagian bawah**.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Rencana ini menyusun ulang arsitektur kontrol agar sesuai dengan seluruh preferensi yang telah Anda konfirmasikan:

- **Layar Atas Bersih Total**: Seluruh tombol di bagian atas layar (Kembali, Pantul/Jatuh, Kotak/Bulat, Suara, Sembunyikan) dihapus dan dipindahkan ke satu bar minimalis di bagian bawah layar.
- **Perbaikan Bug "Diam Saat Live Rame" & Auto-Reset**: Mengatasi *race condition* antar-thread yang menghentikan loop fisika secara diam-diam saat event *Like/Chat/Gift* masuk bersamaan, serta memastikan ronde selalu *auto-reset* baik saat ada juara (1 bertahan) maupun seri/habis semua (0 bertahan).
- **Audio Terpisah & Anti-Bocor**: Memblokir pemrosesan event game saat BeyBattle tidak aktif, menghentikan seluruh suara seketika saat keluar arena, serta memisahkan **Saklar SFX**, **Saklar Announcer TTS**, dan **Slider Volume (0–100%)**.
- **Pengaturan Bebas (Tidak Kaku)**: Menyediakan *Bottom Sheet* **Pusat Pengaturan** terpadu di bawah agar durasi jeda reset, aturan fisika, batas pemain, masing-masing rintangan, audio, hingga elemen HUD dapat Anda atur sesuka hati dan tersimpan permanen.

---

### 1. Overview & Core Concept

#### Akar Masalah Bug yang Ditemukan & Solusinya

| Masalah Dilaporkan | Akar Masalah di Sistem Saat Ini | Solusi Perbaikan |
| :--- | :--- | :--- |
| **Game Diam Saat Live Rame** | `onLikeEvent`, `onChatEvent`, dan `onGiftEvent` berjalan paralel di banyak *coroutine* dan memodifikasi daftar partikel/pemain bersamaan dengan loop fisika 60 FPS tanpa *try-catch* atau *lock*. Saat bentrok, `physicsJob` mati diam-diam sehingga semua gangsing membeku di layar. | Mengamankan loop fisika dengan *exception guard* (`try-catch`), *single-writer synchronization* / *concurrent safe bounds*, serta membatasi kapasitas pemain aktif dengan penggantian aman agar 60 FPS tetap berjalan mulus berapa pun ramainya event. |
| **Tidak Auto-Reset Setelah Selesai** | 1) Saat ronde berakhir seri (`0` pemain bertahan akibat jatuh bersama/trapdoor), logika juara (`size == 1`) tidak terpanggil sehingga arena macet selamanya.<br>2) `resetRoundInternal` tidak memanggil `publishStateUpdate()` dan `lastChampion` sempat tidak di-reset bersih. | Menangani akhir ronde untuk **1 bertahan (Juara)** maupun **0 bertahan (Seri/Draw)** dengan hitung mundur *auto-reset* otomatis (jeda dapat diatur bebas), langsung memperbarui snapshot UI (`publishStateUpdate`), dan opsi *Auto-Spawn Bot* jika pemain kurang dari 2. |
| **Suara Bocor Keluar Game** | `LiveEventReceiver` terus mengirim event ke mesin BeyBattle meski arena sudah ditutup (`!isGameActive`), sehingga *Gift/Meteor/Blizzard* tetap memutar suara TTS & SFX di luar game. Selain itu keluar arena belum memanggil `stopAll()` ke TTS & `AudioTrack`. | Menambahkan gerbang `if (!isGameActive) return` di seluruh penerima event (*Join/Like/Chat/Gift*), memanggil `soundManager.stopAll()` saat keluar arena atau mute, dan membatasi maksimal 4 `AudioTrack` simultan agar audio tidak menumpuk. |
| **Tombol Terpencar & Pengaturan Kaku** | Tombol tersebar di pojok atas dan bawah, sementara parameter penting (jeda reset, kecepatan, damage, batas Like, rintangan per-jenis, visibilitas HUD) terkunci *hardcode*. | Layar atas dikosongkan total. Semua kendali disatukan dalam **Dock Bawah Minimalis** dan **Pusat Pengaturan 4-Tab** yang fleksibel dan tersimpan otomatis. |

---

### 2. User Experience & Visual Design

#### Tata Letak Layar Penuh Baru (Top-Clean & Thumb-First)

```
┌──────────────────────────────────────────────┐
│                                              │
│     (Layar Atas 100% Bersih Tanpa Tombol)    │
│                                              │
│            ╭──────────────────╮              │
│           ╱   ARENA GANGSING   ╲             │
│          │   (60 FPS Kanvas)    │            │
│           ╲                    ╱             │
│            ╰──────────────────╯              │
│                                              │
│  [Opsional: Kill Feed / Ticker / Hitung Mundur]│
├──────────────────────────────────────────────┤
│ [ R3 • 🛡️12 ]  [ ↺ ]  [ ⚙️ ATUR ]  [ ✕ ]    │ ◄── Dock Bawah Super Minimalis
└──────────────────────────────────────────────┘
```

1. **Layar Atas Bersih Total**: Tidak ada lagi deretan tombol di atas layar. Area atas murni untuk tampilan stadium, gangsing, dan latar belakang siaran agar terlihat rapi seperti game *streaming* profesional.
2. **Dock Bawah Super Minimalis**: Hanya 1 kapsul kecil di tepi bawah berisi:
   - Pill info ringkas (`Ronde • Pemain Aktif`) yang bisa diketuk untuk membuka tab *Tes Live* cepat.
   - Tombol **Reset Ronde Cepat (`↺`)**.
   - Tombol **Pusat Pengaturan (`⚙️ ATUR`)** untuk membuka panel lengkap di bawah.
   - Tombol **Mode Bersih / Sembunyikan Dock (`👁️`)** dan **Keluar (`✕`)** dalam satu baris rapi.
3. **Pusat Pengaturan Fleksibel (Bottom Sheet 4 Sub-Tab di Bawah)**:
   - **Tab 1 — Aturan & Reset**:
     - Saklar **Auto-Reset Ronde** (ON/OFF) + Slider **Jeda Selebrasi sebelum Reset** (1 – 15 detik).
     - Saklar **Auto-Isi Bot saat Kurang Pemain** (ON/OFF) + Stepper **Target Minimal Pemain/Bot** (2 – 12).
     - Slider **Batas Maksimal Pemain di Arena** (5 – 40).
     - Slider **Peluruhan Stamina Pasif** (0.0 = Murni Duel s/d 3.0/detik).
   - **Tab 2 — Fisika & Duel**:
     - Toggle **Mode Batas Arena** (`🧱 Pantul & Menyusut` vs `💨 Jatuh Ring-Out`).
     - Slider **Mulai Zona Menyusut** (5 – 60 detik) & **Batas Ukuran Minimal Zona** (35% – 90%).
     - Slider **Pengali Pantulan & Kecepatan** (0.5x – 2.0x).
     - Slider **Pengali Damage Bilah** (0.5x – 2.5x) & **Maksimal Pembesaran Tap Like** (0% – 50%).
   - **Tab 3 — Rintangan & Audio**:
     - Saklar terpisah per rintangan: **Vortex Pusat**, **Sinar Laser**, **Trapdoor Maut**, serta tombol/saklar **Titan Boss Goliath** (+ slider HP Boss).
     - **Kontrol Audio Terpisah**: Saklar **SFX Benturan & Bilah**, Saklar **Suara Komentator TTS**, dan **Slider Volume Master** (0% – 100%).
   - **Tab 4 — Arena, HUD & Tes Live**:
     - Bentuk Arena (`Kotak / Bulat`), Preset Rasio (`1:1, 4:3, 16:9, 3:4, 9:16`), Slider `P × L`, Tema Background & Pilih Foto Galeri.
     - Saklar Visibilitas HUD: Tampilkan/Sembunyikan **Kill Feed**, **Banner Event Ticker**, dan **Indikator Info Ronde**.
     - Aksi Cepat **Tes Live**: Input nama tester, `+ Bot`, `Like+20`, `Dash`, `Gift Titan/Galaxy`, `Meteor`, `Freeze`, `Petir`, `Bersihkan Ring`.

- **Aesthetic Direction**: *Clean Broadcast Arcade* — kontras tinggi, tombol kompak dengan *touch target* nyaman di area jempol, dan layar atas bebas gangguan.

---

### 3. Key Product Decisions & Trade-Offs

- **Keputusan 1: Thread-Safety pada Loop Fisika via Single-Lock Snapshot & Concurrent Guards**
  - *Pendekatan*: Melindungi mutasi list pemain/partikel/teks dan menyertakan blok `try-catch` di setiap iterasi `physicsJob` sehingga *coroutine* fisika tidak akan pernah mati meskipun ratusan event *Like/Chat/Gift* masuk dalam 1 detik.
  - *Alasan*: Penyebab utama game membeku saat *live rame* adalah *race condition* antara `Dispatchers.Default` pada handler event dan loop 60 FPS.
  - *Alternatif*: Memindahkan seluruh event ke *Channel/Actor* tunggal — dapat dipertimbangkan, namun *synchronized guard* + *ConcurrentLinkedQueue* untuk spawn event jauh lebih sederhana, nol risiko *deadlock*, dan tetap responsif di 60 FPS.

- **Keputusan 2: Penghentian Audio Berlapis (Event Gate + Track Pool + Stop-on-Exit)**
  - *Pendekatan*: Menolak event game jika `isGameActive == false`, membatasi maksimal 4 `AudioTrack` aktif secara bersamaan, melacak track aktif di koleksi aman, dan langsung memanggil `stopAll()` saat keluar layar arena atau mematikan saklar suara.
  - *Alasan*: Mencegah kebocoran suara di luar game dan mencegah *AudioFlinger exhaustion* saat banyak gangsing bertabrakan bersamaan.

- **Keputusan 3: Persistensi Seluruh Pengaturan ke `SharedPreferences`**
  - *Pendekatan*: Setiap parameter baru (jeda reset, auto-bot, fisika, rintangan satuan, audio SFX/TTS/volume, toggle HUD) disimpan otomatis saat diubah sehingga konfigurasi host tidak pernah hilang saat aplikasi ditutup atau ronde berganti.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Arsitektur Aliran Event, Fisika Aman-Thread, Audio & Kontrol

```
┌────────────────────────────────────────────────────────────────────────┐
│                    LiveEventReceiver (TikTok / Bigo)                   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼  [Gerbang Ketat: isGameActive == true]
┌────────────────────────────────────────────────────────────────────────┐
│               InteractiveBeyBattleEngine (Thread-Safe 60 FPS)          │
│  ┌────────────────────┐  ┌─────────────────────┐  ┌─────────────────┐  │
│  │  Event Queue Safe  │─►│  Physics Loop Guard │─►│ Auto-Reset FSM  │  │
│  │  - Join/Like/Chat  │  │  - try/catch anti-  │  │ - 1 Left: Champ │  │
│  │  - Gift & Limits   │  │    crash saat rame  │  │ - 0 Left: Draw  │  │
│  │  - Custom Rules    │  │  - Custom Multipliers│ │ - Auto-Bot Fill │  │
│  └────────────────────┘  └──────────┬──────────┘  └────────┬────────┘  │
└─────────────────────────────────────┼──────────────────────┼───────────┘
                                      │                      │
                 ┌────────────────────┘                      │
                 ▼                                           ▼
┌─────────────────────────────────┐  ┌───────────────────────────────────┐
│      BeyBattleSoundManager      │  │      BeyBattleArenaScreen         │
│  - Saklar SFX & Saklar TTS      │  │  - Layar Atas Bersih Total        │
│  - Master Volume (0..100%)      │  │  - Kanvas 60 FPS + Countdown      │
│  - Pool Max 4 Track + stopAll() │  │  - Dock Bawah Minimalis           │
└─────────────────────────────────┘  │  - Pusat Pengaturan 4-Tab Bawah   │
                                     └───────────────────────────────────┘
```

#### Pemetaan State & Pengaturan yang Dapat Dikonfigurasi
- **Konfigurasi Aturan & Auto-Reset (`BeyBattleState`)**: `autoResetEnabled`, `celebrationDurationSec` (1–15 dtk), `autoSpawnBotsEnabled`, `minBotTargetCount` (2–12), `maxActivePlayers` (5–40), `passiveHpDecayPerSec` (0.0–3.0).
- **Konfigurasi Fisika & Zona**: `bounceMultiplier` (0.5–2.0), `damageMultiplier` (0.5–2.5), `maxLikeSizeBonus` (0.0–0.50), `shrinkStartSec` (5–60 dtk), `minShrinkFactor` (0.35–0.90), `boundaryMode` (`WALL_BOUNCE` / `RING_OUT`).
- **Konfigurasi Rintangan Satuan & Audio**: `isVortexEnabled`, `isLaserEnabled`, `isTrapdoorsEnabled`, `bossMaxHp` (1000–10000), `isSfxEnabled`, `isAnnouncerEnabled`, `masterVolume` (0.0–1.0).
- **Konfigurasi Visibilitas HUD**: `showKillFeed`, `showEventTicker`, `showRoundBadge`.
- **Alur Auto-Reset Pasti Jalan**:
  - Ketika pemain bertahan `== 1` (saat `playersList.size > 1` atau pernah ada eliminasi) → masuk fase selebrasi juara (`celebrationDurationSec` detik, atau instan jika `autoResetEnabled` berjeda 0/minimal).
  - Ketika pemain bertahan `== 0` (seri/habis bersamaan) dan `autoResetEnabled` aktif → tampilkan banner seri singkat (2 detik) lalu otomatis memulai ronde berikutnya dan memanggil `publishStateUpdate()` serta mengisi *auto-bot* bila diaktifkan.
