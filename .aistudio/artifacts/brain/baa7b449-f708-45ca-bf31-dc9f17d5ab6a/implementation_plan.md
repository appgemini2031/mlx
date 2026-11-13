# Rencana Revitalisasi Tata Letak Menu Floating CuanCame PRO

Revitalisasi menyeluruh tata letak antarmuka (*UI Layout & Spatial Hierarchy*) pada Menu Floating CuanCame untuk mengeliminasi kelelahan scrolling saat live streaming, mencegah salah sentuh (*mis-click*), dan memaksimalkan ruang pandang layar live tanpa mengubah satu pun logika atau fungsi internal.

## User Review & Critical Decisions

> [!IMPORTANT]
> Seluruh logika fungsi, SharedPreferences, komunikasi broadcast, interaksi kamera hook, dan alur state tetap 100% dipertahankan seperti aslinya. Perubahan murni difokuskan pada pengelompokan visual (*grouping*), kartu lipat (*collapsible cards*), keterbacaan tipografi, dan efisiensi ruang pandang.

- **Keputusan 1 (Pengelompokan Kontrol)**: Menerapkan sistem kartu lipat (*Accordion Section*) dengan status ringkas (*summary badge*) pada header kartu tertutup. Pengguna dapat langsung melihat kontrol utama tanpa scrolling.
- **Keputusan 2 (Penataan Tab Notifikasi & Overlay)**: Merapikan sub-tab Notifikasi (Jualan, Stiker, Watermark, Doa Komentar) menggunakan chip pill selektor modern dengan ruang sentuh ramah jari (*touch-friendly*).
- **Keputusan 3 (Bottom Dock D-Pad & Skala)**: Menjadikan dock navigasi bawah fleksibel/bisa diciutkan (*collapsible dock*) dengan satu ketukan tombol toggle, sehingga area daftar video/pengaturan mendapatkan ruang layar 40% lebih tinggi saat dock tidak sedang digunakan.

---

## 1. Overview & Core Concept

### Masalah pada Tata Letak Saat Ini:
1. **Vertical Scroll Fatigue**: Tab Video menumpuk 7 blok pengaturan vertikal (Navigasi Video, Daftar Playlist, Mode Master/Spill, Siklus Bridge, Auto-Next, Rotasi/Mirror/Flip, Skala, dan FPS). Host harus menggulir layar berkali-kali hanya untuk mengubah orientasi atau FPS.
2. **Kepadatan Ekstrem Tab Notifikasi**: Tab Notifikasi menggabungkan 4 modul besar (Notif Jualan, Stiker, Watermark, Doa Komentar) serta Log Inspector Scan Komentar ke dalam satu wadah dengan puluhan baris input teks, slider, dan chip warna yang rapat.
3. **Ruang Layar Terkunci oleh Fixed Dock**: Bottom Dock (D-Pad arah, rotasi 90°, cermin, skala) berada secara permanen di bagian bawah panel floating, mengurangi tinggi scrollable viewport secara signifikan di ponsel layar 20:9 standar.
4. **Target Sentuh Berisiko**: Sebagian tombol preset memiliki ukuran font 8.5–9sp dan padding tipis yang rentan meleset saat dioperasikan secara terburu-buru saat live.

### Nilai Baru yang Dihadirkan:
- **Instant Glanceable Control**: Kontrol esensial (Judul Video Aktif, Tombol Play/Pause, Mute, Next/Prev) selalu berada di bagian teratas tanpa perlu scroll.
- **Collapsible Section Cards**: Pengaturan sekunder (Orientasi Kamera, Format Alur Bridge, Anti-Deteksi Server) dikelompokkan dalam kartu lipat yang bersih dengan badge indikator status langsung di judulnya.
- **Collapsible Bottom Dock**: Dock posisi & skala dapat disembunyikan/dimunculkan dengan cepat via toggle bar minimalis.
- **Zero Logic Modification**: Semua event handler, listener, broadcast, dan repository state berjalan 100% identik.

---

## 2. User Experience & Visual Design

