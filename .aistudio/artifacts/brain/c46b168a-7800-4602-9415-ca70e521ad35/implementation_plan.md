# Revisi Komprehensif: Solusi Multi-Metode Penanganan Streak & Anomali Seluruh Event TikTok Live Relay

Rencana arsitektur yang diperbarui dengan **Multi-Metode Penanganan Gift Streak & Combo** (tidak hanya mengandalkan satu timer kaku), serta audit dan resolusi menyeluruh terhadap seluruh event siaran TikTok Live (Gift, Like, Chat, Join, dan Room Stats).

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Tanggapan atas Masukan:** Penanganan streak gift kini dirombak menjadi **arsitektur multi-metode berlapis (hybrid multi-strategy)**, bukan hanya satu timer statis. Sistem memadukan flag protokol resmi, deteksi penurunan angka instan, prioritas delta upstream, dan mode operasional yang dapat dipilih host.

### Ringkasan 4 Pilar Metode Penanganan Streak:
1. **Metode 1: Protocol-Driven (`groupId` & `repeatEnd`)**:
   Memanfaatkan identifier unik combo (`groupId`/`comboId`) dan flag resmi `repeatEnd: true` dari TikTok Webcast Protobuf. Setiap combo baru memiliki ID terpisah sehingga tidak pernah tercampur.
2. **Metode 2: Monotonic Decrement Detection (Reset Instan)**:
   Jika jumlah gift mengecil (`gCount < previousStreak`), sistem langsung menyimpulkan ini adalah saweran baru tanpa menunggu timer 1 milidetik pun.
3. **Metode 3: Upstream Authority (`deltaCount` Priority)**:
   Jika relay bridge Termux/Node.js sudah menghitung `deltaCount` dan `deltaDiamonds`, Android langsung mempercayai nilai tersebut untuk menghindari inkonsistensi perhitungan ganda.
4. **Metode 4: Adaptive Sliding Window Timeout**:
   Sebagai *safety net* jika koneksi jaringan mengalami jitter atau paket `repeatEnd` hilang di perjalanan, sistem menutup combo setelah jeda adaptif (3,5 detik).
5. **Mode Pilihan di Pengaturan (Configurable Mode)**:
   Host dapat memilih antara:
   - **Smart Hybrid (Direkomendasikan)**: Kombinasi Metode 1-4 di atas.
   - **Raw / Stateless Direct**: Setiap payload gift langsung dieksekusi apa adanya tanpa deduplikasi streak (cocok untuk game tempo cepat).
   - **Milestone Burst**: Hanya memicu animasi jurus arena ketika combo mencapai kelipatan tertentu (x10, x50, x100).

---

## 1. Overview & Core Concept

Arsitektur Relay menghubungkan **Device 1 (Termux / PC)** dengan **Device 2 (Aplikasi Android Siaran)** melalui WebSocket lokal port 8765.

### Matriks Solusi Multi-Metode Anomali Seluruh Event:

| Jenis Event | Masalah / Anomali | Solusi Multi-Metode yang Diterapkan |
| :--- | :--- | :--- |
| **GIFT STREAK** | Gift baru terbuang (`delta = 0`) atau streak macet | **Multi-Metode**: Flag `repeatEnd` + Reset `gCount < prev` + Priority `deltaCount` + Sliding Window Fallback |
| **LIKE BURST** | Like cepat tertahan antrean banner overlay | Pemisahan (*decoupling*) antara komputasi skor game instan vs visual banner |
| **CHAT PARSE** | Spasi, karakter emoji, huruf besar/kecil, dan residu game lama | Normalisasi token perintah game & pembersihan total listener game 1 & 2 |
| **JOIN FLOOD** | Lonjakan penonton masuk membebani alokasi memori | Queue asinkron avatar dengan batas kapasitas aman & deduplikasi ID |
| **ROOM STATS** | Angka penonton sesaat menjadi 0 saat inisialisasi / reconnect | Latch buffer mempertahankan angka penonton valid terakhir jika payload bernilai 0 |

---

## 2. User Experience & Visual Design

### Skenario Pengujian Aliran Penonton (User Flows)

1. **Skenario Combo Cepat (Streak 1 ➔ 2 ➔ 5 ➔ 10)**:
   - Penonton mengirim combo Mawar. Arena BeyBattle & Trooper Royale merespons secara beruntun dengan pertambahan skor delta `+1, +1, +3, +5`.
   - Angka total koin dan papan peringkat (*Top Likers/Gifters*) sinkron secara mulus.

2. **Skenario Penurunan Angka Instan (Streak 20 lalu kirim 1)**:
   - Penonton sebelumnya mengirim 20 Mawar. Dua menit kemudian penonton mengirim 1 Mawar.
   - **Hasil**: Sistem mendeteksi `1 < 20`, langsung me-reset baseline ke 0, menghitung delta = 1, dan mengeksekusi aksi game tanpa ada koin yang hilang atau bernilai 0.

3. **Skenario Selesai Combo Resmi (`repeatEnd = true`)**:
   - TikTok mengirimkan sinyal penutup combo. Sistem langsung membersihkan memori streak penonton tersebut sehingga kiriman berikutnya bersih 100%.

4. **Skenario Jeda Santai (Timeout Fallback)**:
   - Penonton berhenti mengetuk combo di tengah jalan tanpa sinyal `repeatEnd`. Setelah 3,5 detik, sistem menutup sesi streak secara otomatis di latar belakang.

---

## 3. Key Product Decisions & Trade-Offs

