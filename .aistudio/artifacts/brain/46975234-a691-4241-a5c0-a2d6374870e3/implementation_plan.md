# Sistem Sewa, Kunci APK Remote, dan Anti-Manipulasi Jam (Gerbang Utama: update.sh)

Solusi sistematis dan 100% gratis untuk mengelola sewa APK CuanCame dengan **`update.sh` sebagai gerbang awal tunggal di Termux**, terintegrasi dengan **Bot Telegram Admin**, tanpa biaya sewa server atau VPS bulanan. Sistem ini dirancang tahan terhadap pembajakan/pencurian APK (Hardware-Locked), kebal terhadap manipulasi tanggal/jam oleh penyewa (Multi-Layer Time Guard), dan memiliki fitur pemutus/pengunci APK jarak jauh (Remote Kill-Switch) bahkan ketika HP server Termux admin sedang offline/mati.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Keputusan Utama Sesuai Arahan Pengguna:**
> 1. **Gerbang Awal Tetap Menggunakan `update.sh`**:
>    Seluruh menu manajemen sewa, pembuatan kunci aktivasi, remote kill-switch (matikan APK), dan bot Telegram dikonsolidasikan langsung ke dalam skrip utama **`update.sh`**. Pengguna tidak perlu membuka banyak file skrip terpisah; cukup jalankan `bash update.sh` atau gunakan argumen cepat (`--license`, `--kill`, `--bot`).
> 2. **Server Termux Tidak Perlu Online 24 Jam**:
>    - Pembuatan kunci menggunakan **Kriptografi Asimetris ECDSA (NIST P-256)**: Termux memegang Private Key, APK memegang Public Key. Validasi sewa di APK bersifat mandiri tanpa perlu menghubungi Termux secara realtime.
>    - Remote Kill-Switch (Pematian APK) menggunakan **GitHub Gist / Telegram Channel Publik Gratis** sebagai papan pengumuman (*Noticeboard Blacklist*). Admin mengupdate blacklist saat Termux/Bot online, dan APK penyewa mengecek blacklist tersebut secara silent saat terhubung ke internet.
> 3. **Perlindungan Anti-Manipulasi Jam Berlapis**:
>    - *Hardware Monotonic Clock (`SystemClock.elapsedRealtime()`)*: Melacak waktu berjalan perangkat keras yang kebal terhadap perubahan setelan jam manual di HP penyewa.
>    - *Anti-Clock Rollback High-Water Mark*: Mencatat waktu puncak; jika tanggal/jam HP dimundurkan ke masa lalu, APK seketika mengunci diri.
>    - *Universal HTTP Date CDN Sync*: Sinkronisasi waktu atomik dunia dari header HTTP `Date:` server global (Google/Cloudflare CDN) tanpa server khusus.

---

## 1. Overview & Alur Kerja Lengkap

