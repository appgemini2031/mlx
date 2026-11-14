# Rencana Perbaikan Anomali Penanganan Event Gift (`LiveEventReceiver.kt`)

## Analisis Penyebab Anomali (Bug Identified)
1. **Penyimpanan Streak Tanpa Expiration Timestamp**:
   Peta `localGiftStreaks` di `LiveEventReceiver.kt` saat ini hanya menyimpan angka integer `gCount` terakhir tanpa mencatat waktu (`timestamp`).
2. **Event Gift Baru Terbuang (Dropped)**:
   Ketika penonton yang sama mengirimkan gift batch/combo baru setelah beberapa menit (misalnya mengirim Mawar x1 setelah sebelumnya pernah melakukan streak Mawar x20):
   - `gCount` (1) lebih kecil dari `prevStreak` (20).
   - Logika penanganan event menganggap `gCount > prevStreak` adalah SALAH (`false`), sehingga `deltaGifts` dihitung sebagai `0` dan `deltaCoins` menjadi `0`.
   - Akibatnya, gift baru tersebut **DIABAIKAN / DIBUANG** dan tidak pernah diproses ke mesin game (`InteractiveBeyBattleEngine`, `InteractiveTugOfWarEngine`, `InteractiveKingOfThroneEngine`, `InteractiveTrooperRoyaleEngine`).

---

## Solusi & Perubahan Kode:

### 1. Struktur Data `GiftStreakInfo` (`LiveEventReceiver.kt`)
Memperbarui `localGiftStreaks` menggunakan model data `GiftStreakInfo(val count: Int, val lastTimeMs: Long)` untuk melacak jumlah dan timestamp terakhir gift.

### 2. Logika Deteksi Reset Combo & Expiration Timeout
- Menambahkan batas timeout streak **4.0 detik** (`nowMs - lastTimeMs > 4000L`).
- Jika waktu berlalu lebih dari 4.0 detik ATAU `gCount <= prevEntry.count`, maka `prevStreak` otomatis di-reset ke `0` (menandakan awal batch gift baru).
- Seluruh delta hitungan gift (`deltaGifts`) dan koin (`deltaCoins`) dijamin selalu terhitung positif tanpa ada gift yang terlewat atau bernilai 0.

---

## Langkah Verifikasi
1. Verifikasi kompilasi aplikasi dengan `compile_applet`.
2. Jalankan unit test `LiveEventBrokerTest.kt` dan `BigoLiveEventBrokerTest.kt` via `gradle :app:testDebugUnitTest`.