- **Keputusan 1: Mengapa Pendekatan Berlapis (Multi-Metode) Lebih Baik?**
  - Mengandalkan hanya satu timer rentan terhadap delay jaringan seluler (jitter).
  - Mengandalkan hanya flag `repeatEnd` rentan jika paket protobuf putus.
  - Dengan memadukan ID combo, penurunan angka, flag protokol, dan timer pengaman, **tidak ada satu pun skenario di mana gift penonton akan terbuang**.

- **Keputusan 2: Pemisahan Total Residu Engine Game 1 & 2**:
  - Seluruh pemanggilan ke `InteractiveTugOfWarEngine` dan `InteractiveKingOfThroneEngine` di dalam `LiveEventReceiver.kt` dihapus tuntas.
  - Event routing dialokasikan 100% secara eksklusif ke `InteractiveBeyBattleEngine` dan `InteractiveTrooperRoyaleEngine`.

---

## 4. Technical Architecture & Data Strategy

### Struktur Data Multi-Metode Tracker di Android (`LiveEventReceiver.kt`)

```kotlin
data class GiftStreakSession(
    val groupId: String,
    val lastCount: Int,
    val lastUpdateMs: Long,
    val isCompleted: Boolean = false
)
```

### Logika Eksekusi Multi-Metode (Algoritma Penentu Delta):

```
                       [Incoming Gift Event Payload]
                                    │
                                    ▼
                      Ada field "deltaCount" valid?
                                   / \
                            [Ya]  /   \  [Tidak]
                                 /     \
                                ▼       ▼
                    Gunakan deltaCount  Evaluasi Sesi Streak
                    langsung (Metode 3)        │
                                               ▼
                              Apakah groupId berubah ATAU
                              gCount < previousCount ATAU
                              repeatEnd == true ATAU
                              (now - lastUpdateMs) > TIMEOUT_MS?
                                              / \
                                       [Ya]  /   \  [Tidak]
                                            /     \
                                           ▼       ▼
                               Reset Streak ke 0  Hitung Delta:
                               Delta = gCount     gCount - prevCount
                                           \       /
                                            \     /
                                             ▼   ▼
                               [Eksekusi ke Game Arena]
```

### Diagram Arsitektur Komponen yang Diperbarui:

```
┌─────────────────────────────────────────────────────────────┐
│                   TIKTOK LIVE STREAM SERVER                 │
└──────────────────────────────┬──────────────────────────────┘
                               │ Webcast Protobuf
                               ▼
┌─────────────────────────────────────────────────────────────┐
│           DEVICE 1: TERMUX RELAY ENGINE (Node.js/Python)     │
│  - tiktok_bridge.js: Tangkap repeatEnd, groupId, deltaCount │
│  - tiktok_event_server.py: Broker WebSocket LAN (8765)      │
└──────────────────────────────┬──────────────────────────────┘
                               │ JSON Event Frame
                               ▼
┌─────────────────────────────────────────────────────────────┐
│          DEVICE 2: ANDROID LIVE EVENT RECEIVER              │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 1. Filter Platform: TIKTOK                            │  │
│  └───────────────────────────┬───────────────────────────┘  │
│                              │                              │
│  ┌───────────────────────────┴───────────────────────────┐  │
│  │ 2. Multi-Metode Resolver Engine                       │  │
│  │    ├─ Layer 1: Protocol Flag (repeatEnd & groupId)    │  │
│  │    ├─ Layer 2: Monotonic Decrement Check (Reset)      │  │
│  │    ├─ Layer 3: Upstream Delta Priority               │  │
│  │    ├─ Layer 4: Adaptive Sliding Window Fallback       │  │
│  │    ├─ Like: Decoupled Banner + Direct Tap Buffer      │  │
│  │    ├─ Chat: Normalized Tokenizer (Bey/Trooper Only)   │  │
│  │    ├─ Join: Safe Async Avatar Limiter                 │  │
│  │    └─ Stats: Non-Zero Fallback Latch                  │  │
│  └───────────────────────────┬───────────────────────────┘  │
│                              │                              │
│              ┌───────────────┴───────────────┐              │
│              ▼                               ▼              │
│    ┌───────────────────┐           ┌───────────────────┐    │
│    │  BEYBATTLE ARENA  │           │  TROOPER ROYALE   │    │
│    │  (Gangsing Live)  │           │  (Prajurit Chibi) │    │
│    └───────────────────┘           └───────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Rencana Verifikasi Teknis

1. **Pengujian Unit Otomatis (`LiveEventBrokerTest.kt`)**:
   - `testGiftStreakProtocolRepeatEnd`: Memverifikasi reset instan saat `repeatEnd: true`.
   - `testGiftStreakDecreasingCountReset`: Memverifikasi transisi hitungan menurun (misal 20 ➔ 1) menghasilkan delta = 1, bukan 0.
   - `testGiftStreakGroupIdChange`: Memverifikasi pergantian combo ID baru mereset streak lama seketika.
   - `testGiftStreakTimeoutFallback`: Memverifikasi batas timeout pengaman bekerja saat koneksi jitter.
   - `testGiftUpstreamDeltaPriority`: Memverifikasi prioritas `deltaCount` dari bridge.
   - `testRoomStatsNonZeroSmoothing`: Memverifikasi angka penonton tidak drop ke 0 saat reconnect.
   - `testCleanDecoupledOldGames`: Memastikan tidak ada exception atau pemanggilan ke TugOfWar dan KingOfThrone.
2. **Kompilasi & Build**:
   - Menjalankan `compile_applet`.
   - Menjalankan seluruh test suite melalui `gradle :app:testDebugUnitTest`.
