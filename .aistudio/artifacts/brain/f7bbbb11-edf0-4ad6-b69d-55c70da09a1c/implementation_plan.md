# Rencana Pembersihan Proyek — asisten copy

Proyek ini akan dibersihkan secara menyeluruh dari seluruh fitur lama sehingga hanya menyisakan fondasi inti yang ringan dan rapi: layanan **Floating Window/Bubble** dengan isi kosong bersih di sisi Android, serta **logika pipeline GitHub & `update.sh`** untuk otomasi build, rilis, dan pembaruan APK.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Seluruh fitur lama (Virtual Camera/Xposed, Game Interaktif, Khatam Al-Qur'an, Photo Studio, Audio/Video Player, Auto-Tap/Accessibility, Live Event Broker, RTMP, VPN/AdBlocker, Lisensi/Decoy, Telemetri Remote, Firebase, dan Bot Telegram) akan dihapus permanen agar basis kode kembali bersih total.

- **Identitas Aplikasi (Dikonfirmasi)**: Nama aplikasi dan identitas proyek diseragamkan menjadi **`asisten copy`** di pengaturan proyek, label peluncur Android, dan metadata platform.
- **Ruang Lingkup Aplikasi Android (Dikonfirmasi)**: Hanya mempertahankan **Floating Window/Bubble** dengan panel isi kosong yang bersih, ditambah layar utama minimalis untuk izin overlay, saklar Floating, serta modul cek & unduh pembaruan APK dari GitHub.
- **Ruang Lingkup Termux & GitHub (Dikonfirmasi)**: Hanya mempertahankan pipeline GitHub (`update.sh`, pengunduh APK otomatis, manajemen rilis GitHub, dan workflow GitHub Actions). Semua skrip broker live, bot Telegram, generator lisensi, dan utilitas non-GitHub dihapus.
- **Keputusan Kompatibilitas Update APK (Default Disarankan)**: `applicationId` dan sertifikat keystore permanen tetap dipertahankan apa adanya agar APK baru hasil build GitHub Actions tetap bisa menimpa instalasi sebelumnya tanpa bentrok tanda tangan (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

---

### 1. Overview & Core Concept

- **What It Does**:
  - **Sisi Android (`asisten copy`)**: Menyediakan aplikasi pendamping ringan dengan layanan latar depan (*Foreground Service*) untuk menampilkan gelembung (*floating bubble*) dan panel mengambang (*floating panel*) yang dapat digeser, diciutkan/diperluas, dan ditutup secara aman, dengan isi panel yang benar-benar kosong dan bersih sebagai kanvas dasar. Di layar utama aplikasi tersedia kontrol izin *Display over other apps*, saklar aktif/nonaktif Floating, serta dukungan cek pembaruan APK langsung dari repositori GitHub.
  - **Sisi Termux & GitHub CI/CD**: Menyediakan gerbang CLI terpadu (`update.sh`) yang fokus murni untuk menyinkronkan kode ke GitHub, memicu build APK otomatis melalui GitHub Actions, memantau kompilasi secara *real-time*, mengunduh APK hasil rilis ke folder Download ponsel, serta mengelola/menghapus rilis di GitHub.
- **Target Audience / Persona**: Pengembang atau pemilik proyek yang menginginkan kerangka dasar aplikasi *floating overlay* yang bersih tanpa beban fitur lama, sekaligus mempertahankan alur kerja *push-build-download* berbasis Termux dan GitHub.
- **Key Value**: Ukuran kode dan APK menyusut drastis, waktu kompilasi Gradle dan GitHub Actions menjadi jauh lebih cepat, izin Android menjadi minimal dan patuh kebijakan, serta alur rilis Termux–GitHub tetap berjalan mulus tanpa gangguan modul lama.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Membuka Aplikasi Utama**: Pengguna melihat layar gelap modern bertajuk **`asisten copy`** dengan indikator status layanan Floating, tombol pemberian izin overlay (jika belum aktif), saklar utama untuk memunculkan/menyembunyikan Floating Window, serta kartu ringkas untuk memeriksa atau mengatur sumber pembaruan APK GitHub (repo & token PAT untuk repo privat).
  2. **Menggunakan Floating Bubble & Panel**: Saat diaktifkan, gelembung/bar mini muncul mengambang di layar dan dapat digeser bebas. Mengetuk tombol buka akan menampilkan panel mengambang kosong yang bersih dengan header ringkas (judul `asisten copy`, tombol ciutkan ke bubble, dan tombol tutup layanan). Pengguna dapat mengecilkan panel kembali menjadi bubble kapan saja.
  3. **Menjalankan `update.sh` di Termux**: Pengguna disuguhi menu ringkas yang hanya berisi opsi GitHub Pipeline (Push & Auto-Purge CI Build, Unduh APK Rilis, Manajemen Rilis GitHub, Bersihkan Sisa Kode APK Lokal, dan Konfigurasi Endpoint/Token GitHub).

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Minimalis gelap modern (*Sleek Minimal Dark Studio*) — bersih, presisi, tanpa elemen dekoratif yang ramai.
  - *Color Palette & Mood*: Menggunakan token warna tema Material 3 gelap yang terpusat: latar belakang gelap pekat (`DarkBackground`), permukaan kartu lembut (`DarkSurface` & `DarkSurfaceVariant`), aksen utama biru sian (`NeonCyan`) untuk kontrol aktif dan fokus, aksen sekunder ungu (`NeonPurple`) dan hijau zamrud (`EmeraldGreen`) untuk status berhasil/aktif.
  - *Typography & Hierarchy*: Memanfaatkan font Inter lokal yang sudah terpasang dengan hierarki tipografi Material 3 yang tegas — judul ringkas, label status berjarak lega, serta ukuran sentuh minimal `48.dp` di layar Compose.
  - *Component Styling & Layout*: Tata letak *edge-to-edge* penuh yang menghormati `WindowInsets.safeDrawing`, kartu bertepi halus dengan elevasi tonal konsisten, serta panel floating kosong dengan tampilan *empty state* yang rapi (ikon + pesan bahwa panel siap diisi).

- **Interactive Feedback & Motion**:
  - Transisi halus berbasis `AnimatedVisibility` pada banner/pengaturan pembaruan dan status layanan.
  - Umpan balik sentuh yang jelas (*ripple* Material 3 di aplikasi utama, serta konfirmasi klik ganda atau umpan balik Toast ringkas pada tombol tutup bubble agar tidak tertutup tidak sengaja).

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Menjaga Kemurnian Floating Shell Tanpa Dependensi Berat**:
  - *Chosen Approach*: Membangun ulang layanan Floating menjadi satu kesatuan ramping yang hanya bergantung pada `WindowManager`, notifikasi Foreground Service, dan penyimpanan preferensi lokal sederhana (`SharedPreferences`), menghapus seluruh ketergantungan ke repositori media, Xposed, dan engine game.
  - *Why*: Menjamin layanan *overlay* super ringan, bebas *memory leak*, tidak memerlukan izin kamera/VPN/aksesibilitas, dan mudah dikembangkan di masa depan.
  - *Alternatives Considered*: Mempertahankan sebagian kelas model/repository lama dengan fitur dimatikan; ditolak karena tetap meninggalkan kode mati (*dead code*) dan membebani ukuran APK.

- **Decision 2 — Membebaskan Updater GitHub dari Gerbang Lisensi/VIP**:
  - *Chosen Approach*: Menghapus pengecekan lisensi/VIP dari pengelola pembaruan APK GitHub sehingga pengecekan rilis dan pengaturan repo/PAT tetap bisa diuji dan digunakan langsung pada template bersih ini.
  - *Why*: Karena sistem lisensi dan *decoy* ikut dihapus sesuai instruksi, mempertahankan gerbang lisensi akan membuat fitur GitHub di dalam APK terkunci mati (*dead-end*).
  - *Alternatives Considered*: Menghapus UI GitHub Updater dari APK seluruhnya; ditolak karena pengguna meminta mempertahankan "logika github" selain `update.sh`.

- **Decision 3 — Perampingan Menu `update.sh` & Izin Android Minimal**:
  - *Chosen Approach*: Menyederhanakan menu interaktif `update.sh` menjadi 5 opsi inti GitHub CI/CD, menghapus modul Python/Node non-GitHub serta aset berukuran besar (dataset teks Al-Qur'an, daftar blacklist/decoy), dan memangkas izin di Manifest hingga hanya izin dasar (`INTERNET`, `ACCESS_NETWORK_STATE`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, dan `REQUEST_INSTALL_PACKAGES`).
  - *Why*: Mempercepat proses *zip-push-build*, mencegah kebingungan akibat menu mati di Termux, dan memastikan aplikasi tetap bersih serta patuh kebijakan izin minimal Android.
  - *Alternatives Considered*: Membiarkan izin dan aset lama tetap ada di proyek; ditolak karena bertentangan dengan permintaan membersihkan semua fitur.

- **Decision 4 — Mempertahankan Identitas Teknis Build (`applicationId` & Keystore)**:
  - *Chosen Approach*: Memperbarui label nama tampilan aplikasi menjadi `asisten copy` di seluruh konfigurasi pengguna, namun mempertahankan `applicationId` dan *canonical keystore* yang terikat di workflow GitHub Actions dan `update.sh`.
  - *Why*: Menjaga alur instalasi pembaruan APK yang sudah ada tetap mulus tanpa error konflik tanda tangan Android, sekaligus memastikan build otomatis di GitHub Actions berjalan tanpa konfigurasi ulang.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                         PIPELINE TERMUX & GITHUB                        │
│                                                                         │
│  [ZIP Proyek] ──▶ [update.sh] ──▶ [Git Push] ──▶ [GitHub Actions CI]    │
│                        │                                │               │
│                        ├──▶ [apk_downloader] ◀──────────┤ (.apk / Rel)  │
│                        └──▶ [release_manager] ◀─────────┘               │
└─────────────────────────────────────────────────────────┬───────────────┘
                                                          │ Rilis APK
                                                          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      APLIKASI ANDROID (asisten copy)                    │
│                                                                         │
│  ┌───────────────────────────┐      ┌────────────────────────────────┐  │
│  │        MainActivity       │      │     AppUpdateManager (GitHub)  │  │
│  │  • Status & Izin Overlay  │─────▶│  • Cek Rilis / Artifact API    │  │
│  │  • Saklar Floating        │      │  • Konfigurasi Repo & PAT      │  │
│  │  • Kartu & Dialog Update  │      │  • Unduh & Installer APK       │  │
│  └─────────────┬─────────────┘      └────────────────────────────────┘  │
│                │ Start / Stop                                           │
│                ▼                                                        │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                FloatingControlService (Foreground)                │  │
│  │                                                                   │  │
│  │  ┌──────────────────────────┐    ┌─────────────────────────────┐  │  │
│  │  │   Floating Bubble Bar    │◀──▶│   Expanded Floating Panel   │  │  │
│  │  │  (Drag, Buka App, Expand,│    │  (Header, Konten Kosong     │  │  │
│  │  │   Tutup Layanan)         │    │   Bersih, Minimize, Tutup)  │  │  │
│  │  └──────────────────────────┘    └─────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - *Preferensi Floating (`SharedPreferences`)*: Menyimpan status aktif/nonaktif auto-start Floating dan posisi koordinat terakhir bubble agar posisi bertahan saat diciutkan atau dibuka ulang.
  - *State Updater GitHub (`StateFlow`)*: Menyimpan `repoSlug`, `githubToken`, status pengecekan, informasi rilis terbaru, dan progres unduhan APK secara reaktif di memori dan `SharedPreferences`.
  - *State Workflow Pipeline (Termux)*: Menyimpan konfigurasi endpoint repositori dan token Git secara terisolasi di konfigurasi Git/Termux lokal tanpa ikut terunggah ke GitHub.

- **Interactive Component & State Mapping**:
  - *Tombol Izin Overlay*: Memeriksa `Settings.canDrawOverlays`; jika belum diizinkan, membuka halaman pengaturan izin overlay sistem dan menyegarkan status otomatis saat kembali ke aplikasi.
  - *Saklar Floating*: Memulai atau menghentikan `FloatingControlService` sebagai *Foreground Service* dengan tipe `specialUse`; sinkron dua arah sehingga menutup layanan dari gelembung/panel mengambang langsung memperbarui status saklar di layar utama.
  - *Bubble & Panel Mengambang*: Mendukung gestur geser (*drag*) mulus dengan pembatasan batas layar (*clamping*), tombol pintas kembali ke `MainActivity`, tombol alih (*toggle*) antara mode gelembung dan mode panel kosong, serta tombol tutup aman dengan konfirmasi ganda.
  - *Kontrol Pembaruan GitHub*: Menangani pengujian koneksi repositori publik/privat, pengunduhan APK dari GitHub Releases maupun Actions Artifacts (termasuk penanganan *redirect* aman), dan pemicu installer Android melalui `FileProvider`.
  - *CLI `update.sh`*: Menangani *self-update* dari ZIP terbaru, sinkronisasi versi `version.properties`, *push* bersih ke remote GitHub tanpa membocorkan direktori alat lokal, pemantauan status workflow via GitHub API, serta pengelolaan daftar/hapus rilis.