### A. Palet Visual & Hierarki Material
- **Latar Belakang Panel**: Glassmorphic Dark Charcoal (`#0B1120` dengan alpha adaptif 50% - 100%).
- **Aksen Primer**: Cyan Elektrik (`#00E5FF`) untuk Video, Pink Neon (`#D946EF`) untuk Musik, Oranye Enerjik (`#F97316`) untuk Notifikasi & Doa, Emerald Green (`#10B981`) untuk status Aktif, Rose Red (`#EF4444`) untuk Mute/Tutup.
- **Card Surface**: `#0F172A` dengan border halus `#1E293B` dan corner radius 8dp.
- **Touch Target**: Minimum tinggi tombol 38–44dp dengan padding sentuh nyaman untuk ibu jari host live.

### B. Struktur Tata Letak Baru Tab Video (Tab 1)
```
┌────────────────────────────────────────────────────────┐
│ [⋮⋮ Drag Bar]                                         │
│ ⚡ CUANCAME PRO  [1/4 virtual.mp4]    [📱] [🚀] [➖] [🛑]│
├────────────────────────────────────────────────────────┤
│ [🎬 Video] [🎵 Musik] [🎨 Visual] [💬 Notif] [👆] [⚙️]  │
├────────────────────────────────────────────────────────┤
│ ▼ KARTU 1: KONTROL PEMUTAR UTAMA (Selalu Terbuka)      │
│   • [Judul Video Aktif ▾] [📋 Buka List]               │
│   • [⏪ Prev] [ ▶️ Putar / ⏸️ Jeda ] [ 🔇/🔊 ] [⏩ Next] │
│   • Seekbar Posisi: [====●────────] 01:23 / 03:45       │
│                                                        │
│ ▸ KARTU 2: SISTEM ALUR & OTOMATISASI [Bridge: ON ▾]    │
│   (Bisa dilipat / diperluas dengan 1 klik)              │
│   • Saklar: [🎯 Master-Spill: OFF] [🔀 Siklus Bridge: ON]│
│   • Mode Pindah: [⚡ Pindah Instan] vs [⏳ Tunggu Selesai]│
│   • Alur Playlist: [🔁 Berurutan] [🔀 Acak] [🔂 Loop]    │
│                                                        │
│ ▸ KARTU 3: ORIENTASI, SKALA & FPS [0° | Normal | 30 ▾] │
│   (Bisa dilipat / diperluas dengan 1 klik)              │
│   • Rotasi: [0°] [90°] [180°] [270°]                   │
│   • Flip: [↔️ Cermin ON/OFF] [↕️ Balik V ON/OFF]        │
│   • Skala: [Crop Penuh] [Fit Layar] [Stretch]          │
│   • Frame Rate: [15] [24] [30] [60] FPS                │
├────────────────────────────────────────────────────────┤
│ [ ⌃ Dock Posisi & Transformasi (Bisa Diciutkan) ]     │
│   [⬅️] [⬆️] [⬇️] [➡️]  |  [🔄 90°] [🪞] [➖] [➕] [🔒]   │
└────────────────────────────────────────────────────────┘
```

### C. Struktur Tata Letak Baru Tab Notifikasi & Overlay (Tab 4)
- **Sub-Selector Header Bersegi Empat**:
  `[🛒 Notif Jualan]  [🏷️ Stiker Teks]  [💧 Watermark]  [🤲 Doa Chat]`
- **Mode Ringkas**:
  - Setiap sub-tab memiliki toggle saklar utama di bagian atas dengan badge status menyala hijau terang saat aktif.
  - Opsi pengaturan warna, transparansi, durasi, dan pergerakan dikelompokkan ke dalam segmen lipat, sehingga tidak memadati pandangan.
- **Log Inspector Scan Komentar**: Disediakan tombol buka/tutup cepat `[📋 Log Scan Teks (X Diterima) ▾]` sehingga tidak menghabiskan ruang panel jika host sedang tidak membutuhkannya.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Menggunakan Accordion Collapsible Cards di dalam Tab Video & Tab Notif**
  - *Chosen Approach*: Menyediakan baris judul kartu dengan tombol panah ekspansi (`▾` terbuka, `▸` tertutup) dan status ringkasan (*summary badge*).
  - *Why*: Menghemat ruang vertikal hingga 60%. Host tidak perlu scroll bermeter-meter untuk mencapai fitur yang sering diubah.
  - *Trade-off*: Membutuhkan sedikit memori lokal UI (`isStreamSettingsOpen`, `isOrientationOpen`) untuk menyimpan status buka/tutup kartu selama floating berjalan.

