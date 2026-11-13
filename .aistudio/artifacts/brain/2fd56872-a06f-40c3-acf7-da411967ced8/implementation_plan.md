# Rencana Implementasi: Modularisasi `update.sh`, Self-Update di Awal, dan Pengamanan Kunci Privat dari GitHub

Berdasarkan preferensi pengguna:
1. **Self-Update di Awal**: Pembaruan `update.sh` dilakukan secara otomatis di awal eksekusi sebelum menu interaktif muncul, sehingga pengguna tidak perlu menekan nomor menu dua kali.
2. **Modularisasi Terpisah & Pengamanan Git**: Memecah `update.sh` menjadi modul-modul terpisah di dalam direktori `tools/`, memisahkan kunci privat ECDSA dan data sensitif ke file lokal terproteksi, serta memastikan `.gitignore` memblokir semua file rahasia agar tidak bocor ke repositori GitHub.

---

## 1. Analisis Masalah Saat Ini

1. **Menu Tampil 2 Kali (Self-Update Terlambat)**:
   - Saat ini, pengecekan pembaruan `update.sh` baru berjalan pada **Langkah 2.6** (setelah pengguna memilih Menu 1 dan mengekstrak ZIP).
   - Ketika file baru ditimpa dan perintah `exec bash "$0" "$@"` dijalankan, argumen menu tidak ada, sehingga Termux mengeksekusi ulang dari baris pertama dan menampilkan menu 1–8 untuk kedua kalinya.
2. **Monolitik & Resiko Kebocoran Rahasia ke GitHub**:
   - Skrip `update.sh` saat ini berukuran lebih dari 3.200 baris dalam 1 file tunggal.
   - Di dalam baris 36–40 dan baris 570–574, terdapat **Private Key ECDSA** lisensi dalam bentuk teks terbuka (`DEFAULT_PRIVATE_KEY_PEM`).
   - File `update.sh` di-track dan di-push ke GitHub oleh `git add -A`. Siapa pun yang memiliki akses ke repositori dapat melihat kunci privat tersebut dan membuat lisensi palsu tanpa batas.
   - File `cuancame_rentals.json` (riwayat transaksi dan ID perangkat penyewa) saat ini belum dimasukkan ke `.gitignore`.

---

## 2. Arsitektur Solusi Baru

### A. Pre-Execution Self-Update (Di Baris Pertama `update.sh`)
- Saat `bash update.sh` dijalankan, sebelum menampilkan menu teks:
  1. Skrip memeriksa folder download HP (`/sdcard/Download`, `/storage/emulated/0/Download`, `~/downloads`).
  2. Mengidentifikasi ZIP proyek terbaru (berdasarkan timestamp file).
  3. Mengintip isi ZIP dengan `unzip -p "$ZIP" update.sh` atau ekstrak modul sementara.
  4. Jika terdapat perbedaan antara `update.sh` di ZIP dengan file lokal:
     - Salin `update.sh` dan seluruh modul baru.
     - Tampilkan notifikasi singkat: `[ 🔄 ] Pembaruan update.sh terdeteksi! Memuat versi terbaru...`
     - Jalankan `exec bash "$0" --from-self-update "$@"`
  5. Menu utama langsung tampil dalam versi terbaru tanpa loop atau mental.

### B. Modularisasi Struktur Skrip (`tools/`)
Memecah file monolitik 3.200 baris menjadi modul-modul independen yang bersih:

| Modul | Lokasi File | Fungsi Utama |
|---|---|---|
| **Entrypoint / Gateway** | `update.sh` | Orchestrator ramping: self-update di awal, menu utama 1–8, pipeline sync proyek, bump version & git push. |
| **Pusat Lisensi & Keygen** | `tools/license_manager.py` | Generator serial, cek validitas, perpanjang hari sewa, format pesan WA. |
| **Kunci Privat Terproteksi** | `tools/cuancame_private_key.pem` & `~/.cuancame/private_key.pem` | File fisik kunci ECDSA (chmod 600), dibaca oleh python tanpa ada hardcode di skrip bash. |
| **Remote Kill-Switch** | `tools/remote_kill.sh` | Pengelola `blacklist.txt`, sinkronisasi noticeboard ke GitHub. |
| **Bot Telegram Admin** | `tools/telegram_bot.py` | Bot Telegram tombol interaktif admin. |
| **Auto-Download 5-Tier** | `tools/apk_downloader.py` | Mesin pelacak Actions run & pengunduh APK (Asset API, Curl, GH CLI, Browser URL) anti-404. |
| **Kelola Release GitHub** | `tools/release_manager.py` | Daftar rilis, hapus satu, atau hapus semua rilis di GitHub. |
| **Local APK Builder** | `tools/local_builder.sh` | Kompilasi APK di Termux secara offline/lokal. |

### C. Pengamanan Git Anti-Bocor (.gitignore & Clean Push)
- Direktori `tools/` didaftarkan di `.gitignore` untuk file-file sensitif, atau file sensitif dipisahkan secara ketat:
  - `tools/*.pem`
  - `*private_key*`
  - `cuancame_rentals.json`
  - `*.cfg`
  - `*.conf`
  - `.github_token`
- Dalam `update.sh`, kunci privat **tidak pernah di-hardcode** dalam bentuk string teks.
- File kunci privat disimpan di `$HOME/.cuancame/private_key.pem` pada perangkat lokal Termux pemilik aplikasi, sehingga aman 100% dan tidak akan pernah terdorong ke GitHub publik maupun privat.

---

## 3. Rencana Langkah Pengerjaan

1. **Pembuatan Direktori & Modul `tools/`**:
   - `tools/license_manager.py`: Modul mandiri untuk manajemen sewa & lisensi ECDSA.
   - `tools/apk_downloader.py`: Modul mandiri untuk download APK dengan 5-tier multi-strategy anti-404.
   - `tools/telegram_bot.py`: Modul bot Telegram interaktif.
   - `tools/release_manager.py`: Modul manajemen GitHub releases.
   - `tools/remote_kill.sh`: Modul manajemen blacklist perangkat.
   - `tools/local_builder.sh`: Modul kompilasi lokal HP.
2. **Pembersihan `update.sh` Utama**:
   - Menjadikan `update.sh` sebagai master orchestrator yang bersih (kurang dari 400 baris).
   - Menambahkan mekanisme **Pre-Check Self-Update di baris awal**.
   - Menghubungkan setiap opsi menu 1–8 ke modul terkait di `tools/`.
   - Menghilangkan seluruh hardcode kunci privat ECDSA dari `update.sh`.
3. **Pembaruan Aturan `.gitignore`**:
   - Memastikan `cuancame_rentals.json`, `tools/*.pem`, `*.pem`, `*.conf`, `*.cfg`, dan `*private_key*` tidak dapat di-commit ke Git.
4. **Verifikasi Sintaks & Eksekusi**:
   - Menguji sintaks bash dengan `bash -n update.sh` dan seluruh sub-skrip.
   - Menguji kompilasi seluruh file python modul di `tools/`.
   - Menjalankan `compile_applet` untuk memastikan build aplikasi Android tetap sukses.

---

## 4. Konfirmasi & Tindak Lanjut

Setelah rencana ini disetujui, perubahan akan langsung diimplementasikan ke file `update.sh`, modul-modul `tools/`, dan konfigurasi `.gitignore`.
