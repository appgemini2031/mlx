# Rencana Implementasi: Modul Audio Anti-Deteksi & Pemecah Sidik Jari Akustik (Acoustic Fingerprint Disruptor)

Sistem anti-deteksi video (micro-noise, color shift, micro-zoom pada OpenGL fragment shader) telah sukses mengelabui deteksi server visual TikTok. Namun, sistem deteksi server (seperti TikTok Content ID, AcoustID, dan algoritma *spectrogram constellation map*) juga memindai sidik jari akustik (audio fingerprint) dari video siaran langsung dan musik MP3.

Rencana ini merancang solusi komprehensif **Audio Anti-Deteksi Real-Time** yang bekerja pada tingkat pemutar media kamera virtual (`GlVideoRenderer`) dan pemutar musik mandiri (`StandaloneMusicPlayer`).

---

## 1. Analisis Celah & Prinsip Kerja Anti-Deteksi Audio

### Bagaimana Server TikTok/Platform Mendeteksi Audio:
1. **Spectrogram Constellation Map**: Server mengekstraksi titik-titik puncak frekuensi (peaks) pada rentang waktu tertentu dan membuat hash pasangan frekuensi-waktu (*landmarks*).
2. **Pitch & Harmonic Fingerprinting**: Nada dasar dan harmonik vokal/musik dihitung nilainya. Jika sama persis dengan audio berhak cipta atau rekaman live sebelumnya, server langsung menandai live sebagai "Konten Rekaman / Tidak Asli".
3. **Time-Domain Correlation**: Server mencocokkan tempo ketukan lagu (BPM) dan panjang jeda waktu.

### Solusi Teknis Pemecah Sidik Jari (Acoustic Disruptor):
* **Micro-Pitch Shifting (Pergeseran Nada Mikro)**: Menggeser nada audio sebesar ±1.01x hingga 1.03x (15 hingga 35 cents). Pergeseran ini memindahkan seluruh *bin FFT* frekuensi pada spektrogram sehingga hash landmark berubah 100%, namun tetap terdengar alami bagi telinga manusia tanpa suara vokal menjadi aneh (*chipmunk effect* dicegah).
* **Dynamic Tempo Drift (Variasi Tempo Dinamis)**: Memvariasikan kecepatan audio secara mikro (±0.5% hingga 1.0%) secara berkala perlahan (gelombang sinusoidal halus). Ini memecah korelasi domain-waktu sehingga algoritma pencocokan temporal server gagal mengenali urutan sampel.
* **Tiga Tingkat Intensitas Preset**:
  * **Rendah (Low)**: Pitch +1.01x, Tempo stabil (+0.5%), cocok untuk vokal santai.
  * **Sedang (Medium - Rekomendasi)**: Pitch +1.02x, Dynamic Tempo Drift (0.995x - 1.012x), perlindungan optimal untuk musik dan suara host live.
  * **Agresif (Aggressive)**: Pitch +1.03x, Tempo Drift lebih aktif (±1.5%), perlindungan maksimal terhadap audio viral bertarget ketat.

---

## 2. Rincian Perubahan Modul & Arsitektur

### A. Model Data Konfigurasi (`VcamModels.kt`)
* Tambahkan enum `AudioAntiHashIntensity`:
  ```kotlin
  enum class AudioAntiHashIntensity(val displayName: String, val pitchRatio: Float, val tempoDriftPercent: Float) {
      LOW("Rendah (±1%)", 1.010f, 0.005f),
      MEDIUM("Sedang (±2%)", 1.020f, 0.010f),
      AGGRESSIVE("Agresif (±3%)", 1.030f, 0.015f)
  }
  ```
* Tambahkan field pada `VcamConfig`:
  * `isAudioAntiHashEnabled: Boolean = true` (default aktif melindungi audio)
  * `audioAntiHashMode: AudioAntiHashIntensity = AudioAntiHashIntensity.MEDIUM`

### B. Mesin Pemutar Video Hook (`GlVideoRenderer.kt`)
* Terapkan `PlaybackParams` dinamis pada `MediaPlayer` video audio:
  * Gunakan API Android `player.playbackParams = PlaybackParams().apply { pitch = targetPitch; speed = targetSpeed }`.
  * Tambahkan siklus *heartbeat tempo drift* lembut (berubah halus setiap beberapa detik) untuk menggagalkan deteksi kontinu server TikTok.
  * Mendukung pembaharuan live saat pengguna mengubah mode anti-deteksi tanpa menghentikan pemutaran video.

### C. Pemutar Musik Mandiri (`StandaloneMusicPlayer.kt`)
* Integrasikan `PlaybackParams` serupa ke dalam `StandaloneMusicPlayer` untuk file MP3 latar belakang.
* Pastikan musik latar memiliki variasi harmonik mikro yang selaras sehingga tidak memicu pelanggaran hak cipta musik TikTok.

### D. Repository & Broadcast Sinkronisasi (`VcamRepository.kt` & `PerAppConfigRepository.kt`)
* Tambahkan fungsi kontrol repository:
  * `toggleAudioAntiHash(enabled: Boolean?): Boolean`
  * `setAudioAntiHashMode(mode: AudioAntiHashIntensity)`
* Siarkan broadcast ke hook target `com.vcam.action.SET_AUDIO_ANTI_HASH` dan simpan konfigurasi persisten ke penyimpanan lokal.

### E. Antarmuka Pengguna (UI Controls)
1. **Menu Utama (`MediaManagerScreen.kt`)**:
   * Pada **Tab Musik (Audio MP3)** dan **Pengaturan Sistem**: Tambahkan kartu kontrol **🛡️ Audio Anti-Deteksi**.
   * Switch toggle ON/OFF dengan indikator status hijau/merah.
   * Chip pilihan intensitas: **Rendah (1%)**, **Sedang (2%)**, **Agresif (3%)**.
2. **Floating Window (`FloatingControlService.kt` / `SpillTabBuilder.kt`)**:
   * Pada **Tab 2 (Musik)** dan **Tab 3 (Visual/Settings)**: Tambahkan tombol aksi cepat:
     `🛡️ Audio Anti-Detek: ON (Sedang ▾)`.
   * Memungkinkan pengguna menaikkan atau menurunkan intensitas saat live berlangsung tanpa meninggalkan aplikasi target.

---

## 3. Rencana Verifikasi & Pengujian

1. **Uji Unit Test (`app/src/test/java/com/vcam/`)**:
   * Menulis `AudioAntiHashTest.kt` untuk memverifikasi kalkulasi pitch ratio, tempo drift, serta transisi konfigurasi repository.
   * Memastikan nilai pitch dan speed selalu berada dalam batas aman Android `PlaybackParams` (min 0.1f, max 2.0f).
2. **Verifikasi Kompilasi & Build**:
   * Menjalankan `compile_applet` untuk memastikan kompatibilitas bytecode Kotlin dan Android SDK 26+.
   * Menjalankan `gradle testDebugUnitTest` untuk memverifikasi kestabilan seluruh test suite.