```
                           ┌──────────────────────────┐
                           │    TERMINAL TERMUX HP    │
                           │     bash update.sh       │
                           └─────────────┬────────────┘
                                         │
        ┌────────────────────────────────┼────────────────────────────────┐
        ▼                                ▼                                ▼
┌─────────────────┐            ┌──────────────────┐             ┌───────────────────┐
│ Menu 2: SEWA    │            │ Menu 3: KUNCI    │             │ Menu 4: TELEGRAM  │
│ Generate Serial │            │ Matikan / Kill   │             │ Jalankan Bot      │
│ (1, 7, 30 Hari, │            │ APK Tertentu     │             │ Pengelola Otomatis│
│ Lifetime)       │            │ (Blacklist ID)   │             │ di Background     │
└────────┬────────┘            └────────┬─────────┘             └─────────┬─────────┘
         │                              │                                 │
         │ Kirim Kunci ke WA            │ Update Noticeboard Publik       │ Kontrol via HP
         ▼                              ▼                                 ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                            APK CUANCAME DI HP PENYEWA                             │
│                                                                                   │
│  1. Hardware ID Check: CC-XXXX-XXXX-XXXX (Tolak jika APK dicuri ke HP lain)      │
│  2. Silent Blacklist Check: Kunci total jika ID ada di Noticeboard               │
│  3. Time Guard: Cegah manipulasi tanggal (Monotonic + HTTP Date CDN + Rollback)   │
└───────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Struktur Menu Baru di `update.sh` (Gerbang Awal Tunggal)

Ketika admin menjalankan `bash update.sh` di Termux, menu interaktif yang tampil:

```
=======================================================
🚀 CUANCAME ENTERPRISE MANAGER (GATEWAY UTAMA v4.2)
=======================================================
🤖 Pilih Menu:
  1) ⚡ Update Proyek (Langsung Proses & Tanpa Hambatan)
  2) 🔑 Kelola Sewa APK (Buat Serial Baru, Perpanjang, Cek Status)
  3) ⛔ Kunci / Matikan APK Remote (Kill-Switch Blacklist)
  4) 🤖 Jalankan Bot Telegram Admin (Kontrol Sewa dari Chat HP)
  5) 📦 Kelola Release GitHub (Daftar, Hapus Satu, Hapus Semua)
  6) 🔨 Build APK di HP (Termux Local Build - Bebas Limit GitHub)
