# Modul Optimasi Perangkat & Fokus Jaringan (Device & Network Booster)

Rencana optimasi menyeluruh untuk menjaga kelancaran live streaming pada perangkat dengan spesifikasi RAM ~2600 MB dan koneksi 30 Mbps, mencegah lagging akibat beban aplikasi latar belakang dan fluktuasi jaringan.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Fitur ini menambahkan 2 modul utama: **Pembersih RAM Otomatis (Turbo RAM Cleaner)** dan **Fokus Jaringan via Firewall VPN Lokal (Live Traffic Prioritizer)** untuk mengisolasi kuota internet agar hanya dinikmati oleh aplikasi Live Streaming (TikTok/Shopee) dan mesin VCAM.

- **Konfirmasi Modul RAM**: Pembersihan proses background aman (`killBackgroundProcesses`) dipadukan dengan pemangkasan buffer GL dan mode rendering ringan saat RAM menipis (< 400 MB).
- **Konfirmasi Modul Jaringan**: Menggunakan `VpnService` lokal (100% offline, tanpa server luar) untuk memblokir lalu lintas internet aplikasi selain aplikasi Live saat tombol saklar aktif.
- **Konfirmasi Modul Suhu/FPS**: Penguncian frame rate 30 FPS adaptif guna mencegah *thermal throttling* (penurunan performa akibat HP panas).

---

## 1. Overview & Core Concept

### Masalah Pengguna
- Perangkat memiliki banyak aplikasi terpasang dengan kapasitas RAM terbaca ~2600 MB.
- Koneksi internet 30 Mbps rentan tersendat (*packet drop*) jika aplikasi lain (media sosial, backup foto, download otomatis) berjalan bersamaan di latar belakang.
- Kekhawatiran terjadi patah-patah (*stuttering/lag*) saat live streaming berlangsung lama.

### Solusi yang Dihadirkan
1. **Turbo RAM Cleaner**: Melepaskan memori aplikasi latar belakang yang tidak aktif dan merapikan alokasi buffer internal VCAM hanya dengan 1 ketukan atau secara otomatis berkala.
2. **Local VPN Traffic Blocker**: Layanan VPN lokal di dalam perangkat yang hanya meloloskan paket data dari aplikasi Live Streaming yang ditargetkan (misal: TikTok, Shopee Live) serta memblokir koneksi dari aplikasi lain selama sesi live.
3. **Pengatur Beban GPU & Suhu (Thermal/FPS Lock)**: Mengunci rendering pada 30 FPS atau 25 FPS hemat daya untuk menjaga temperatur CPU/GPU tetap dingin.
4. **Dashboard Kontrol di Aplikasi & Floating Window**: Indikator real-time pemakaian RAM, status blokir data, dan tombol cepat `[⚡ Turbo Clean]` langsung di jendela melayang.

---

## 2. User Experience & Visual Design

### Alur Pengguna (User Flow)
1. **Sebelum Live (Layar Optimasi Perangkat)**:
   - Pengguna membuka tab baru **"⚡ Optimasi"** di aplikasi utama.
   - Melihat indikator visual: Kapasitas RAM terpakai/tersisa, Suhu baterai, dan Status Jaringan.
   - Mengaktifkan saklar **"Fokus Jaringan (Blokir APK Lain)"** dan menekan tombol **"⚡ Turbo Bersihkan RAM"**.
2. **Saat Live Berlangsung (Floating Window)**:
   - Floating Window menampilkan chip pintas:
     - `[⚡ Bersihkan RAM (2.1 GB Bebas)]`
     - `[🛡️ Fokus Data: AKTIF]`
   - Jika RAM tiba-tiba turun ke bawah batas aman (< 400 MB), status OSD/banner memberi tahu pengguna secara halus dan menjalankan pembersihan otomatis.