- **Decision 2: Collapsible Bottom Dock**
  - *Chosen Approach*: Memberikan bilah tipis (*mini grip tab*) di atas bottom dock untuk menyembunyikan atau memunculkan D-Pad navigasi posisi.
  - *Why*: Ketika host sedang memilih video atau mengatur musik, D-Pad bawah tidak diperlukan dan hanya menyita ruang pandang.
  - *Trade-off*: Menambahkan satu tombol sentuh kecil, namun memberikan fleksibilitas penuh pada layar HP berukuran sedang (misal 6.5 inci).

- **Decision 3: Pemisahan Kontrol Primer dan Sekunder pada Media Video**
  - *Chosen Approach*: Kontrol Primer (Play, Pause, Mute, Seek, Video Title, Prev/Next) selalu ditempatkan di tingkat pertama tanpa pernah tertutup.
  - *Why*: Dalam kondisi darurat live streaming (misal ada kendala teknis atau host ingin jeda mendadak), tombol jeda dan mute harus dapat diakses dalam hitungan 0.1 detik tanpa perlu scroll.

---

## 4. Technical Architecture & Component Mapping

```
┌─────────────────────────────────────────────────────────────────┐
│                    FloatingControlService                       │
├────────────────────────────────┬────────────────────────────────┤
│      Header & Quick Icons      │        FloatingTab Bar         │
│  [📱] [🚀] [➖] [🛑]           │  [🎬] [🎵] [🎨] [💬] [👆] [⚙️]  │
├────────────────────────────────┴────────────────────────────────┤
│                Scrollable Tab Content Holder                    │
│                                                                 │
│  ┌───────────────────────┐            ┌──────────────────────┐  │
│  │ SpillTabBuilder       │            │ BuyerNotifTabBuilder │  │
│  │ • Video Primary Card  │            │ • Sub-mode Bar       │  │
│  │ • Accordion: Alur     │            │ • Sales Accordion    │  │
│  │ • Accordion: Orientasi│            │ • Preset Quick Grid  │  │
│  │ • Tab Musik Player    │            └──────────────────────┘  │
│  │ • Tab Visual Anti-Det │            ┌──────────────────────┐  │
│  └───────────────────────┘            │ StikerTabBuilder     │  │
│                                       │ WatermarkTabBuilder  │  │
│                                       │ LiveCommentTabBuilder│  │
│                                       └──────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│             Collapsible Bottom Dock (FloatingUiWidgets)         │
│  [⌃/⌄ Toggle Dock]                                              │
│  [D-Pad: ⬆️⬇️⬅️➡️] [Transformasi: Rotasi | Mirror | Scale | Lock] │
└─────────────────────────────────────────────────────────────────┘
```

### Rencana File yang Dimodifikasi:
1. `app/src/main/java/com/vcam/floating/tabs/SpillTabBuilder.kt`:
   - Penataan ulang Tab 1 (Video) dengan kartu lipat (*Accordion Section*).
   - Penataan ulang tombol kontrol pemutar utama (Play, Pause, Mute, Prev, Next) dalam 1 baris grid ergonomis.
   - Penataan ulang Tab 2 (Musik) dan Tab 3 (Visual Anti-Deteksi) agar lebih terstruktur.
2. `app/src/main/java/com/vcam/floating/components/FloatingUiWidgets.kt`:
   - Penambahan fungsionalitas ekspansi/ciut (*collapsible*) pada Fixed Bottom Dock.
   - Peningkatan kenyamanan sentuhan (*touch target size*) pada D-Pad dan stepper button.
3. `app/src/main/java/com/vcam/floating/tabs/BuyerNotifTabBuilder.kt`:
   - Penataan kartu lipat untuk opsi lanjutan notifikasi penjualan (durasi, interval, warna kapsul, teks aksi).
4. `app/src/main/java/com/vcam/floating/tabs/LiveCommentTabBuilder.kt`:
   - Penataan bagian Inspector Log Scan Komentar agar bisa dibuka/tutup dengan mudah (*collapsible log card*).
5. Verifikasi kompilasi penuh menggunakan `compile_applet`.
