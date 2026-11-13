# Restrukturisasi Fundamental UX: Pusat Kendali Game Interaktif Terpadu (Unified Game Hub)

Rencana perombakan total arsitektur antarmuka (User Experience) menu game siaran langsung CuanCame untuk mengeliminasi kebingungan pengguna, tombol raksasa yang acak-acakan, serta teks instruksi panjang yang memakan tempat. Seluruh game (Tarik Tambang, Raja Singgasana, BeyBattle, dan Mini Trooper) disatukan ke dalam **Satu Standar Dashboard Terpadu** yang bersih, ringkas, dan konsisten.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Berdasarkan umpan balik dan hasil klarifikasi pengguna:
> - **Struktur Pusat Kendali**: **Satu Dashboard Terpadu** — Seluruh game menggunakan cetak biru (blueprint) kartu ringkas dengan tata letak tombol, status, dan hierarki yang 100% seragam.
> - **Cara Join Penonton**: **Banner Mikro / 3 Pill Chip Seragam** — Diletakkan di atas arena dan di kartu game (`❤️ LIKE`, `💬 CHAT`, `🎁 GIFT`), tanpa paragraf penjelasan panjang yang membingungkan.
> - **Standarisasi Ukuran Tombol**: **Tombol Kompak Ukuran Seragam (Tinggi 32–34dp)** dengan ikon simbolik dan label maksimal satu kata (`[▶️ MULAI]`, `[↺ RESET]`, `[🤖 +BOT]`, `[🎁 GIFT TES]`, `[⚙️ ATUR]`).
> - **Kontrol Fullscreen & VCam Konsisten**: Tombol `[📺 FULLSCREEN]` (Layar Penuh HP Ke-2 60 FPS) dan `[👁️ VCAM]` (Kamera Virtual) diletakkan pada posisi identik di semua game.

---

## 1. Masalah Utama & Solusi Fundamental

| Masalah Saat Ini | Akar Penyebab | Solusi Fundamental Terpadu |
| :--- | :--- | :--- |
| **Menu Acak-acakan & Membingungkan** | Setiap game punya gaya layout, warna, dan struktur tombol yang berbeda-beda. | **Unified Game Card Component**: Ke-4 game memakai 1 template kartu yang sama persis strukturnya. |
| **Tombol Terlalu Besar & Beragam Ukuran** | Ada tombol 48dp, 40dp, dan 30dp yang tersebar di banyak baris. | **Standarisasi Grid 32–34dp**: Semua tombol aksi berukuran compact seragam dengan padding minimal. |
| **Banyak Teks Instruksi Panjang** | Paragraf tutorial manual memenuhi layar menu dan menutupi kontrol penting. | **Eliminasi Teks Panjang**: Diganti pill badge mikro simbolik (Like = Aksi, Chat = Aksi, Gift = Aksi). |
| **Letak Tombol Fullscreen Berbeda-beda** | Host bingung mencari cara membuka layar penuh arena di setiap game. | **Standard Primary Action Header**: Tombol Fullscreen & VCam selalu berada di pojok kanan atas kartu game. |
| **Pengaturan Posisi & Skala Berserakan** | Slider dan tombol stepper bertumpuk di layar utama. | **Unified Quick-Settings Dialog**: Pengaturan (X/Y, Zoom, Top 5, Tim) dipindahkan ke 1 modal pop-up bersih. |

---

## 2. Cetak Biru Komponen: Kartu Game Terpadu (Standard Game Card)

Setiap game (1. Tambang, 2. Sultan, 3. Gangsing, 4. Prajurit) akan dirender menggunakan struktur komponen seragam berikut:

```
┌────────────────────────────────────────────────────────────────────────┐
│ 🎮 [IKON] NAMA GAME                    [📺 FULLSCREEN]  [👁️ VCAM: ON] │ ◄── Header & Aksi Utama Seragam
├────────────────────────────────────────────────────────────────────────┤
│ CARA JOIN PENONTON (PADAT & JELAS):                                    │
│ ┌──────────────────┐  ┌──────────────────┐  ┌────────────────────────┐ │ ◄── 3 Pill Chip Seragam
│ │ ❤️ LIKE: [AKSI]  │  │ 💬 CHAT: [AKSI]  │  │ 🎁 GIFT: [SENJATA/HP]  │ │     (Tanpa teks panjang)
│ └──────────────────┘  └──────────────────┘  └────────────────────────┘ │
├────────────────────────────────────────────────────────────────────────┤
│ STATUS LIVE: [👥 12 Pemain] • [⏱️ 01:45] • [🏆 Pemenang / Top 1]       │ ◄── Baris Status Ringkas
├────────────────────────────────────────────────────────────────────────┤
│ ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐ │
│ │ ▶️ MULAI │  │ ↺ RESET  │  │ 🤖 +BOT  │  │ 🎁 TES   │  │ ⚙️ ATUR    │ │ ◄── 5 Tombol Kompak (34dp)
│ └──────────┘  └──────────┘  └──────────┘  └──────────┘  └────────────┘ │     (1 Kata + Ikon)
└────────────────────────────────────────────────────────────────────────┘
```

