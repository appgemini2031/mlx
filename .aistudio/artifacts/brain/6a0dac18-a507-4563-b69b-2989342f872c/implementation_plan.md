# Standarisasi Dropdown & Eliminasi Fitur Usang Menu Notifikasi (Tab 💬)

Rencana perombakan dan penyederhanaan menyeluruh pada Menu Notifikasi (ikon pesan `💬` di samping ikon pengaturan `⚙️` pada Floating Window):
1. **Menghapus secara permanen** Sub-Tab Doa & Komentar dan Sub-Tab Inspeksi Scan dari bilah navigasi sub-mode dan kontainer antarmuka karena sudah usang / tidak berguna.
2. **Merestrukturisasi 6 sub-tab aktif yang tersisa** (Jualan, Stiker, Watermark, Waktu, Khatam, Event) agar setiap pengaturan dibungkus dalam kartu dropdown (accordion), menggunakan istilah singkat-padat, tanpa pengulangan kata pada tombol, dan mematuhi pemisahan mutlak tombol ikon vs kata murni.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Penghapusan Fitur Usang (Clean Removal)**:
> - **Sub-Tab Doa & Komentar** dihapus dari switcher sub-tab Menu Notifikasi.
> - **Sub-Tab Inspeksi Scan** dihapus dari switcher sub-tab Menu Notifikasi.
> - Bilah sub-mode kini hanya menampilkan 6 sub-tab bersih: **Jualan**, **Stiker**, **Watermark**, **Waktu**, **Khatam**, dan **Event**. Mode default saat membuka Tab Notif langsung diarahkan ke **Jualan** (atau **Event**).

- **Aturan Mutlak Ikon vs Kata (Strict Mutual Exclusion)**:
  - **Ikon Murni (Tanpa Teks)**: Tombol arah/posisi (`⬅️`, `⬆️`, `⬇️`, `➡️`), stepper nilai/font (`➖`, `➕`), zoom (`🔍`), reset (`↺`), putar/tes (`▶`), dialog input keyboard (`⌨️`), hapus chip (`✕`). Tombol-tombol ini tidak memiliki teks pendamping.
  - **Kata Murni (Tanpa Ikon/Emoji)**: Tombol status ("ON", "OFF", "MULAI", "HENTIKAN", "SAMBUNG", "PUTUS", "BATAL"), orientasi ("Vertikal", "Horizontal"), penempatan ("Kiri", "Kanan", "Bawah", "Tengah"), target ("Semua", "Chat", "Gift", "Like", "Join", "Follow", "Share"), dan filter. Tombol-tombol ini tidak menggunakan emoji atau ikon.
- **Dropdown Accordion di Setiap Blok Pengaturan**: Setiap kelompok fitur di 6 sub-tab dibungkus dengan kartu collapsible terstandarisasi (`createCollapsibleSectionCard`). Default hanya kartu status/koneksi utama yang terbuka; pengaturan detail lainnya tertutup rapi.
- **Bebas Pengulangan Kata & Bahasa Singkat**: Menghilangkan teks berulang seperti `Komentar: ON`, `Durasi: 3s`, `Geser Kiri`, `Tombol Simpan`. State aktif ditunjukkan langsung lewat warna latar (hijau emerald vs slate gelap).

---

### 1. Overview & Core Concept

Sebelumnya Menu Notifikasi menampung 8 sub-tab termasuk sistem lama scanning layar (Doa/Komentar via OCR/Accessibility dan Inspeksi Scan). Karena integrasi live streaming kini telah dialihkan ke **Live Event Broker** yang jauh lebih presisi via WebSocket LAN langsung dari Termux, modul scanning Doa/Komentar dan Inspeksi Scan sudah tidak lagi berguna dan hanya memenuhi layar.

Dengan revisi ini:
- Sub-tab Doa dan Inspeksi Scan dihapus tuntas dari switcher sub-tab floating.
- Tersisa 6 sub-tab inti: **Jualan**, **Stiker**, **Watermark**, **Waktu**, **Khatam**, dan **Event**.
- Seluruh 6 sub-tab mengadopsi struktur dropdown accordion seragam, tombol ikon murni, tombol kata murni, dan tata bahasa ringkas-padat.

---

### 2. User Experience & Visual Design per Sub-Tab

#### Bilah Switcher Sub-Tab Notifikasi Baru:
```
┌────────────────────────────────────────────────────────────┐
│ [ Jualan ] [ Stiker ] [ Watermark ] [ Waktu ] [ Khatam ] [ Event ] │
└────────────────────────────────────────────────────────────┘
```

#### A. Sub-Tab 1: Jualan / Notif Pembeli (`BuyerNotifTabBuilder`)
- **Dropdown 1: Status & Kendali** ➔ Toggle "MULAI"/"HENTIKAN", total notif, pemicu manual "MUNCULKAN", tombol "Salin".
- **Dropdown 2: Kapsul & Posisi** ➔ Posisi X/Y kapsul pembeli menggunakan ikon murni (`⬅️`, `⬆️`, `⬇️`, `➡️`).
- **Dropdown 3: Teks & Font** ➔ Pilihan nama barang, template ucapan, ukuran font menggunakan ikon murni (`➖`, `➕`).
- **Dropdown 4: Waktu & Suara** ➔ Interval jeda notifikasi, durasi tampil, toggle audio bell ("ON"/"OFF").
- **Dropdown 5: Uji Coba** ➔ Tombol aksi teks murni "Tes Notif".

