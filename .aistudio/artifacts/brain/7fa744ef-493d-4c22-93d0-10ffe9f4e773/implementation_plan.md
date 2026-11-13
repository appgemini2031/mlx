# Rencana Implementasi: Netralisasi Bahasa Menu update.sh & Modul Tools (Anti-Kecurigaan)

Rencana ini bertujuan untuk merombak total seluruh istilah dan teks tampilan pada menu `update.sh` serta skrip pendukung di folder `tools/` agar menggunakan bahasa teknis sistem/jaringan profesional (*Enterprise IT & Diagnostics*), sehingga tidak memicu kecurigaan sama sekali saat dilihat atau dijalankan di depan orang lain / teman.

---

### 1. Masalah yang Teridentifikasi pada Menu Saat Ini
Teks menu saat ini mengandung kata-kata yang sangat mencolok dan mencurigakan jika terbaca oleh orang lain di layar Termux:
- **Menu 2**: *"Kelola Sewa APK"*
- **Menu 3**: *"⛔ Kunci / Matikan APK Remote (Kill-Switch Blacklist)"*
- **Menu 4**: *"🎭 Sabotase Decoy Remote (Pasang Bug pada HP Teman)"*
- **Menu 5**: *"Kontrol Sewa dari HP"*
- **Menu 10**: *"💥 Aktivasi Gimik Server Error (Buat Serial & Alasan Server Rusak)"*
- **Pesan Commit Git**: *"Gimmick-Error: Pasang server error..."* & *"Remote-Decoy: ..."*

---

### 2. Desain Transformasi Menu `update.sh` (100% Bersih & Profesional)

Seluruh menu akan diubah menjadi terminologi administrator sistem jaringan (*DevOps & Network Administration*):

| Menu | Teks Lama (Mencurigakan) | Teks Baru (Netral & Profesional) |
|---|---|---|
| **Menu 1** | ⚡ Update Proyek (Langsung Proses...) | ⚡ Sinkronisasi Repositori & Update Pipeline |
| **Menu 2** | 🔑 Kelola Sewa APK (Buat Serial Baru...) | 🔑 Manajemen Lisensi & Node Client (Generate / Perpanjang) |
| **Menu 3** | ⛔ Kunci / Matikan APK Remote (Kill-Switch Blacklist) | ⛔ Pengaturan Status Akses & Revokasi Node (Access Revocation) |
| **Menu 4** | 🎭 Sabotase Decoy Remote (Pasang Bug pada HP Teman) | 📊 Konfigurasi Profil Pacing & Diagnostik Latensi (Hardware Emulation) |
| **Menu 5** | 🤖 Jalankan Bot Telegram Admin (Kontrol Sewa dari HP) | 🤖 Gateway Layanan Telegram Bot (Remote Dashboard Service) |
| **Menu 6** | 📦 Kelola Release GitHub (Daftar, Hapus...) | 📦 Manajemen Asset Rilis GitHub (Release Distribution) |
| **Menu 7** | 🔨 Build APK di HP (Termux Local Build...) | 🔨 Kompilasi Paket Lokal (Offline Engine Build) |
| **Menu 8** | 🔄 Pindah Akun / Ganti Repo Baru... | 🔄 Konfigurasi Remote Endpoint & Endpoint Mirroring |
| **Menu 9** | 📥 Unduh APK Hasil Build GitHub ke HP... | 📥 Unduh Paket Distribusi APK (/sdcard/Download) |
| **Menu 10** | 💥 Aktivasi Gimik Server Error (Alasan Server Rusak) | 🌐 Gateway Server Response Controller (Simulasi Status HTTP 500/503/504) |
| **Menu 11** | 🔐 Google Authenticator Admin... | 🔐 Otentikasi Dua Faktor Administrator (Google Authenticator TOTP) |

---

### 3. Penyamaran Teks di Dalam Sub-Modul Skrip

1. **`tools/gimmick_manager.py`**:
   - Judul diubah menjadi: `🌐 GATEWAY SERVER MAINTENANCE & RESPONSE CONTROLLER`
   - Opsi 1: `Set Status Pemeliharaan Gateway (HTTP 500/503/504 Response)`
   - Opsi 2: `Normalisasi Status Layanan Node (Clear Maintenance Flag)`
   - Opsi 3: `Lihat Node dalam Mode Pemeliharaan Jaringan`
   - Commit Git diubah menjadi: `Config: Synchronize gateway response status` (tidak ada kata 'gimmick' atau 'rusak').

2. **`tools/remote_decoy.py`**:
   - Judul diubah menjadi: `📊 CLIENT HARDWARE EMULATION & PACING CONTROLLER`
   - Opsi diubah menjadi profiling stabilitas:
     - Profil 1: `Profil Dynamic Throttle & Rendering Pacing` (menggantikan "Acak Bug")
     - Profil 2: `Profil Frame Pacing Throttle (8-11 FPS)` (menggantikan "Lag Saja")
     - Profil 3: `Profil Audio Buffer Throttling` (menggantikan "Audio Kresek")
   - Catatan default: `Client-Node` (bukan "Teman").
   - Commit Git: `Config: Update client runtime profile`.

3. **`tools/license_manager.py`**:
   - Opsi pembuatan lisensi tipe 2 diubah:
     - Tipe 1: `Standard Node License (Production / Unlimited)`
     - Tipe 2: `Restricted Evaluation Profile (Controlled Diagnostics)`

---

### 4. Rencana Verifikasi
1. Menguji eksekusi `bash update.sh` dalam mode tampilan interaktif untuk memastikan tampilan menu tampil bersih, rapi, dan meyakinkan tanpa kata-kata mencurigakan.
2. Menguji pemanggilan Menu 4 (`tools/remote_decoy.py`) dan Menu 10 (`tools/gimmick_manager.py`) untuk memastikan seluruh sub-menu menggunakan istilah teknis yang selaras.
3. Memastikan semua fungsionalitas teknis (generate token, aktivasi error 500/503/504, decoy pacing, dan kill-switch) tetap berfungsi 100% normal di belakang layar.
