# Rencana Implementasi: Solusi Komprehensif 4 Celah Teknis CuanCame

Mengatasi 4 celah teknis yang telah diidentifikasi pada sistem Live Streaming TikTok: Ketergantungan LSPosed, Keterbatasan Codec & RAM 2600MB, Kerapuhan Selektor Komentar, dan Deteksi Algoritma Server TikTok (*Server-side Duplicate Content / Video Hash*).

---

## 1. Arsitektur & Strategi Solusi

```
┌────────────────────────────────────────────────────────────────────────┐
│                   CUANCAME ADVANCED SHIELD SUITE                       │
├─────────────────────────┬──────────────────────┬───────────────────────┤
│ 1. ANTI-HASH ENGINE     │ 2. CODEC GUARD       │ 3. MULTI-HEURISTIC    │
│    (OpenGL ES Shader)   │    (RAM & Bitrate)   │    COMMENT SCANNER    │
├─────────────────────────┼──────────────────────┼───────────────────────┤
│ • Dynamic Micro-Noise   │ • Pre-flight Inspect │ • Multi-pattern ID    │
│ • Random Color Shift    │ • Safe Mode 1080p30  │ • Bounding Box Match  │
│ • Micro-Zoom Breathing  │ • RAM Low Watchdog   │ • Sheet Exclusion     │
├─────────────────────────┴──────────────────────┴───────────────────────┤
│ 4. LSPOSED HEALTH & CLOAKING GUARD                                     │
│ • Scope & Hook Status Validator • Anti-Tamper Reflection Cloaking     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Rincian Modul Solusi

### Solusi 1: Anti-Deteksi Server TikTok (Dynamic Anti-Hash Visual Engine)
*Masalah:* Server TikTok memindai video hash, keyframe fingerprint, dan perulangan visual untuk mendeteksi *unoriginal content* / rekaman.
* **Modifikasi GLSL Shader di `GlShaderProgram.kt`**:
  * Menambahkan uniform waktu dan intensitas acak ke fragment shader OpenGL.
  * **Dynamic Micro-Noise (Film Grain)**: Menghasilkan noise acak berintensitas sangat rendah (0.5% - 1.5%) yang berganti setiap frame. Tidak terlihat oleh mata penonton, tetapi merusak kecocokan hash MD5/SHA dan video fingerprint server TikTok di setiap frame.
  * **Dynamic Micro Color Shift**: Modulasi halus temperatur warna (< 1% fluktuasi pada channel merah/biru secara periodik) sehingga histogram warna terus bergerak dinamis.
  * **Micro-Zoom Breathing**: Variasi rasio skala 1.000x ke 1.012x perlahan setiap 20-30 detik sehingga koordinat piksel tepi dan konten tidak statis.
* **Pengaturan UI**:
  * Kontrol intensitas Anti-Hash di Floating Window dan menu Camera Settings (Pilihan: Nonaktif, Ringan, Seimbang, Agresif).

### Solusi 2: Codec Guard & Pencegah Hang RAM 2600MB
*Masalah:* Video 4K, 60fps, bitrate > 20Mbps, atau codec HEVC berat menyebabkan decoder hardware kehabisan buffer dan memicu crash di HP RAM 2.6GB.
* **Komponen `VideoCodecGuard.kt`**:
  * Melakukan inspeksi pre-flight saat video dipilih via `MediaMetadataRetriever`:
    * Ekstraksi resolusi, framerate, bitrate, dan mimeType.
    * Status indikator: "Aman (1080p 30fps)", "Peringatan (60fps)", "Bahaya Crash (4K / Bitrate Tinggi)".
  * **Mode Safe-Playback Otomatis**:
    * Jika video terdeteksi berisiko tinggi dan RAM tersisa < 450MB, aplikasi secara otomatis mengaktifkan frame limiter 30fps dan membatasi alokasi buffer rendering OpenGL.
  * **Emergency Memory Trimmer**:
    * Integrasi ke `DeviceOptimizationManager` untuk otomatis membebaskan memori bitmap stiker & overlay lama saat terdeteksi lonjakan konsumsi memori.

### Solusi 3: Pemindai Komentar Kebal Update Versi TikTok (`LiveCommentParser.kt`)
*Masalah:* Update TikTok sering mengubah `viewId` chat list, mematahkan pembacaan komentar otomatis.
* **Penyempurnaan Algoritma Scanning**:
  * **Fallback Multi-Heuristic**:
    * *Heuristic 1 (Resource ID list)*: Memeriksa daftar nama ID populer TikTok versi terbaru maupun versi lama.
    * *Heuristic 2 (Struktur Pasangan Teks)*: Jika ID tidak cocok, periksa elemen kontainer di area chat yang memiliki pasangan 2 TextView (`[Nama]` dan `[Pesan]`).
    * *Heuristic 3 (Regex Delimiter)*: Mendeteksi pola titik dua atau baris ganda dari string node gabungan.
  * **Penyempurnaan Filter Anti-Sheet**:
    * Memastikan menu penutup (share sheet, gift panel, comment input keyboard dialog) tetap diabaikan secara ketat agar tidak salah dibaca sebagai nama/komentar penonton.

### Solusi 4: LSPosed Health Guard & Cloaking
*Masalah:* Pengguna sering bingung jika hook tidak berjalan karena scope LSPosed terlepas atau terganggu deteksi root/anti-hook.
* **Komponen `LsposedHealthGuard.kt`**:
  * Menambahkan indikator status kesehatan Hook interaktif di Dashboard utama (Status: Aktif, Scope TikTok Terhubung, atau Perlu Tindakan).
  * Menampilkan tombol pemandu 1-klik untuk membuka pengaturan LSPosed jika modul terdeteksi nonaktif atau target TikTok belum dicentang.
  * **Hook Cloaking**: Masking stack trace pada hook broadcast dan method interceptor agar nama package CuanCame tidak muncul dalam pencarian classloader refleksi aplikasi target.

---

## 3. Tahapan Rencana Perubahan Kode

1. **`GlShaderProgram.kt` & `GlVideoRenderer.kt`**:
   * Implementasikan uniform `uAntiHashIntensity`, `uTimeSeed`, dan fungsi micro-noise + subtle color shift pada fragment shader.
   * Hubungkan kontrol anti-hash ke pengaturan live (`MainHook.kt`).
2. **`VideoCodecGuard.kt`**:
   * Buat kelas validator file video untuk mengecek resolusi, framerate, bitrate, dan memberikan rekomendasi keselamatan memori.
3. **`LiveCommentParser.kt`**:
   * Tingkatkan parser komentar dengan multi-heuristic selector dan fallback struktur node tanpa bergantung pada ID tunggal.
4. **`LsposedHealthGuard.kt` & UI Integration**:
   * Tambahkan visualisasi diagnostik LSPosed di Home Screen dan tab Pengaturan CuanCame.
   * Tambahkan tombol toggle Anti-Hash Shield di Floating Window (`FloatingBubbleWindow.kt` & `SpillTabBuilder.kt`).
5. **Verifikasi & Pengujian**:
   * Menjalankan unit test untuk memastikan GL shader compiling tanpa error, validasi codec berjalan akurat, dan unit test untuk parser komentar multi-heuristic.
   * Kompilasi aplikasi dengan `compile_applet`.
