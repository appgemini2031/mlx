# Revisi Akurasi Pemindaian Komentar TikTok Live & Pencatatan Posisi Penonton

Memperbaiki logika pemindaian aksesibilitas secara langsung berdasarkan 4 gambar referensi yang dilampirkan: mencegah pemindaian saat kolom chat tertutup menu/sheet produk (Ref 2), menyaring pesan sistem asisten TikTok & pin produk (Ref 1), membaca komentar penonton secara presisi (Ref 3 & 4), serta mendeteksi dan menyimpan koordinat posisi jumlah penonton ke konfigurasi lokal perangkat.

## User Review & Critical Decisions

> [!IMPORTANT]
> Penyesuaian berdasarkan instruksi dan 4 gambar referensi:
> - **Satu Perangkat**: Tidak ada konsep 2 HP. Seluruh proses berjalan langsung di 1 HP pada aplikasi TikTok yang sedang berjalan.
> - **Komentar Tertutup Menu (Ref 2)**: Saat menu produk/skrip/dialog bawah terbuka menutupi chat, pemindaian komentar langsung **dijeda total** agar teks produk ("Flash Sale", "Sandal Anak", "Sematkan") tidak terbaca sebagai komentar.
> - **Penyaringan Pesan Asisten TikTok (Ref 1)**: Kalimat bot platform seperti *"Kami sedang memberi tahu orang-orang..."*, *"Terus semangat! Energimu..."*, dan *"Semua fan di Fan Club..."* dibuang ke kategori DITOLAK SISTEM.
> - **Penyimpanan Posisi Penonton**: Mendeteksi lokasi ikon/angka penonton di area atas (Ref 1: `0`, Ref 3: `5.4K`) dan menyimpan koordinat layarnya ke SharedPreferences perangkat.

---

## 1. Overview & Core Concept

1. **Deteksi Menu Penutup (Ref 2)**:
   - Jika layar sedang membuka drawer/bottom-sheet belanja (terdeteksi header seperti *"Produk"*, *"Skrip"*, *"+ Tambah"*, *"GMV Teratribusi"* yang menutupi koordinat Y > 30%), sistem langsung menandai status `TERTUTUP_MENU` dan menghentikan pembacaan komentar.
2. **Penyaringan Pesan Bot & Pin Produk (Ref 1)**:
   - Pesan pengumuman asisten TikTok yang otomatis muncul saat live dimulai disaring bersih.
   - Kartu sematan produk di bagian bawah (*"Batal sematkan"*, *"Rp19.999"*, *"Flash Sale"*) diproteksi agar tidak masuk ke parsing komentar.
3. **Akurasi Parsing Komentar Asli (Ref 3 & 4)**:
   - Menganalisis container komentar TikTok yang terdiri dari badge/lencana (misal `[△ 2]`), nama pengirim (*"Cantika sary"*, *"aii lestari"*, *"Maelia"*), dan isi chat (*"Kk aku mau bellow"*, *"mukenah"*, *"Oman"*).
   - Memastikan pesan *"sudah bergabung"* dibuang dengan rapi.
4. **Pencatatan Koordinat Jumlah Penonton**:
   - Memindai node di area atas (sekitar status bar/header) yang memuat angka penonton aktif (misal `0`, `5.4K`, `1.2K`).
   - Menyimpan posisi `Rect(left, top, right, bottom)` dan persentase koordinat `(posXpct, posYpct)` ke SharedPreferences perangkat untuk referensi fitur selanjutnya.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Saat Live Berjalan Normal (Ref 1, 3, 4)**:
   - Host siaran langsung seperti biasa. Komentar penonton masuk dan terbaca secara akurat di Tab Inspeksi (`[DITERIMA]`).
   - Pesan asisten platform TikTok dan notifikasi bergabung disortir ke `[DITOLAK SISTEM]`.
