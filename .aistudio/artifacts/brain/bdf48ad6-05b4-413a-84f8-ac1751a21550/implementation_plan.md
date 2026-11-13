# Rencana Perbaikan Bug Siklus Bridge Cycle & Antrean Master Loop

Perbaikan komprehensif untuk mengatasi kendala pengujian siklus live streaming (*Bridge Cycle*) yang terhenti (*stuck*) di video Master Loop dan antrean yang tidak pernah berpindah ke Bridge II / video konten.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Berdasarkan jawaban klarifikasi yang telah Anda konfirmasi:
> - **Opsi Tes Siklus**: Disediakan 2 mode pengujian yaitu **"⚡ Langsung (Transisi Instan ke Bridge II)"** untuk pengujian cepat tanpa menunggu, dan **"⏳ Tunggu Master (Antre Halus)"** untuk pengujian transisi alami saat durasi Master Loop habis.
> - **Perilaku Transisi**: Cukup perbaiki otomasi siklus saat video selesai tanpa perlu tombol pemotong durasi terpisah.

---

### 1. Analisis Akar Masalah (Root Causes)

1. **Stale Closure di `VideoPlayerView.kt`**:
   - Parameter `isLooping` dan lambda `onVideoCompleted` tertangkap secara statis dalam blok `factory` dari `AndroidView`.
   - Ketika Master Loop pertama kali diputar dengan `isLooping = true`, listener penyelesaian (`setOnCompletionListener`) terkunci pada nilai `isLooping == true`.
   - Akibatnya, saat video Master Loop mencapai akhir durasi, pemutar lokal hanya mengeksekusi `mp.seekTo(0); mp.start()` dan sama sekali tidak pernah memanggil `onVideoCompleted.invoke()`.
   - Selain itu, penggunaan `mp.isLooping = true` pada native Android `MediaPlayer` menyebabkan sistem Android tidak mengirimkan event `MEDIA_PLAYBACK_COMPLETE`.

2. **Filter Broadcast Tidak Memeriksa Mode Bridge di `VcamRepository.kt`**:
   - Receiver broadcast `VIDEO_COMPLETED` di `VcamRepository` memiliki kondisi gerbang:
     `if ((_config.value.autoNextEnabled || _pendingVideoIndex.value != null || _config.value.isMasterSpillMode) && _config.value.playlist.isNotEmpty())`
   - Dalam mode Bridge Cycle, `autoNextEnabled` bernilai `false`, `_pendingVideoIndex` bernilai `null` (karena menggunakan `pendingBridgeContentIndex`), dan `isMasterSpillMode` bernilai `false`.
   - Kondisi tersebut bernilai `false`, sehingga broadcast `VIDEO_COMPLETED` dari target hook langsung diabaikan.
   - Ditambah lagi, baris pemeriksaan awal `if (extraSource == "SEAMLESS_PRELOAD") return` keluar sebelum baris `advanceBridgeCycle` dapat dieksekusi.

3. **Sinkronisasi Hook di `GlVideoRenderer.kt`**:
   - Deteksi antrean `hasQueuedVideo` di `GlVideoRenderer` hanya memeriksa `livePendingVideoPath != null`, dan tidak mengetahui keberadaan `pendingBridgeContentIndex` dari Bridge Cycle.

---

### 2. Solusi & Perubahan yang Akan Dilakukan

#### A. Komponen Pemutar Preview (`VideoPlayerView.kt`)
- Menggunakan `rememberUpdatedState(isLooping)` dan `rememberUpdatedState(onVideoCompleted)` agar closure selalu mengacu pada kondisi runtime terbaru.
- Menghapus ketergantungan pada native `mp.isLooping = true` ketika ada listener `onVideoCompleted`. Biarkan loop ditangani di level listener `setOnCompletionListener` sehingga setiap kali video selesai, event selalu tertangkap dan mengevaluasi status antrean secara akurat.

#### B. Logika Siklus & IPC (`VcamRepository.kt` & `VcamViewModel.kt`)
- Menambahkan parameter `immediate: Boolean = false` pada `triggerBridgeContentVideo(indexOrId, immediate)`:
  - **Jika `immediate = true`**: Langsung berpindah ke fase `BridgeCyclePhase.BRIDGE_2`, mengarahkan ke Bridge II dengan target video konten yang dipilih.
  - **Jika `immediate = false`**: Memasukkan ke antrean pending (`pendingBridgeContentIndex` / `bridgeContentQueue`), menonaktifkan loop native Master Loop, dan saat Master Loop mencapai akhir durasi, otomatis beralih ke Bridge II ➡️ Konten.
- Memperbaiki receiver `VIDEO_COMPLETED` di `VcamRepository`:
  - Menyertakan `_config.value.isBridgeCycleMode` dalam filter utama.
  - Memastikan event preload / completion pada mode Bridge Cycle memicu `advanceBridgeCycle(broadcastToHook = true)`.

#### C. Antarmuka Pengujian Siklus (`MediaManagerScreen.kt` & Tab Floating)
- Memperbarui tombol di kartu video konten Bridge Cycle:
  - Tombol **"⚡ Langsung"** untuk pengujian cepat instan ke Bridge II.
  - Tombol **"⏳ Tunggu Master"** untuk pengujian transisi natural setelah video master selesai.
  - Jika sedang mengantre, menampilkan indikator status aktif antrean dan posisi urutannya.

---

### 3. Arsitektur Aliran Siklus (Cycle Flow Diagram)

```
┌──────────────────────────────────────────────────────────────┐
│                    Siklus Bridge Aktif                       │
└──────────────────────────────────────────────────────────────┘
                               │
                               ▼
                        [ Bridge I ]
                  (Video intro pembuka 1x)
                               │
                               ▼ Selesai
                    ┌─────────────────────┐
                    │     Master Loop     │ ◄───┐
                    │ (Standby & Berputar)│     │
                    └─────────────────────┘     │
                               │                │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
   [ Opsi A: ⚡ Langsung ]              [ Opsi B: ⏳ Antre ]
   - Langsung potong Master            - Master habiskan durasi
   - Beralih ke Bridge II              - Selesai ➡️ Bridge II
            │                                     │
            └──────────────────┬──────────────────┘
                               │
                               ▼
                         [ Bridge II ]
                 (Video jembatan respons 1x)
                               │
                               ▼ Selesai
                      [ Video Konten #N ]
                 (Produk / Spill / Demo 1x)
                               │
                               ▼ Selesai
                         [ Bridge I ]
                 (Kembali ke pembuka 1x)
                               │
                               └────────────────┘
```

---

### 4. Rencana Verifikasi

1. **Uji Coba Otomatisasi Siklus Lokal (Preview UI)**:
   - Menjalankan simulasi `triggerBridgeContentVideo` dengan opsi "Tunggu Master" dan memverifikasi saat video mencapai detik akhir, pemutar berpindah ke Bridge II, lalu ke Konten, lalu ke Bridge I, lalu kembali ke Master Loop.
   - Menjalankan simulasi `triggerBridgeContentVideo` dengan opsi "Langsung" dan memverifikasi transisi terjadi seketika tanpa tertahan di Master Loop.
2. **Uji Coba Unit Test (`BridgeCycleTest.kt`)**:
   - Memperbarui test case untuk memverifikasi penanganan antrean FIFO dan transisi instan maupun natural.
3. **Kompilasi Proyek**:
   - Menjalankan `compile_applet` untuk memastikan seluruh perubahan kode bebas dari error sintaks maupun tipe data.
