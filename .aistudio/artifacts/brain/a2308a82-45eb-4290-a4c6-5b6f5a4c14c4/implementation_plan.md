# Rencana Redesain Konsep & Alur Pintasan (Sistem Pin & Unpin Aplikasi)

Revisi alur **Pintasan Cerdas** dari sistem pelacak frekuensi otomatis (lencana "1x", "2x") menjadi **Sistem Pin (📌 Sematkan) & Unpin (📍 Lepas Pin) Manual** yang bersih, terkontrol penuh, dan sesuai keinginan pengguna.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Perubahan Alur Pintasan (Permintaan Pengguna)**:
> - Menghilangkan lencana angka buka "x" / frekuensi otomatis.
> - Mengganti dengan fitur **Pin (📌 Sematkan)** dan **Unpin (📍 Lepas Pin)** manual pada setiap aplikasi HP.
> - Aplikasi terpin akan selalu berada di seksi paling atas (**📌 Aplikasi Disematkan**).
> - Dilengkapi pencarian aplikasi interaktif untuk mempermudah mencari dan menyematkan aplikasi favorit.

---

## 1. Overview & Core Concept

- **Problem**: Sistem perhitungan frekuensi otomatis ("1x", "2x") sebelumnya terasa kurang pas dan tidak fleksibel bagi pengguna yang ingin memilih aplikasi tertentu secara manual.
- **Solution**:
  1. **Seksi Aplikasi Terpin (📌 Disematkan)**: Menampilkan barisan aplikasi pilihan pengguna di posisi teratas.
  2. **Pencarian & Pin Cepat**: Kolom pencarian `🔍 Cari aplikasi...` di mana pengguna dapat mencari aplikasi apa saja di HP, lalu menekan tombol **`📌 Pin`** untuk langsung menyematkannya ke atas, atau **`📍 Unpin`** untuk melepasnya.
  3. **Penyimpanan Permanen**: Daftar package name aplikasi terpin disimpan secara permanen di `SharedPreferences` (`KEY_PINNED_APPS_JSON`).

---

## 2. User Experience & Visual Design

- **Alur Pengguna (User Flow di Tab 🚀 Pintasan)**:
  1. **Seksi Teratas (📌 Disematkan)**:
     - Jika ada aplikasi terpin, muncul seksi khusus "📌 Aplikasi Disematkan" dengan tombol aksi cepat **`Buka`** dan **`📍 Unpin`**.
     - Jika belum ada, muncul petunjuk singkat: *"Belum ada aplikasi disematkan. Cari dan tekan 📌 Pin pada aplikasi favorit Anda di bawah."*
  2. **Seksi Pencarian & Semua Aplikasi (📱 Semua Aplikasi)**:
     - Input pencarian live `🔍 Cari aplikasi...`.
     - Setiap baris aplikasi memiliki ikon asli, nama aplikasi, tombol **`Buka`**, dan tombol **`📌 Pin`** / **`📍 Unpin`**.
  3. **Visual UI Bersih**:
     - Lencana "x" dihapus total.
     - Setiap baris aplikasi terpin menggunakan latar belakang *blue glow* (`#111E36`) dengan garis tepi cyan (`#0284C7`).

---

## 3. Key Product Decisions & Trade-Offs

- **Pin/Unpin Manual vs Auto-Count**:
  - *Kenapa*: Memberikan kontrol 100% kepada pengguna untuk menentukan aplikasi apa saja yang menjadi pintasan utama tanpa bergantung pada algoritma hitung otomatis.
  - *Dampak*: Tampilan jauh lebih bersih, rapi, dan mudah diatur sesuai kebutuhan pengguna.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                 TAB 🚀 PINTASAN RESTRUCTURED                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  [ 🔍 Cari aplikasi...                    ] [ ✕ Bersihkan ] │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│  📌 APLIKASI DISEMATKAN (Pinned Items Section)             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 📱 Mobile Legends           [ Buka ] [ 📍 Unpin ]    │  │
│  │ 📱 WhatsApp                 [ Buka ] [ 📍 Unpin ]    │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│  📱 SEMUA APLIKASI (Filtered / Full Search Results)        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 📱 Shopee                   [ Buka ] [ 📌 Pin ]      │  │
│  │ 📱 YouTube                  [ Buka ] [ 📌 Pin ]      │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

- **Penyimpanan Data**:
  - `KEY_PINNED_APPS_JSON` di `SharedPreferences` menyimpan daftar urutan `packageName` yang disematkan pengguna.

---

## 5. Steps to Implement

1. **Penyimpanan State Pin/Unpin**:
   - Menambahkan struktur data `pinnedPackageNames: MutableList<String>` di `FloatingControlService.kt`.
   - Mengimplementasikan `loadPinnedAppsFromPrefs()` dan `savePinnedAppsToPrefs()`.
2. **Pembaruan Kartu Aplikasi & Aksi Pin/Unpin**:
   - Memperbarui `buildPintasanAppCard()` dengan tombol **`📌 Pin`** / **`📍 Unpin`** menggantikan lencana "x".
   - Mengurutkan seksi "📌 Disematkan" di posisi paling atas dan seksi "📱 Semua Aplikasi" di bawahnya.
3. **Peningkatan Form Side-Drawer & Overlay**:
   - Memastikan Side-Drawer di tepi layar menyajikan tampilan Pin/Unpin secara responsif.
4. **Verifikasi Kompilasi & Test**:
   - Jalankan `compile_applet` dan unit test `gradle :app:testDebugUnitTest`.