### Rincian 4 Kartu Game Terpadu:

1. **Game 1: Tarik Tambang (Duel 2 Kubu)**:
   - *Cara Join*: `❤️ LIKE = +1 Tarik` • `💬 CHAT = +10 Tarik` • `🎁 GIFT = +100 Tarik Koin`.
   - *Aksi*: `[▶️ MULAI]`, `[↺ RESET]`, `[🤖 +TARIK]`, `[🎁 GIFT TES]`, `[⚙️ ATUR TIM]`.
2. **Game 2: Raja Singgasana (Rebutan Kursi Sultan)**:
   - *Cara Join*: `❤️ LIKE = +1 Poin` • `💬 CHAT = +15 Kudeta` • `🎁 GIFT = Rebut Takhta`.
   - *Aksi*: `[▶️ MULAI]`, `[↺ RESET]`, `[🛡️ +HP]`, `[🎁 SULTAN]`, `[⚙️ ATUR GELAR]`.
3. **Game 3: BeyBattle Royale (Gangsing Tempur)**:
   - *Cara Join*: `❤️ LIKE = +RPM/Center` • `💬 CHAT = Dash Tabrak` • `🎁 GIFT = Mega Titan`.
   - *Aksi*: `[▶️ MULAI]`, `[↺ RESET]`, `[🤖 +BOT]`, `[🎁 TITAN]`, `[⚙️ ATUR ARENA]`.
4. **Game 4: Mini Trooper Royale (Perang Prajurit Chibi)**:
   - *Cara Join*: `❤️ LIKE = Tembak` • `💬 CHAT = Dodge Roll` • `🎁 GIFT = Bazooka & Tank`.
   - *Aksi*: `[▶️ MULAI]`, `[↺ RESET]`, `[🤖 +BOT]`, `[🎁 BAZOOKA]`, `[⚙️ ATUR TOP 5]`.

---

## 3. Desain Layar Penuh Arena (Fullscreen Clean Arena)

Di seluruh tampilan layar penuh arena (Device 2 / Streaming Monitor):

1. **Header Mikro Atas Seragam (36dp)**:
   - Kiri: Identitas Game & Ronde (`🎖️ R-1`).
   - Tengah: Metrik Utama Ringkas (`👥 12/20 HIDUP • ⏱️ 01:24`).
   - Kanan: Tombol Keluar Arena (`[✕ KELUAR]`).
2. **Banner Pedoman Mikro 1 Baris (Seragam di Bawah Header)**:
   - Menghilangkan teks bantuan yang mengaburkan visual.
   - Contoh: `❤️ LIKE: TEMBAK | 💬 CHAT: HINDAR | 🎁 GIFT: BAZOOKA & TANK`.
3. **Bottom Dock Kontrol Cepat (Compact & Konsisten)**:
   - Letak di bawah layar dengan insets navigasi (`navigationBarsPadding()`).
   - Tombol-tombol berbentuk pil 32dp: `[MULAI/JEDA]`, `[RESET]`, `[+BOT]`, `[GIFT TES]`, `[ATUR POSISI]`.

---

## 4. Dialog Pengaturan Cepat Terpadu (Unified Quick-Settings Dialog)

Ketika host menekan tombol `[⚙️ ATUR]` di game mana pun, sistem menampilkan satu dialog modal yang seragam:

1. **Bagian 1: Posisi Kamera Virtual (X / Y Axis Stepper)**:
   - Tombol arah padat: `[⬅️] [⬆️] [⬇️] [➡️]`.
2. **Bagian 2: Ukuran Skala (Zoom Stepper)**:
   - Tombol zoom padat: `[➖ KECIL] [➕ BESAR]` (dengan indikator persentase 50% s/d 200%).