#### B. Sub-Tab 2: Stiker Tulisan (`StikerTabBuilder`)
- **Dropdown 1: Status & Teks** ➔ Toggle stiker, input baris teks via dialog keyboard (`⌨️`).
- **Dropdown 2: Gaya & Warna** ➔ Pilihan mode teks murni ("Blok", "Polos", "Outline", "Jalan"), pemilih warna hex (`⌨️`).
- **Dropdown 3: Posisi & Ukuran** ➔ Stepper ikon murni (`⬅️`, `⬆️`, `⬇️`, `➡️`, `➖`, `➕`).
- **Dropdown 4: Preset Cepat** ➔ Chip teks murni ("Promo", "Live", "Follow", "Kupon").

#### C. Sub-Tab 3: Watermark Kamera (`WatermarkTabBuilder`)
- **Dropdown 1: Status & Teks** ➔ Toggle watermark, input teks via dialog keyboard (`⌨️`).
- **Dropdown 2: Gaya & Warna** ➔ Pilihan mode teks murni, opasitas, warna font.
- **Dropdown 3: Posisi & Ukuran** ➔ Stepper ikon murni (`⬅️`, `⬆️`, `⬇️`, `➡️`, `➖`, `➕`).
- **Dropdown 4: Gerak Acak** ➔ Interval acak, batas aman anti-hitam, toggle auto-move ("ON"/"OFF").

#### D. Sub-Tab 4: Waktu Otomatis (`AutoTimeTabBuilder`)
- **Dropdown 1: Status & Format** ➔ Toggle waktu, format tanggal/bulan ("Singkat", "Lengkap"), jam live ("24 Jam", "12 Jam").
- **Dropdown 2: Posisi & Ukuran** ➔ Stepper ikon murni (`⬅️`, `⬆️`, `⬇️`, `➡️`, `➖`, `➕`).
- **Dropdown 3: Gaya & Warna** ➔ Pilihan warna teks/kapsul, transparansi latar.

#### E. Sub-Tab 5: Khatam Al-Qur'an 30 Juz (`QuranKhatamTabBuilder`)
- **Dropdown 1: Status & Progres** ➔ Surat aktif, nomor ayat, progres huruf, simpan posisi.
- **Dropdown 2: Pemicu Interaksi** ➔ Toggle saklar Tap Like, Chat, Gift dengan tombol teks murni ("ON"/"OFF").
- **Dropdown 3: Tipografi & Bahasa** ➔ Jenis font Arab, font terjemahan Latin, ukuran font (`➖`, `➕`).
- **Dropdown 4: Posisi & Tata Letak** ➔ Stepper ikon murni (`⬅️`, `⬆️`, `⬇️`, `➡️`).
- **Dropdown 5: Uji Coba** ➔ Tombol aksi teks murni "Tes Huruf", "Tes Ayat", "Reset".

#### F. Sub-Tab 6: Event Broker (`LiveCommentTabBuilder`)
- **Dropdown 1: Koneksi** ➔ Host, Port, IP Cepat, Target, Tambah Target (`⌨️`), status ("TERHUBUNG"/"PUTUS"), tombol ("SAMBUNG"/"PUTUS").
- **Dropdown 2: Saklar Event** ➔ Chip teks murni ("Komentar", "Gift", "Like", "Join", "Follow", "Share", "Penonton") tanpa teks ON/OFF berulang; tombol durasi.
- **Dropdown 3: Overlay Terpisah** ➔ Stepper ikon murni (`⬅️`, `⬆️`, `⬇️`, `➡️`, `➖`, `➕`).
- **Dropdown 4: Overlay Terpadu** ➔ Stepper ikon murni (`⬅️`, `⬆️`, `⬇️`, `➡️`, `➖`, `➕`, `🔍`).
- **Dropdown 5: Top Koin & Tap** ➔ Pilihan orientasi teks murni ("Vertikal", "Horizontal"), posisi cepat ("Kiri", "Kanan", "Bawah"), stepper ikon murni (`⬅️`, `⬆️`, `⬇️`, `➡️`, `➖`, `➕`, `🔍`, `↺`, `▶`).
- **Dropdown 6: Foto Melayang** ➔ Target event teks murni ("Semua", "Chat", "Gift", "Like", dsb), stepper ikon murni (`➖`, `➕`, `▶`), zona teks murni ("Area").
- **Dropdown 7: Uji Coba** ➔ Tombol simulasi teks murni ("Chat", "Gift", "Like", "Top 10", "Foto").

---

### 3. Key Product Decisions & Trade-Offs

