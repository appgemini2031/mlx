# Rencana Perbaikan Pembacaan & Pemrosesan Event Siaran

Pengguna melaporkan adanya kejanggalan dalam pembacaan semua event siaran (Chat, Gift, Like, Join, Follow, Share). Berdasarkan audit menyeluruh terhadap alur WebSocket broker, parser JSON, antrean dispatcher, dan generator visual overlay, ditemukan beberapa faktor utama kejanggalan:
1. **Parsing Kunci JSON yang Kaku**: Nilai teks chat hanya mengambil `"text"`, sehingga jika broker mengirim `"comment"` atau `"content"` teks menjadi kosong. Nama pengirim jika kosong langsung fallback ke "Penonton" tanpa memeriksa `"nickname"` atau `"uniqueId"`. Untuk gift, jika jumlah koin dikirim dalam `"diamonds"` atau `"diamond_count"`, pembacaan koin gagal sinkron.
2. **Ketiadaan Respon Pong pada WebSocket**: Frame Ping (opcode 9) diabaikan tanpa mengirim frame Pong (opcode 10), menyebabkan server/broker memutuskan koneksi secara periodik setelah 30-60 detik.
3. **Format Kalimat yang Terlalu Panjang & Bertele-tele**: Teks event memuat kata-kata redundant ("Mengirim ...", "Mengetuk Like ...", "Ahlan wa Sahlan ...", "💎 Koin") yang bertentangan dengan prinsip ringkas-padat, sehingga terpotong (*ellipsized*) saat ditampilkan di deck kartu atau banner.
4. **Salah Tipe Visual untuk Event Interaksi**: Event Join (Member), Follow, dan Share saat ini dikirim dengan `bannerType = "EVENT_LIKE"`, sehingga semuanya ditampilkan dengan ikon hati merah (`❤️`) dan latar belakang merah crimson, bukan warna dan ikon yang sesuai (Join: 👋 biru/teal, Follow: 👤 ungu, Share: 🔄 hijau/cyan).
5. **Ketidaksinkronan Doa & Kontributor Huruf**: Di kartu Khatam Al-Qur'an, pembatasan 3.2 detik untuk pergantian doa menyebabkan nama kontributor di footer berganti tetapi kotak doa di atasnya masih menampilkan doa orang sebelumnya.

---

## Rincian Solusi & Perubahan

### 1. Robust JSON Event Parsing & WebSocket Pong (`LiveEventReceiver.kt`)
- **Penanganan Pong (RFC 6455)**: Balas frame Ping (opcode 9) dengan frame Pong (opcode 10) secara otomatis agar koneksi broker tidak putus-nyambung.
- **Fleksibilitas Kunci JSON**:
  - `chat`: Baca dari `"text"`, `"comment"`, atau `"content"`.
  - `senderName`: Baca dari `"senderName"`, `"nickname"`, atau `"uniqueId"`. Bersihkan spasi.
  - `gift`: Baca `"giftName"` / `"name"`, `"giftCount"` / `"count"` / `"repeatCount"`, dan `"diamondCount"` / `"diamonds"`.
  - `like`: Baca `"likeCount"` / `"count"` dan `"totalLikes"` / `"total"`.
- **Format Teks Ringkas Padat**:
  - Chat: Teks doa murni tanpa imbuhan berulang.
  - Gift: `$gName x$gCount • $totalCoinsInEvent 💎` (tanpa kata "Mengirim").
  - Like: `+$lCount Like` (tanpa kata "Mengetuk Like").
  - Join: `Masuk Siaran` (tanpa kalimat panjang).
  - Follow: `Mengikuti Siaran`.
  - Share: `Membagikan Siaran`.

### 2. Diferensiasi Tipe & Gaya Visual Banner Event (`OverlayBannerBitmapGenerator.kt` & `LiveEventReceiver.kt`)
- Pada `generateEventLikeBitmap` / banner interaksi:
  - **Join (Member)**: Ikon `👋 `, latar belakang biru toska (`#E60284C7`), border `#38BDF8`.
  - **Follow**: Ikon `👤 `, latar belakang ungu lembut (`#E67C3AED`), border `#C084FC`.
  - **Share**: Ikon `🔄 `, latar belakang hijau zamrud (`#E60D9488`), border `#2DD4BF`.
  - **Like**: Ikon `❤️ `, latar belakang merah crimson (`#E6BE123C`), border `#FDA4AF`.
- Pada `generateEventGiftBitmap`:
  - Ikon `🎁 `, latar emas amber (`#E6D97706`), border emas `#FDE047`. Menampilkan nama dan rincian hadiah tanpa kata "Mengirim".

### 3. Sinkronisasi Doa & Kontributor di Kartu Khatam (`InteractiveQuranKhatamEngine.kt` & `OverlayBannerBitmapGenerator.kt`)
- Sinkronkan `lastPrayerSender` dan `lastPrayerText` langsung saat komentar diproses untuk penambahan huruf, sehingga nama kontributor di footer kartu selalu seirama dengan teks doa yang sedang tampil di deck kiri.
- Sederhanakan teks baris event di deck kanan: `🎁 $sdr: $gName x$gCount • $dCount 💎` dan `❤️ $sdr: +$lCount Like` agar pas sempurna di lebar kolom tanpa terpotong `...`.

### 4. Pembersihan Redundansi Ikon & Teks di Analitik Dashboard (`OverlayBannerBitmapGenerator.kt`)
- Terapkan aturan ketat: jika ada ikon jangan menulis nama bendanya lagi.
  - Baris 1: `❤️ $likes • 💬 $chats` (tanpa kata "Suka" dan "Doa").
  - Baris 2: `🎁 $gifts • 💎 $diamonds` (tanpa kata "Hadiah" dan "Koin").
  - Baris 3: `👋 $joins • 👤 $follows • 🔄 $shares` (tanpa kata "Join", "Follow", "Share").
  - Progres: `🔤 $remLetters Huruf`, `📜 $remAyahs Ayat • 🕌 $remSurahs Surat`, `✨ $remJuz Juz`.

---

## Rencana Verifikasi
1. **Unit Test Local JVM**: Jalankan `gradle :app:testDebugUnitTest` untuk memverifikasi logika parsing dan pemrosesan event.
2. **Kompilasi & Build Check**: Jalankan `compile_applet` untuk memastikan seluruh perubahan terkompilasi bersih tanpa peringatan atau kesalahan sintaks.