```
┌─────────────────────────────────────────────────────────────┐
│ ⚡ OPTIMASI PERANGKAT & JARINGAN (BOOSTER LIVE)              │
├─────────────────────────────────────────────────────────────┤
│  RAM Tersedia: 2,150 MB / 2,600 MB (Sehat: 82%)             │
│  [========================================......]           │
│  Suhu HP: 34.5°C (Dingin)  •  Koneksi: 30 Mbps              │
│                                                             │
│  [ ⚡ Turbo Bersihkan RAM Sekarang ]                        │
├─────────────────────────────────────────────────────────────┤
│  🛡️ Fokus Aliran Data (Local Firewall)                       │
│  [ Saklar: AKTIF ]                                          │
│  Status: Blokir 38 aplikasi latar belakang                  │
│  Dilewati: TikTok, Shopee, VCAM Virtual Camera              │
├─────────────────────────────────────────────────────────────┤
│  🎮 Stabilisator Frame Rate & Suhu                          │
│  [ Saklar: 30 FPS Stabil (Direkomendasikan) ]                │
│  Mengurangi konsumsi baterai & mencegah ponsel panas.        │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Key Product Decisions & Trade-Offs

### Keputusan 1: Pemblokiran Jaringan Menggunakan Local VpnService
- **Pilihan**: Mengimplementasikan `android.net.VpnService` lokal tanpa routing internet ke server luar (100% diproses di perangkat).
- **Alasan**: Android tidak mengizinkan pemblokiran koneksi aplikasi lain tanpa izin root, kecuali menggunakan mekanisme resmi `VpnService`. Dengan filter internal, paket dari aplikasi non-target langsung di-drop, sehingga 100% bandwidth 30 Mbps difokuskan untuk TikTok/Shopee.
- **Alternatif**: Mengarahkan ke pengaturan hemat data bawaan (kurang efektif karena banyak aplikasi tetap bisa sync di latar belakang).

### Keputusan 2: Pembersihan RAM Ramah Lingkungan
- **Pilihan**: Memanggil `ActivityManager.killBackgroundProcesses()` untuk paket aplikasi non-kritis + `System.gc()` internal + pengetatan pool GL BufferQueue.
- **Alasan**: Aman dari crash sistem, tidak merusak data aplikasi lain, namun instan membebaskan ratusan megabyte RAM sebelum sesi live dimulai.

### Keputusan 3: Penempatan Akses Cepat di Floating Window
- **Pilihan**: Menambahkan tombol aksi cepat di tab `Floating Window`.
- **Alasan**: Pengguna tidak perlu keluar dari aplikasi TikTok/Shopee saat live untuk membersihkan RAM atau mengaktifkan blokir data.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                   UI & Floating Layer                  │
│  - MediaManagerScreen / OptimizationTab                │
│  - FloatingControlService Quick Action Chips           │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│              DeviceOptimizationManager                 │
│  - getDeviceMemoryInfo() (Total, Avail, Threshold)     │
│  - triggerTurboRamClean() (killBackgroundProcesses)    │
│  - monitorBatteryTemperature()                         │
│  - setFpsCap(30 / 25 / 60)                             │
└─────────────┬────────────────────────────┬─────────────┘
              │                            │
┌─────────────▼──────────────┐ ┌───────────▼─────────────┐
│    LiveFocusVpnService     │ │   GlVideoRenderer &     │
│ (Local Loopback Firewall)  │ │   BufferQueueManager    │
│ - Drop non-target packets  │ │ - Trim dormant textures │
│ - Whitelist TikTok/Shopee  │ │ - Enforce 30 FPS lock   │
└────────────────────────────┘ └─────────────────────────┘
```

### Rencana File & Komponen yang Dikerjakan:
1. **`com.vcam.optimizer.DeviceOptimizationManager`**:
   - Logika kalkulasi RAM (tersedia vs total), deteksi suhu baterai, pembersih proses latar belakang, dan pengatur profil performa.
2. **`com.vcam.optimizer.LiveFocusVpnService`**:
   - Layanan VPN lokal Android (`VpnService`) dengan *allowed applications* yang diisi otomatis dengan target aplikasi Live (TikTok, Shopee, VCAM).
3. **`AndroidManifest.xml`**:
   - Deklarasi izin `KILL_BACKGROUND_PROCESSES` dan service `LiveFocusVpnService` dengan izin `BIND_VPN_SERVICE`.
4. **`VcamModels.kt` & `VcamRepository.kt`**:
   - Konfigurasi status `isLiveFocusVpnEnabled`, `isAutoRamCleanEnabled`, `fpsGovernorLimit`, dan event broadcast.
5. **UI Jetpack Compose (`OptimizationScreen.kt`) & Floating Window Tab**:
   - Antarmuka visual pengukur RAM, tombol 1-tap turbo clean, dan toggle fokus internet.