- **Keputusan 1: Eliminasi Total Doa & Komentar dan Inspeksi Scan**
  - *Pendekatan*: Menghapus tombol tab `btnModeDoaTab` dan `btnModeScanLogTab` serta kontainer UI terkait dari `LiveCommentTabBuilder` dan `FloatingControlService`. Mode default dialihkan ke `Jualan` saat pertama kali membuka Tab Notif.
  - *Alasan*: Menghilangkan beban visual dan kode yang tidak berguna sesuai instruksi pengguna, serta memperlebar ruang untuk sub-tab yang produktif.
- **Keputusan 2: Pemusatan Primitif UI Dropdown & Stepper di `FloatingUiWidgets`**
  - *Pendekatan*: Menggunakan fungsi terpusat untuk kartu dropdown, tombol stepper ikon murni, dan chip toggle teks murni di seluruh file builder.
  - *Alasan*: Menjamin konsistensi visual 100% di semua 6 sub-tab dan memastikan tidak ada deviasi gaya.
- **Keputusan 3: Penegakan Ketat Aturan Ikon vs Kata**
  - *Pendekatan*: Stepper posisi/ukuran murni memakai ikon tanpa kata; opsi status dan orientasi murni memakai teks tanpa ikon/emoji.
  - *Alasan*: Memberikan tampilan antarmuka yang bersih, profesional, cepat dibaca di layar live, dan bebas dari kebisingan visual.

---

### 4. Technical Architecture & File Modifications

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         FloatingControlService (Tab 4)                      │
│                                       │                                     │
│   ┌───────────────────────────────────┴────────────────────────────────┐    │
│   ▼                                                                    ▼    │
│ [Mode Switcher: 6 Sub-Tab Bersih]                    [FloatingUiWidgets]    │
│ [Jualan] [Stiker] [Watermark] [Waktu] [Khatam] [Event]                      │
│                                                       - CollapsibleCard     │
│                                                       - IconActionBtn       │
│                                                       - TextActionBtn       │
│                                                       - DenseToggleChip     │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │
     ┌──────────────┬──────────────┬────┴─────────┬──────────────┬──────────────┐
     ▼              ▼              ▼              ▼              ▼              ▼
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│Jualan    │   │Stiker    │   │Watermark │   │Waktu     │   │Khatam    │   │Event     │
│(Buyer    │   │(Stiker   │   │(Watermark│   │(AutoTime │   │(Quran    │   │(Live     │
│ Notif)   │   │ Tab)     │   │ Tab)     │   │ Tab)     │   │ Khatam)  │   │ Broker)  │
│5 Dropdown│   │4 Dropdown│   │4 Dropdown│   │3 Dropdown│   │5 Dropdown│   │7 Dropdown│
└──────────┘   └──────────┘   └──────────┘   └──────────┘   └──────────┘   └──────────┘
```

#### File yang Dimodifikasi:
1. `app/src/main/java/com/tajasb/floating/components/FloatingUiWidgets.kt`:
   - Tambahkan fungsi pembangun kartu collapsible terstandarisasi, tombol ikon murni, tombol teks murni, dan chip toggle padat.
2. `app/src/main/java/com/tajasb/floating/tabs/LiveCommentTabBuilder.kt`:
   - Hapus Sub-Tab Doa & Komentar dan Sub-Tab Inspeksi Scan dari bilah switcher.
   - Pindahkan Sub-Tab Event Broker ke posisi terdepan/terintegrasi penuh dengan 7 kartu accordion dropdown dan aturan ketat ikon vs kata.
3. `app/src/main/java/com/tajasb/floating/tabs/BuyerNotifTabBuilder.kt`:
   - Restrukturisasi Sub-Tab Jualan ke 5 kartu accordion dropdown terstandarisasi, tombol ikon murni, dan tombol teks murni.
   - Sesuaikan logika visibilitas tab di `updateBuyerNotifUi()` tanpa referensi ke Doa/Inspeksi.
4. `app/src/main/java/com/tajasb/floating/tabs/StikerTabBuilder.kt`:
   - Restrukturisasi Sub-Tab Stiker ke 4 kartu accordion dropdown terstandarisasi.
5. `app/src/main/java/com/tajasb/floating/tabs/WatermarkTabBuilder.kt`:
   - Restrukturisasi Sub-Tab Watermark ke 4 kartu accordion dropdown terstandarisasi.
6. `app/src/main/java/com/tajasb/floating/tabs/AutoTimeTabBuilder.kt`:
   - Restrukturisasi Sub-Tab Waktu ke 3 kartu accordion dropdown terstandarisasi.
7. `app/src/main/java/com/tajasb/floating/tabs/QuranKhatamTabBuilder.kt`:
   - Restrukturisasi Sub-Tab Khatam ke 5 kartu accordion dropdown terstandarisasi.
8. `app/src/main/java/com/tajasb/floating/FloatingServiceViewHost.kt` & `app/src/main/java/com/tajasb/FloatingControlService.kt`:
   - Hapus variabel dan referensi view usang yang terkait dengan Doa dan Inspeksi scan.
9. Verifikasi kompilasi menyeluruh via `compile_applet`.
