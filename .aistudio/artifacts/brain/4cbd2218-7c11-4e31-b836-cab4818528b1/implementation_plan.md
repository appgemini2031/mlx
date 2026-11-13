# Sistem Decoy Freeze Dinamis (Acak Putaran 1–N & Persentase Durasi 5%–95%)

Pembaruan sistem Gerbang Decoy agar terasa 100% seperti ketidakstabilan hardware alami yang tidak memiliki pola tetap: setiap sesi kamera memilih putaran freeze secara acak antara **Putaran ke-1 sampai ke-N** (beserta peluang lolos putaran dinamis) dan membekukan kamera + layar APK pada **5% hingga 95% durasi video** tanpa notifikasi layar apa pun.

## User Review & Critical Decisions

> [!IMPORTANT]
> Keputusan berikut telah dikonfirmasi dan menjadi dasar arsitektur pembaruan:

- **Rentang Putaran Dinamis (Acak 1 sampai $N$):** Jika dari Bot Telegram diatur nilai $N$ (misalnya **4**, maka acak antara **putaran ke-1 sampai ke-4**; jika diatur **2**, maka acak antara **putaran ke-1 sampai ke-2**). Pengguna tidak akan pernah bisa menebak di putaran keberapa aplikasi akan membeku karena setiap kali aplikasi dibuka ulang, target putaran diundi ulang secara acak.
- **Persentase Durasi Acak (5%–95%) + Peluang Lolos Putaran:** Di dalam putaran yang terpilih, titik *freeze* diundi secara acak dari **5% hingga 95% total durasi video** (bisa di awal sekali, di tengah, atau menjelang akhir video). Selain itu, terdapat **peluang lolos putaran dinamis** (ketika mencapai putaran target sebelum batas maksimum $N$, mesin memiliki peluang acak untuk membiarkan putaran tersebut lolos ke putaran berikutnya hingga maksimal putaran ke-$N$), sehingga ritme macet terasa benar-benar organik.
- **Kontrol Rentang Dinamis & Input Custom di Bot Telegram:** Admin dapat memilih preset rentang dinamis (**Acak 1–2**, **Acak 1–3**, **Acak 1–4**, **Acak 1–5**, **Acak 1–6 Putaran**) atau mengetikkan langsung angka custom $N$ di Telegram untuk mengatur rentang acak **1 sampai $N$ putaran**, serta tombol **Cabut Bug Decoy (VIP Normal 100%)** untuk menormalkan kamera dan membuka kunci layar secara instan.
- **Notifikasi Layar Tetap OFF untuk Non-VIP:** Seluruh Toast, Snackbar, dan indikator layar tetap dimatikan total pada Gerbang Awal (non-VIP) dan hanya aktif untuk pengguna VIP.

---

## 1. Overview & Core Concept

- **What It Does:** Menggantikan penghitung putaran statis dengan **Mesin Probabilitas Putaran Dinamis (1–$N$)** dan **Acak Persentase Durasi (5%–95%)**. Saat target tercapai, kamera membeku diam pada *frame* terakhir, audio mati, dan UI aplikasi/APK terkunci total hingga pengguna keluar aplikasi.
- **Target Audience / Persona:** Pemilik sistem yang ingin melindungi APK dari penggunaan tanpa izin dengan efek *hardware hang* yang natural dan mustahil diprediksi polanya, sambil tetap memberikan pengalaman 60 FPS mulus untuk pengguna VIP pilihan.
- **Key Value:** Menghilangkan kesan "bug buatan yang selalu muncul di putaran ke-2" karena kadang kamera langsung *freeze* di putaran ke-1 (awal/akhir), kadang di putaran ke-3, dan kadang lolos hingga putaran ke-$N$.

---

## 2. User Experience & Visual Design

- **Key User Flows:**
  1. **Pengguna Non-VIP Membuka Kamera (Sesi 1):** Misalkan batas maksimum diatur $N = 4$ (Acak 1–4 putaran). Pada Sesi 1, sistem mengundi putaran ke-3 pada posisi 82% durasi video. Putaran 1 dan 2 berjalan mulus 60 FPS, lalu di akhir putaran ke-3 kamera & layar APK tiba-tiba membeku total.
  2. **Pengguna Force-Close & Buka Lagi (Sesi 2):** Saat aplikasi dibuka kembali, sistem mengundi ulang dari rentang $1 \dots 4$. Kali ini terpilih putaran ke-1 pada posisi 14% durasi (atau mendapat peluang lolos ke putaran ke-2 pada posisi 61%). Polanya selalu berubah di setiap sesi.
  3. **Kontrol Admin di Telegram:** Admin membuka detail perangkat di Bot Telegram $\rightarrow$ klik **Atur Freeze Dinamis (1–N) & Trial** $\rightarrow$ memilih tombol rentang (misal **Acak 1–3 Putaran** atau **Acak 1–5 Putaran**) atau menekan **Input Angka Putaran Custom (1–N)** dan mengetik angka (misal `4` untuk acak 1–4 putaran).
  4. **Pencabutan Decoy (VIP Normal):** Saat admin menekan **Cabut Bug Decoy (VIP Normal)** di Telegram, seluruh *freeze* langsung dilepas dalam $< 0.5$ detik tanpa perlu keluar aplikasi, dan fitur Toast VIP diaktifkan.
