# Rencana Implementasi: Transisi Halus Siklus Bridge (Master Loop Menunggu Selesai)

## Ringkasan Kebutuhan
Ketika mode **Bridge Cycle** aktif dan **Master Loop** sedang berputar (looping):
- Jika ada komentar masuk (atau tombol pemicu manual ditekan), aplikasi **TIDAK** memotong video Master Loop secara mendadak ke Bridge II.
- Video Master Loop dibiarkan berjalan normal hingga durasinya selesai.
- Begitu Master Loop selesai secara alami, alur otomatis berpindah ke **Bridge II**.
- Setelah video Bridge II selesai, alur berpindah ke **Video Konten Pemicu Komentar** (misal Video #5).
- Setelah video konten selesai, alur masuk ke **Bridge I** lalu kembali ke **Master Loop**.
- Jika terdapat beberapa komentar yang masuk berurutan saat Master Loop masih berjalan, komentar akan dimasukkan ke dalam antrean (FIFO) untuk diputar bergantian secara mulus.

---

## 1. Pembaruan State Model (`VcamConfig.kt`)
- Menambahkan koleksi antrean `bridgeContentQueue: List<Int> = emptyList()` pada `VcamConfig` untuk menampung pemicu komentar berurutan (FIFO).
- Memastikan `pendingBridgeContentIndex: Int?` mencatat target pemicu berikutnya yang sedang menunggu giliran.

---

## 2. Logika Pemicu & Antrean (`VcamRepository.kt`)
- **`triggerBridgeContentVideo(targetIndex: Int)`**:
  - Jika saat ini sedang di `BridgeCyclePhase.MASTER_LOOP`:
    - **Jangan** langsung potong video atau ubah fase ke `BRIDGE_2`.
    - Daftarkan target ke antrean (`pendingBridgeContentIndex` dan `bridgeContentQueue`).
    - Kirim sinyal ke pemutar video (di Preview & Virtual Camera Hook) untuk menonaktifkan loop native (`setLooping(false)`), sehingga putaran video master saat ini akan memicu `onVideoCompleted` tepat ketika durasinya habis.
    - Tampilkan log & notifikasi status bahwa komentar telah masuk dan menunggu Master Loop selesai.
  - Jika saat ini sedang di fase lain (`BRIDGE_2`, `CONTENT_VIDEO`, atau `BRIDGE_1`):
    - Tambahkan target ke dalam `bridgeContentQueue` untuk diproses setelah siklus saat ini rampung.
- **`advanceBridgeCycle()`**:
  - `MASTER_LOOP` selesai $\rightarrow$ jika ada `pendingBridgeContentIndex`, pindah ke `BRIDGE_2`.
  - `BRIDGE_2` selesai $\rightarrow$ pindah ke `CONTENT_VIDEO` dengan memutar target `pendingBridgeContentIndex`.
  - `CONTENT_VIDEO` selesai $\rightarrow$ pindah ke `BRIDGE_1`.
  - `BRIDGE_1` selesai $\rightarrow$ jika masih ada antrean di `bridgeContentQueue`, ambil item berikutnya lalu langsung sambung ke `BRIDGE_2` (atau kembali ke `MASTER_LOOP` jika antrean kosong).
  - Ketika kembali ke `MASTER_LOOP` tanpa antrean, aktifkan kembali native looping (`setLooping(true)`).

---

## 3. Sinkronisasi Hook IPC & Native Video Renderer (`MainHook.kt` & `HookBroadcastReceiver.kt`)
- Saat aksi `BRIDGE_QUEUE_CONTENT` atau state pending diperbarui:
  - Kirim broadcast IPC dengan flag `extra_bridge_cycle_pending`.
  - `GlVideoRenderer.setLooping(false)` diaktifkan agar virtual camera di TikTok/Shopee Live tidak me-looping otomatis di akhir frame, melainkan memicu listener completion untuk transisi otomatis ke Bridge II.
- Menjaga konsistensi indeks video dan display name agar log overlay menampilkan indikator status yang jelas: *"Menunggu Master Loop selesai..."*.

---

## 4. UI Preview & Floating Control (`CameraLivePreviewScreen.kt` & `FloatingControlService.kt`)
- Memperbarui `shouldLoopCurrentVideo` pada `CameraLivePreviewScreen` agar tetap `false` ketika terdapat antrean pending pada fase `MASTER_LOOP`.
- Menambahkan badge/indikator status pada Floating Control dan Live Preview ketika video sedang berada dalam antrean menunggu giliran.
- Memastikan tombol manual pemicu video di Floating Control menggunakan metode penundaan yang sama agar tidak memotong Master Loop secara kasar.

---

## Verifikasi & Pengujian
1. Uji kompilasi kode (`compile_applet`) untuk memastikan tidak ada kesalahan sintaks atau tipe data.
2. Pengujian skenario alur:
   - Jalankan Bridge Cycle $\rightarrow$ Master Loop berputar terus.
   - Kirim pemicu komentar $\rightarrow$ Master Loop tidak terputus, durasi berjalan hingga selesai.
   - Selesai Master Loop $\rightarrow$ Otomatis putar Bridge II.
   - Selesai Bridge II $\rightarrow$ Otomatis putar Video Pemicu Komentar.
   - Selesai Video Konten $\rightarrow$ Otomatis putar Bridge I.
   - Selesai Bridge I $\rightarrow$ Kembali berputar di Master Loop.