👉 Pilihan Anda [1-6, default 1 (Enter)]: 
```

### Dukungan Perintah Cepat (Shortcut Arguments) di `update.sh`:
- `bash update.sh` ➔ Membuka menu interaktif di atas.
- `bash update.sh --license` (atau `-k`) ➔ Langsung masuk ke Generator Kunci Sewa.
- `bash update.sh --kill [DEVICE_ID]` ➔ Seketika mematikan/mencabut izin APK dengan ID tersebut.
- `bash update.sh --bot` ➔ Menjalankan bot Telegram admin di background Termux.
- `bash update.sh --build` (atau `-b`) ➔ Kompilasi APK langsung di HP.
- `bash update.sh --release` (atau `-r`) ➔ Manajemen release APK di GitHub.

---

## 3. Detail Mekanisme Fitur

### A. Alur Pembuatan Sewa & Aktivasi (Menu 2)
1. Calon penyewa menyalin **ID Perangkat** dari menu aktivasi APK CuanCame (misal `CC-8A2F-9D4E`).
2. Admin membuka `update.sh` di Termux (atau via Bot Telegram):
   - Masukkan ID Perangkat: `CC-8A2F-9D4E`
   - Pilih Paket: 3 Hari, 7 Hari, 30 Hari, 1 Tahun, atau Kustom Hari.
   - Masukkan Catatan/Nama Penyewa (misal `Budi-Live`).
3. Skrip menandatangani payload dengan Private Key ECDSA NIST P-256 dan menghasilkan:
   `CC-LIC-ey...`
4. Skrip otomatis menyalin kunci ke clipboard Termux dan menampilkan template pesan siap kirim untuk WhatsApp.
5. Penyewa menempelkan kunci ke APK CuanCame. APK memverifikasi dengan Public Key yang tertanam. **Langsung aktif tanpa butuh server online!**

### B. Alur Remote Kill-Switch / Pematian APK (Menu 3)
1. Jika penyewa melanggar perjanjian, belum membayar perpanjangan, atau meminta pembatalan:
   - Admin memilih Menu 3 di `update.sh` atau mengirim perintah `/kill CC-8A2F-9D4E` ke bot Telegram.
2. Skrip menambahkan ID perangkat target ke **Noticeboard Blacklist** gratis (berbasis GitHub Gist terenkripsi atau Telegram Channel publik).
3. Di sisi APK penyewa:
   - Setiap kali aplikasi dibuka atau menjalankan live streaming (saat ada koneksi internet), APK melakukan pemeriksaan senyap (*silent background check*) ke Noticeboard.
   - Jika ID perangkat terdaftar di daftar hitam:
     - Lisensi lokal seketika dihapus dan vault dikunci permanen.
     - Floating window & layanan VCAM berhenti total.
     - Muncul dialog kunci penuh: *"⛔ AKSES APLIKASI TELAH DINONAKTIFKAN OLEH ADMINISTRATOR. HUBUNGI ADMIN."*

### C. Alur Bot Telegram Admin di Termux (Menu 4)
1. Admin cukup membuat bot gratis di `@BotFather` (hanya butuh waktu 1 menit).
2. Simpan token bot di Termux sekali saja (disimpan di `~/.cuancame_bot.conf`).
3. Jalankan bot via Menu 4 `update.sh` atau `bash update.sh --bot`.
4. Dari HP admin manapun, buka chat ke bot Telegram tersebut:
   - Tombol `/sewa` ➔ Buat kunci aktivasi sewa baru.
   - Tombol `/kill` ➔ Matikan APK penyewa tertentu.
   - Tombol `/cek` ➔ Cek sisa hari dan status masa aktif serial key.
   - Tombol `/list` ➔ Lihat daftar penyewa aktif dan daftar blacklist.

### D. Anti-Pencurian & Anti-Manipulasi Jam di APK (Kotlin)
1. **Hardware-Lock (Anti-Clone)**:
   Terikat pada MediaDrm Widevine Chip ID + SoC Fingerprint. File APK yang disalin ke HP lain otomatis berubah Device ID-nya dan tidak bisa memakai kunci HP sebelumnya.
2. **Universal HTTP Date Sync**:
   APK membaca header `Date:` dari respon CDN dunia (Google/Cloudflare) secara asinkron tanpa membebani performa.
3. **Anti-Clock Rollback Guard**:
   Jika penyewa mencoba memundurkan jam sistem di setelan HP untuk memperpanjang sewa, APK mendeteksi waktu mundur dari *high-water mark* dan langsung memblokir sementara: *"Jam Sistem Dimundurkan. Silakan Aktifkan Tanggal & Waktu Otomatis."*
4. **Monotonic Hardware Timer (`elapsedRealtime`)**:
   Melacak durasi aplikasi berjalan sejak HP dihidupkan, mencegah kecurangan dengan mematikan koneksi internet terus-menerus.

---

## 4. Perubahan File yang Direncanakan

### 1. `update.sh` (Pembaruan Utama)
- Memperluas fungsi `run_license_keygen` dengan opsi perpanjangan sewa dan pencatatan riwayat sewa lokal.
- Menambahkan fungsi `manage_remote_kill` untuk mematikan dan membuka kunci ID perangkat tertentu.
- Menambahkan fungsi `run_telegram_bot` (daemon ringan menggunakan Python bawaan Termux).
- Memperbarui menu interaktif awal (Menu 1-6) dan parsing argumen CLI (`--kill`, `--bot`, `--license`).

### 2. `app/src/main/java/com/vcam/license/LicenseManager.kt`
- Menambahkan pengecekan daftar hitam online (Remote Blacklist Check) yang tahan kegagalan jaringan (fail-safe).
- Memperkuat deteksi rollback jam dengan `SystemClock.elapsedRealtime()`.
- Menambahkan mekanisme penghancuran lisensi (*auto-revoke*) seketika jika perangkat terdaftar di blacklist.

### 3. `app/src/main/java/com/vcam/license/TimeGuard.kt` (File Baru)
- Mengambil header waktu atomik via HTTP `Date:` dari CDN terpercaya.
- Menghitung drift/selisih waktu lokal dengan waktu dunia sejati.

### 4. `app/src/main/java/com/vcam/ui/screens/LicenseScreen.kt`
- Menampilkan status keamanan waktu dan indikator sinkronisasi.
- Menampilkan pesan pemblokiran khusus jika APK dinonaktifkan oleh administrator.