2. **Saat Host Membuka Menu Produk / Skrip (Ref 2)**:
   - Host menekan tombol keranjang / menu produk untuk melihat etalase.
   - Di Floating Tab Inspeksi, status langsung menampilkan: `⏸️ DIJEDA (Menu Produk Terbuka)`.
   - Tidak ada satu pun teks judul barang, harga, atau tombol sematan yang masuk mengotori log atau memicu video spill.
   - Begitu menu ditutup, status otomatis kembali `🟢 AKTIF MEMINDAI`.
3. **Pencatatan Lokasi Penonton**:
   - Sistem mendeteksi koordinat elemen jumlah penonton di pojok kanan atas.
   - Floating Window mencatat dan menampilkan koordinatnya:
     `📍 Titik Penonton: X=84%, Y=6% (Tersimpan ke Konfigurasi)`

---

## 3. Key Technical Decisions & Strategies

- **Deteksi Menu Penutup Tanpa Lag**:
  - Memeriksa keberadaan node header panel belanja (misal node dengan teks *"Produk"* berdekatan dengan *"Skrip"* atau *"+ Tambah"* pada area Y 20%–40%). Jika terdeteksi, traversal komentar langsung dihentikan sebelum memproses ribuan karakter deskripsi produk.
- **Pembersihan Pattern Asisten TikTok**:
  - Menambahkan kamus pola:
    - `"memberi tahu orang-orang bahwa kamu sedang LIVE"`
    - `"Terus semangat! Energimu"`
    - `"Semua fan di Fan Club"`
    - `"Batal sematkan"` / `"Sematkan produk"`
- **Penyimpanan Anchor Koordinat Penonton**:
  - Disimpan ke `SharedPreferences` file `vcam_screen_anchors.xml`:
    - `viewer_count_raw`: String (misal `"0"` atau `"5.4K"`)
    - `viewer_count_numeric`: Int
    - `viewer_anchor_x_pct`: Float
    - `viewer_anchor_y_pct`: Float
    - `viewer_anchor_bounds`: String `"left,top,right,bottom"`

---

## 4. Technical Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                 VcamAccessibilityService                    │
│   (Memindai jendela aktif satu layar TikTok)                │
└──────────────────────────────┬──────────────────────────────┘
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
    [1. Deteksi Menu Penutup]      [2. Deteksi Titik Penonton]
    • Ada sheet "Produk/Skrip"?     • Cari ikon/angka di header
    • JIKA YA -> STOP SCAN KOMEN    • Simpan koordinat ke Prefs
    • Status: MENU_COVERED                     │
               │                               ▼
               ▼ (Jika chat tidak tertutup) [SharedPreferences]
    [3. Parsing Komentar Bersih (Ref 3 & 4)]  • anchor_x: 84%
    • Filter Pesan Asisten TikTok (Ref 1)     • anchor_y: 6%
    • Filter Notifikasi "sudah bergabung"     • last_count: "0"
    • Ekstrak @User & Chat Sah
               │
               ▼
┌─────────────────────────────────────────────────────────────┐
│                  CommentScanInspector                       │
│  - Catat entri DITERIMA / DITOLAK SISTEM                   │
│  - Tampilkan status Jeda saat menu produk terbuka          │
│  - Tampilkan koordinat lokasi penonton                     │
└─────────────────────────────────────────────────────────────┘
```

### Verification & Testing Plan

1. **Unit Test (`CommentScanInspectorTest.kt`)**:
   - Menguji pengabaian pesan asisten TikTok Ref 1: *"Kami sedang memberi tahu orang-orang..."*, *"Terus semangat! Energimu..."*.
   - Menguji pengabaian teks menu produk Ref 2: *"Produk"*, *"Skrip"*, *"Sematkan"*, *"Batal sematkan"*, *"Flash Sale"*.
   - Menguji parsing komentar penonton Ref 3 & 4: *"Cantika sary: Kk aku mau bellow"*, *"aii lestari: mukenah"*, *"jilbab bbbb"*.
2. **Build Verification**:
   - Menjalankan `compile_applet` untuk memastikan seluruh perubahan kode terkompilasi bersih tanpa error.
