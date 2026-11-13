# Fitur Auto-Download APK GitHub ke HP (Revisi: Anti-Download APK Lama / Anti-Buta Versi)

Solusi terverifikasi dan presisi tinggi untuk memantau proses kompilasi GitHub Actions secara realtime, memastikan build kompilasi commit terbaru benar-benar selesai dan sukses, lalu mengunduh berkas APK yang tepat sesuai versi commit ke folder `/sdcard/Download` HP tanpa risiko mengunduh APK lawas.

---

## Analisis Masalah Lama (Mengapa Dulu Mengunduh APK Sebelumnya?)

Berdasarkan keluhan yang Anda alami: *"build belum selesai malah download apk sebelumnya, kan info apk sudah tertera mengapa buta?"*

### Penyebab Masalah di Masa Lalu:
1. **Pola "Blind Query" (`releases/latest`)**:
   Skrip lama biasanya langsung menembak endpoint GitHub API `GET /releases/latest`. Karena proses kompilasi di GitHub Actions memerlukan waktu **2 hingga 5 menit**, rilis terbaru pada detik ke-0 sampai menit ke-3 **masih merupakan rilis lama** dari commit kemarin! Skrip yang "buta" langsung menganggap rilis lama tersebut sebagai rilis baru dan mengunduhnya seketika.
2. **Tidak Mengunci ke Git Commit SHA (`HEAD`)**:
   Skrip tidak memeriksa apakah rilis atau workflow yang dipantau berasal dari commit SHA yang baru saja di-push.
3. **Mengabaikan Status Realtime Workflow Run (`status` & `conclusion`)**:
   Skrip tidak menunggu sampai status GitHub Actions bernilai `status: completed` dan `conclusion: success`. Jika build sedang berjalan (`in_progress`) atau bahkan gagal (`failure`), skrip lama malah mengambil APK apa pun yang tersedia sebelumnya.
4. **Tidak Memvalidasi Nomor Versi APK Target**:
   Skrip tidak mencocokkan nama berkas APK atau Release Tag dengan nomor versi yang baru saja di-generate di `app/version.properties` (misalnya target `v0.0.190`, tapi yang terunduh `v0.0.189`).

---

## Arsitektur Solusi Baru: "Smart Lock & Verification Engine"

Sistem baru dirancang dengan **4 Lapisan Verifikasi Anti-Salah** agar tidak pernah lagi terjadi salah unduh APK lawas:

```
┌────────────────────────────────────────────────────────────────────────┐
│ 1. KUNCI COMMIT & VERSI (DI LOKAL SEBELUM PUSH)                       │
│    - TARGET_COMMIT_SHA = $(git rev-parse HEAD)                         │
│    - TARGET_VERSION    = "v${V_MAJOR}.${V_MINOR}.${V_PATCH}"           │
│    - TARGET_FILE_NAME  = "v${V_MAJOR}.${V_MINOR}.${V_PATCH}.apk"       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Push ke GitHub
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 2. TRACKING RUN GITHUB ACTIONS BERBASIS COMMIT SHA                     │
│    - Query: GET /repos/{owner}/{repo}/actions/runs?head_sha={SHA}      │
│    - Mengunci HANYA workflow run yang dipicu oleh commit ini!          │
│    - Tampilan Live Status & Timer:                                     │
│      [⏳ Status: in_progress] Kompilasi APK sedang berjalan (1m 15s)...│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Tunggu hingga: status == 'completed'
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 3. EVALUASI HASIL KOMPILASI (ANTI-GAGAL)                               │
│    - Jika conclusion == 'failure':                                     │
│      ⛔ STOP! Tampilkan pesan: "Build di GitHub Gagal! APK tidak akan  │
│         diunduh." Berikan link log detail. TIDAK AKAN ambil APK lama!  │
│    - Jika conclusion == 'success':                                     │
│      ✅ Lanjut ke pengambilan rilis resmi yang berpasangan.            │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 4. VALIDASI TAG RILIS & NAMA ASSET APK                                 │
│    - Cari Release yang memiliki tag: {TARGET_VERSION}-b*               │
│    - Pastikan berkas asset adalah: {TARGET_FILE_NAME}                  │
│    - Unduh langsung ke: /sdcard/Download/CuanCame_{TARGET_VERSION}.apk │
│    - Backup lokal di: ./download/CuanCame_{TARGET_VERSION}.apk         │
│    - Validasi ukuran berkas (> 15 MB) & integritas berkas APK          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## User Review & Critical Decisions

- **Keputusan 1**: Proses download otomatis setelah `update.sh` (Menu 1) **WAJIB** mengunci target ke `TARGET_COMMIT_SHA` dan `TARGET_VERSION`. Skrip **DILARANG KERAS** menyentuh rilis lain selain yang dihasilkan oleh commit tersebut.
- **Keputusan 2**: Jika pengguna memilih Menu Mandiri (Menu 8: Download APK), skrip akan menampilkan **Daftar Versi Rilis yang Jelas** (Versi, Tag, Tanggal, dan Status) sehingga pengguna dapat melihat dengan transparan versi mana yang akan diunduh, tanpa ada tebak-tebakan.
- **Keputusan 3**: Lokasi penyimpanan utama tetap `/sdcard/Download` agar langsung muncul di folder Download HP pengguna, dengan salinan cadangan di folder `./download/` di Termux.

---

## Alur Interaksi Pengguna (Terminal Output Mockup)

### Skenario 1: Pasca Push Update (Menu 1)
```text
🚀 Mengirim ke GitHub (origin main)...
  [ ✅ ] Push berhasil ke GitHub!
  🎯 Target Versi: v0.0.190 (Commit: 134c91a)

