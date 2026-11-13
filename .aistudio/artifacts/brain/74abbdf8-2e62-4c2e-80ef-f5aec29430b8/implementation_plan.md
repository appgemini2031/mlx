# Implementasi Selesai: Game Interaktif Sederhana "Tarik Tambang / Duel Kubu" (Live TikTok)

Sistem Game Interaktif Sederhana berbasis **Duel Tarik Tambang (Tug-of-War)** telah berhasil diimplementasikan secara menyeluruh ke dalam aplikasi. Sistem ini menggantikan konsep Mobile Legends yang rumit dan menonaktifkan fitur Al-Qur'an Khatam sementara agar siaran live TikTok lebih ramai, seru, dan memicu saweran gift secara maksimal.

---

## Ringkasan Fitur & Cara Kerja

### 1. Bagaimana Penonton Gratisan (Non-Gift) Ikut Bermain?
- **Tap Like**: Setiap ketukan Like dari penonton menyumbang tarikan **+1 poin** ke tim yang didukungnya. Jika penonton belum memilih tim, Like otomatis diarahkan ke tim yang sedang tertinggal (*Comeback Mechanic*) sehingga pertarungan selalu sengit dan menegangkan.
- **Chat Komentar**: Penonton cukup mengetik angka atau nama kubu di kolom komentar TikTok:
  - Ketik **"1"**, **"kiri"**, atau nama Tim A (misal: **"COWOK"**) = Menarik **+10 poin** ke kubu kiri.
  - Ketik **"2"**, **"kanan"**, atau nama Tim B (misal: **"CEWEK"**) = Menarik **+10 poin** ke kubu kanan.

### 2. Bagaimana Memicu Penonton Sultan Sawer Gift?
- **Gift Saweran (Pemicu Comeback Raksasa)**:
  - Setiap 1 koin gift TikTok dikonversi menjadi **+10 poin tarikan instan**.
  - Contoh: Gift Mawar (1 koin) = +10 poin, Gift Donat (30 koin) = +300 poin, Gift Paus (2150 koin) = +21.500 poin.
  - Ketika tim hampir kalah, penonton yang tidak ingin timnya dipermalukan akan terpancing melempar gift untuk membalikkan posisi tali secara instan.

### 3. Tampilan Visual di Kamera Virtual (Live Stream Overlay)
- **Desain Modern Obsidian & Dual Gradient**:
  - Judul: `⚔️ TARIK TAMBANG • RONDE [X] • SKOR: [A] - [B]` (Warna Emas).
  - Label Tim & Persentase: `◄ COWOK (58%)` (Biru/Cyan) vs `(42%) CEWEK ►` (Merah/Pink).
  - Jalur Tali Real-Time dengan Bandana Merah yang bergeser ke kiri dan kanan sesuai selisih poin.
  - Ticker Aksi Live: Memberitahukan siapa yang baru saja menarik tali beserta jenis aksinya (Like/Chat/Gift).
  - Banner Kemenangan Emas: Muncul otomatis saat salah satu kubu mencapai garis finish (default 500 poin), diiringi penambahan skor ronde.

### 4. Kontrol di Floating Control (Tab Notif -> Sub-Mode "Tambang")
- **Saklar Utama**: Tombol `GAME: [OFF] / [AKTIF]` (Otomatis mematikan Khatam Qur'an saat dinyalakan).
- **Papan Skor & Kontrol Ronde**: Tombol `↺ Reset Tali`, `🏆 Ronde Baru`, dan `0️⃣ Reset Skor`.
- **Pilihan Tema Cepat**:
  - `👦 Cowok vs 👧 Cewek`
  - `🔵 Tim Biru vs 🔴 Tim Merah`
  - `💔 Jomblo vs 💍 Pacaran`
  - `⬅️ Kiri vs ➡️ Kanan`
  - `✏️ Kustom Nama Tim` (Dialog input untuk nama bebas).
- **Simulator Uji Coba (Bisa Dicoba Tanpa Menunggu Live Asli)**:
  - `💬 Chat Tim A (+10)` & `💬 Chat Tim B (+10)`
  - `🎁 Sawer Donat A (+300)` & `🎁 Sawer Donat B (+300)`
- **Penyesuaian Posisi & Ukuran**:
  - Stepper Posisi Y (Atas ⬆️ / Bawah ⬇️)
  - Stepper Ukuran Skala (Zoom ➖ / ➕)

---

## Status Verifikasi Teknis
- **Kompilasi Gradle**: `BUILD SUCCESSFUL` (0 error).
- **Unit Testing**: Seluruh 25 unit test termasuk `TugOfWarInteractiveGameTest` berhasil lolos 100%.