- **Visual Identity & Theme:**
  - *Aesthetic Direction:* *Zero-Trace Silent Hang* pada perangkat non-VIP — tidak ada perubahan warna, tidak ada kedipan buatan, tidak ada *banner* peringatan, dan tidak ada *Toast* saat kamera mulai atau saat *freeze* terjadi.
  - *Interactive Feedback:* Saat *freeze* terjadi di titik acak (5%–95%), gambar kamera diam di *frame* terakhir dan seluruh layar sentuh terkunci (`FLAG_NOT_TOUCHABLE` + *Main Thread UI Stall*).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Algoritma Dua Lapis (Undian Putaran $1 \dots N$ + Peluang Lolos Putaran + Jitter Durasi 5%–95%)**
  - *Chosen Approach:* Saat sesi video dimulai, mesin memilih kandidat putaran awal $L \in [1, N]$. Ketika video memasuki putaran $L$ (selama $L < N$), mesin mengevaluasi peluang lolos (~30% kemungkinan lolos ke putaran $L+1$). Pada putaran yang akhirnya dikunci untuk *freeze*, titik waktu *freeze* dihitung secara acak merata antara **5% hingga 95% durasi video** (`durationMs * Random(0.05..0.95)`).
  - *Why:* Menghasilkan distribusi waktu *freeze* yang sangat bervariasi—bisa terjadi di detik-detik awal putaran pertama, di pertengahan putaran kedua, atau di ujung putaran keempat—sehingga tidak terlihat seperti *timer* atau penghitung matematis.
- **Decision 2: Dukungan Preset Rentang & Input Angka Custom di Telegram**
  - *Chosen Approach:* Menyediakan tombol instan (`1–2`, `1–3`, `1–4`, `1–5`, `1–6`) sekaligus fitur *prompt* ketik angka bebas di Telegram (`SET_DECOY_LOOP:<dev_id>`), di mana mengetik angka $N$ otomatis mengatur rentang acak `1 sampai N putaran`.
  - *Why:* Memudahkan pengaturan cepat lewat tombol maupun fleksibilitas penuh jika ingin angka putaran spesifik.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram:**

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        TELEGRAM BOT ADMIN PANEL                            │
│  • Preset Dinamis: Acak 1–2 | 1–3 | 1–4 (Default) | 1–5 | 1–6 Putaran      │
│  • Input Angka Custom (Ketik N → Acak 1 sampai N Putaran)                  │
│  • Tombol VIP: Cabut Bug Decoy (VIP Normal 100%)                           │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ Cloud Firestore Realtime Sync
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   LICENSE & DECOY CONFIGURATION LAYER                      │
│  • Menyimpan batas atas putaran dinamis: maxFreezeLoops = N (Default: 4)   │
│  • Sinkronisasi lintas proses via ContentProvider & Broadcast              │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              DYNAMIC LOOP & PERCENTAGE RANDOMIZER (ENGINE)                 │
│  1. Saat kamera dibuka: Pilih target putaran awal secara acak dari [1..N]  │
│  2. Di setiap pergantian putaran:                                          │
│     • Jika putaran == target & putaran < N → Evaluasi Peluang Lolos (~30%) │
│       (Jika lolos, geser target ke putaran + 1 hingga batas maksimal N)    │
│     • Jika putaran dikunci untuk Freeze → Undi persentase acak 5% s/d 95%  │
│       dari total durasi video (500ms s/d 95% durasi)                       │
│  3. Saat posisi video melewati titik persentase acak tersebut:             │
│     • Pause Decoder & kunci frame terakhir di layar                        │
│     • Matikan audio & blokir auto-recovery Watchdog / Auto-Rocket          │
│     • Kunci sentuhan layar (FLAG_NOT_TOUCHABLE) & bekukan Main Thread UI   │
└────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State:**
  - `DecoyConfig.freezeAfterLoopCount`: Menyimpan nilai batas atas $N$ untuk rentang dinamis `1..N` (default `4`, yang berarti acak antara putaran ke-1 sampai ke-4; jika `2` berarti acak putaran ke-1 sampai ke-2).
  - State Sesi Renderer (`GlVideoRenderer` & `VideoPlayerView`):
    - `decoyMaxLoopRange`: Nilai $N$ dari konfigurasi.
    - `decoyActiveTargetLoop`: Hasil undian acak `Random.nextInt(1, N + 1)` saat sesi kamera dimulai.
    - `decoyRandomFreezePercent`: Persentase acak `Random.nextDouble(0.05, 0.95)` yang dikonversi ke milidetik durasi video.
- **Interactive Component & State Mapping:**
  - **Saat Ganti Video / Buka Kamera Baru:** Mengundi ulang `decoyActiveTargetLoop` dalam rentang `1..N` dan me-reset penghitung putaran ke `1`.
  - **Saat Memasuki Putaran Target:** Mengundi apakah mendapat "Peluang Lolos Putaran" (jika masih $< N$). Jika tidak lolos (atau sudah mencapai putaran ke-$N$), menetapkan titik *freeze* pada `5%–95%` durasi video dan mengeksekusi *Hard Freeze* kamera + APK tepat saat *frame* melewati titik tersebut.
  - **Saat Admin Menekan VIP Normal di Telegram:** Langsung membatalkan status *freeze*, membuka kembali input layar aplikasi, dan mengizinkan pemutaran normal tanpa batas putaran.
