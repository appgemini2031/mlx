# Rencana Implementasi: Mesin Cetak Polaroid Wall of Fame (Fleksibel & Realistis)

Menjawab kebutuhan streamer agar **semua posisi banner & elemen dapat dipindah dan disesuaikan secara fleksibel**, serta **desain visual printer dan background dibuat realistis**, rencana implementasi disempurnakan dengan kemampuan kustomisasi tata letak penuh dan grafis detail tinggi.

---

## 1. Fitur Utama & Kustomisasi Fleksibel (Drag & Dock)

Streamer memiliki tata letak layar live yang berbeda-beda (menghindari tertutup komentar di kiri bawah, tertutup tombol gift di kanan bawah, atau wajah streamer). Oleh karena itu, seluruh komponen dibuat fleksibel:

1. **Posisi Elemen Bebas & Fleksibel (Draggable / Dock Presets)**:
   * **Mesin Printer**: Dapat ditempatkan di Atas-Tengah, Kiri-Atas, Kanan-Atas, atau digeser (*drag*) bebas ke koordinat manapun di layar.
   * **Papan Wall of Fame (Pajangan Foto)**: Pilihan layout:
     * *Tali Jemuran Foto (Hanging Wire)* dengan jepit kayu realistis.
     * *Papan Gabus Dinding (Corkboard Grid)*.
     * *Strip Vertikal Samping (Side Stream Streamer Bar)*.
   * **Banner Instruksi & Leaderboard**: Dapat dipindah ke Atas/Bawah, diubah skalanya (zoom in/out 70%–130%), atau disembunyikan (*hide toggle*).
2. **Pilihan Background Realistis & Transparan**:
   * **Studio Corkboard & Pin**: Papan kayu gabus realistis dengan tekstur serat kayu dan pin/paku payung penusuk foto.
   * **Neon Dark Acoustic Studio**: Tampilan studio modern gelap dengan bayangan mendalam (*deep drop shadow*).
   * **Mode Transparan (Chroma / Pure Overlay)**: Latar belakang 100% transparan agar foto polaroid melayang langsung di atas video kamera live streaming.
   * **Green Screen Mode**: Opsi latar hijau murni jika streamer menggunakan OBS filter chroma key.

---

## 2. Desain Visual Realistis Mesin Printer & Kertas Polaroid

* **Mesin Cetak Fisik Realistis**:
  * Mulut slot cetak (*printer mouth slot*) dengan efek bevel metallic dan bayangan lubang realistis.
  * Roller roda mekanik yang berputar halus saat kertas foto mulai dikeluarkan (*paper feed animation*).
  * Indikator LED fisik: Lampu hijau stand-by, lampu oranye berkedip saat sedang mencetak (*printing in progress*).
* **Kertas Foto Polaroid Nyata**:
  * Format rasio polaroid klasik dengan margin bawah lebar untuk tulisan spidol.
  * Tulisan spidol tulisan tangan (*marker handwriting style*) untuk nama penonton dan jenis saweran.
  * Jepitan kayu (*wooden clothespin*) atau selotip bening (*tape sticker*) di sudut foto.
  * Efek bayangan 3D (*realistic ambient drop shadow*) dan sedikit kemiringan acak (-4° s/d +4°) agar tidak kaku.
* **Tingkatan Bingkai (Tiering Visual)**:
  * 👑 **Sultan VIP**: Kertas polaroid bertepian foil emas berkilau (*metallic gold shimmer*) + mahkota 3D.
  * 💎 **Gifter Koin**: Kertas glossy polaroid premium + cap stempel "GIFT HERO".
  * ❤️ **Pejuang Tap-Tap (Gratis)**: Kertas polaroid dengan stempel stiker hati neon merah + badge "❤️ TAP-TAP PEJUANG".

---

## 3. Sistem Inklusif (Koin + Gratis)

* **Jalur Sultan**: Otomatis cetak prioritas saat menerima gift koin.
* **Jalur Pejuang Tap-Tap (Gratis)**: Akumulasi likes/tap-tap layar penonton (target dapat disetel, misal 50–100 likes) langsung memicu cetak foto gratis.
* **Jalur Chat Komentar**: Ketik kata kunci (misal `!cetak`, `HADIR`) untuk antrean cetak komunitas.
* **Panel Kontrol Simulasi & Uji Coba**:
  * Tombol uji simulasi Sultan Gift, simulasi 50x Tap-Tap, dan simulasi Chat.
  * Slider penyesuaian ukuran printer dan foto.
  * Tombol Reset Wall (*Clear Canvas*).

---

## 4. Arsitektur Berkas & Komponen

1. **`PolaroidWallModels.kt`**:
   * Data model `PolaroidPhotoItem`, `TriggerSource`, `FrameTier`, `BackgroundTheme`, dan `LayoutDockConfig` (koordinat x/y, skala, opasitas, docking mode).
2. **`InteractivePolaroidWallEngine.kt`**:
   * Singleton controller yang mendengarkan event gift, likes, dan comment dari `LiveEventReceiver`.
   * Mengatur antrean cetak fisik, durasi eject kertas, dan persistence pengaturan layout.
3. **`PolaroidWallArenaScreen.kt`**:
   * Komponen UI Jetpack Compose:
     * `RealisticPrinterSlot`: Slot mesin cetak dengan roller berputar dan lampu status.
     * `RealisticPolaroidCard`: Kertas polaroid dengan jepit kayu, bayangan 3D, foto profil penonton, dan badge.
     * `WallOfFameBoard`: Papan pameran foto dengan dukungan Corkboard, Neon, atau Transparan.
     * `DraggableOverlayBox`: Wrapper modifier agar banner, printer, dan leaderboard bisa digeser/diatur posisinya secara interaktif.
4. **Integrasi ke Hub Aplikasi**:
   * `GameHubMainScreen.kt`: Menambahkan kartu menu "Mesin Cetak Polaroid Realistis" lengkap dengan ringkasan status.
   * `VcamMainScreen.kt` & `MainActivity.kt`: Menghubungkan layar arena fullscreen overlay.

---

## 5. Rencana Verifikasi

* **Unit Test (`PolaroidWallEngineTest.kt`)**:
  * Pengujian sequence cetak, antrean multi-event, dan verifikasi kalkulasi posisi/skala.
* **Build Verification (`compile_applet`)**:
  * Memastikan seluruh kode Kotlin dan Jetpack Compose bebas error dan berjalan stabil.