👉 Ingin pantau build & download APK otomatis ke HP saat selesai? [Y/n]: y
=======================================================
  📡 MEMANTAU GITHUB ACTIONS (COMMIT: 134c91a)
=======================================================
  [ ⏳ 00:15 ] Status: queued - Menunggu antrean runner GitHub...
  [ ⏳ 00:45 ] Status: in_progress - Mengompilasi APK (Gradle assembleDebug)...
  [ ⏳ 01:50 ] Status: in_progress - Mengompilasi APK (Gradle assembleDebug)...
  [ 🎉 02:40 ] Status: completed (SUCCESS) - Build APK selesai!

📥 Mengambil berkas rilis v0.0.190.apk...
  [========================================] 100% (28.4 MB)
  [ ✅ ] APK BERHASIL DIUNDUH & DIVERIFIKASI!
  📂 Lokasi HP    : /sdcard/Download/CuanCame-v0.0.190.apk
  💾 Lokasi Termux: ~/cuancame/download/CuanCame-v0.0.190.apk
  📱 Buka File Manager HP untuk langsung meng-install APK!
=======================================================
```

### Skenario 2: Jika Build Gagal di GitHub
```text
  [ ❌ 01:20 ] Status: completed (FAILURE) - Kompilasi di GitHub Gagal!
  ⛔ Peringatan: Build gagal dikompilasi oleh GitHub Actions.
  ⚠️  Skrip TIDAK AKAN mengunduh APK lawas.
  🔗 Cek log error di: https://github.com/adiabdltf1/SantrehCuan/actions/runs/12345678
```

---

## Rincian Perubahan Teknis pada `update.sh`

1. **Ekstraksi Target Identitas**:
   - Menangkap commit hash tepat sebelum push: `TARGET_SHA=$(git rev-parse HEAD)`.
   - Mengambil versi target: `TARGET_VER="v${V_MAJOR}.${V_MINOR}.${V_PATCH}"`.
2. **Mesin Python Tracker & Downloader (`watch_and_download_apk`)**:
   - Menggunakan modul bawaan Python (`urllib.request`, `json`, `time`, `os`, `sys`).
   - Query endpoint:
     `https://api.github.com/repos/{owner}/{repo}/actions/runs?head_sha={TARGET_SHA}`
   - Loop polling interval 10 detik, maksimal timeout 10 menit.
   - Mengidentifikasi rilis dengan tag matching `TARGET_VER` atau tag yang diproduksi oleh run tersebut (`v${APP_VER}-b${RUN_NUMBER}`).
   - Unduh asset dengan buffer stream 64KB dan progress bar dinamis.
3. **Penyimpanan Ganda & Izin Android**:
   - Cek write access ke `/sdcard/Download`.
   - Simpan berkas dengan nama bersih: `/sdcard/Download/CuanCame-${TARGET_VER}.apk`.
   - Salin juga ke `./download/CuanCame-${TARGET_VER}.apk`.
4. **Menu Utama 8: Unduh APK Hasil Build**:
   - Menampilkan tabel rilis GitHub terbaru (Tag, Waktu Rilis, Ukuran APK).
   - Pengguna bisa memilih rilis mana yang ingin diunduh atau memilih versi terbaru yang sudah terverifikasi.

---

## Rencana Verifikasi

1. **Uji Validasi Logika Verifikasi**: Memastikan filter `head_sha` dan pencocokan nama tag/file bekerja sempurna dan menolak release lama.
2. **Uji Penanganan Error / Timeout**: Memastikan jika build gagal atau dibatalkan, skrip berhenti secara aman dan memberikan laporan tanpa mendownload apa pun.
3. **Uji Bash Syntax & Lint**: Menjalankan pengecekan bash syntax pada `update.sh`.
4. **Uji Build Android**: Menjalankan `compile_applet` untuk memastikan seluruh proyek tetap bersih dan valid.