3. **Bagian 3: Parameter Spesifik Game**:
   - Jika Game 4 (Trooper Royale): Saklar Top 5 (Foto Profil ON/OFF, Nama ON/OFF, Kills ON/OFF, Layout Baris/Kolom).
   - Jika Game 1 (Tarik Tambang): Edit nama Kubu A & Kubu B.
   - Jika Game 2 (Raja Singgasana): Pilihan preset takhta (`SULTAN`, `PENGUASA`, `TIKTOK`).
   - Jika Game 3 (BeyBattle): Pilihan preset rasio arena (`1:1`, `4:3`, `16:9`, `9:16`).
4. **Tombol Konfirmasi**: `[SELESAI]` (Hijau Emerald).

---

## 5. Technical Architecture & File Changes

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MAIN NAVIGATION / TABS                          │
│                     Tab "🎮 GAME" (Unified Hub)                         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
       ┌────────────────────────────┼────────────────────────────┐
       ▼                            ▼                            ▼
┌─────────────────────────┐  ┌─────────────────────────┐  ┌────────────────────────┐
│   TugOfWarArenaScreen   │  │  KingOfThroneArenaScreen │  │   BeyBattleArenaScreen │
│ (Unified Card Template) │  │  (Unified Card Template) │  │ (Unified Card Template)│
└─────────────────────────┘  └─────────────────────────┘  └────────────────────────┘
                                    │
                                    ▼
                     ┌─────────────────────────────┐
                     │  TrooperRoyaleArenaScreen   │
                     │   (Unified Card Template)   │
                     └─────────────────────────────┘
                                    │
                                    ▼
       ┌─────────────────────────────────────────────────────────┐
       │     UnifiedGameComponents.kt (New Reusable Module)      │
       │  - StandardGameCard(...)                                │
       │  - GameJoinInstructionChips(...)                        │
       │  - StandardGameActionRow(...)                           │
       │  - UnifiedGameSettingsDialog(...)                       │
       └─────────────────────────────────────────────────────────┘
```

### Rencana File yang Dimodifikasi:
1. **Pembuatan Komponen Reusable Baru (`UnifiedGameComponents.kt`)**:
   - Berisi komponen kartu game standar, 3 pill chip cara join, 5 tombol aksi seragam, dan dialog pengaturan cepat.
2. **Refactoring `TugOfWarArenaScreen.kt`**:
   - Menghapus kartu raksasa, teks panjang, dan mengganti seluruh sub-menu (Tambang, Sultan, Gangsing, Prajurit) menggunakan `StandardGameCard`.
3. **Refactoring `KingOfThroneArenaScreen.kt`**:
   - Mengganti kontroler panjang berbelit-belit dengan `StandardGameCard` terpadu.
4. **Refactoring `BeyBattleArenaScreen.kt`**:
   - Menyelaraskan kartu kontroler dengan arsitektur terpadu.
5. **Refactoring `TrooperRoyaleArenaScreen.kt`**:
   - Menyelaraskan kartu kontroler dengan arsitektur terpadu.
6. **Refactoring `TugOfWarTabBuilder.kt` (Floating Control)**:
   - Mengubah tab "Tambang" menjadi "🎮 Game Hub" dengan kartu kompak bebas teks instruksi panjang.

---

## 6. Tahapan Eksekusi & Status Selesai

- [x] **Langkah 1**: Buat modul `UnifiedGameComponents.kt` yang mengabstraksi seluruh UI game menjadi komponen standar (`StandardGameCard`, `GameJoinInstructionChips`, tombol aksi seragam 32–34dp 1 kata, dan `UnifiedGameSettingsDialog`).
- [x] **Langkah 2**: Terapkan komponen terpadu ke `TugOfWarArenaScreen.kt` dan tambahkan Pusat Koneksi Jaringan 2-HP terpusat untuk semua 4 game. Hapus kartu raksasa dan teks instruksi panjang.
- [x] **Langkah 3**: Terapkan komponen terpadu ke `KingOfThroneArenaScreen.kt` (Game 2: Raja Singgasana) dengan modal pengaturan gelar takhta dan ketebalan perisai HP.
- [x] **Langkah 4**: Terapkan komponen terpadu ke `BeyBattleArenaScreen.kt` (Game 3: BeyBattle Royale) dengan modal pengaturan dimensi arena dan latar belakang.
- [x] **Langkah 5**: Terapkan komponen terpadu ke `TrooperRoyaleArenaScreen.kt` (Game 4: Mini Trooper Royale) dengan modal pengaturan posisi, skala, dan visibilitas papan TOP 5.
- [x] **Langkah 6**: Jalankan verifikasi lengkap: `compile_applet` sukses dan seluruh unit test `gradle :app:testDebugUnitTest` berhasil (BUILD SUCCESSFUL).
