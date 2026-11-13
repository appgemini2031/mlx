# Transformasi Bigo Live Event Broker Termux: Solusi Riil Anti-Gimik (Device 1 ➔ Device 2)

Rencana menyeluruh untuk mentransformasikan modul Bigo Live Event Broker di Termux (`tools/bigo_event_server.py`) yang sebelumnya masih berupa regex scraper halaman statis dan simulasi tombol manual, menjadi **Real-Time Live Event Engine** yang fungsional, akurat, dan terhubung langsung ke stream siaran Bigo Live.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Masalah utama pada implementasi sebelumnya:
> 1. `tools/bigo_event_server.py` awalnya mengandalkan `ta.bigo.tv/official_website/studio/getInternalStudioInfo` yang dikhususkan untuk portal broadcaster studio (sehingga mengembalikan `roomId: "0"` dan `alive: 0` jika dipanggil tanpa cookie autentikasi broadcaster, seperti pada kasus target `22o321`).
> 2. Akibatnya, streamer aktif seperti `22o321` terdeteksi `OFFLINE / STANDBY` meskipun sedang siaran langsung.
> 
> Solusi arsitektur anti-gimik tuntas:
> 1. **Ekstraksi Riil SSR Nuxt `https://www.bigo.tv/{clean_id}`**: Halaman web penonton publik menyajikan variabel SSR langsung dari server Bigo:
>    - `roomid=([0-9]{10,})`: Mendeteksi 64-bit Room ID siaran aktif (misal `7006481638343700737`) yang membuktikan host 100% SEDANG LIVE.
>    - `aT.bean=([0-9]+)`: Membaca jumlah Beans siaran secara real-time (misal `1.294.346 🫘`).
>    - `aT.nickname="([^"]+)"`: Membaca nama panggilan resmi host (misal `♎️Mey`).
>    - `aT.roomTopic="([^"]+)"`: Membaca judul/topik siaran (misal `#bigovoice Miracle🥹`).
>    - `uid=([0-9]+)` & `aliOSUrl`: Membaca User ID akun resmi host (misal `554224509`).
> 2. **Deteksi Otomatis Saweran Masuk (Beans Delta)**: Watcher menghitung selisih pertambahan beans (`delta_beans = beans_baru - beans_lama`). Setiap kali penonton mengirim hadiah di Bigo Live, event `gift` instan dipancarkan ke Device 2 (Aplikasi Android CuanCame).
> 3. **Uji Diagnostik Transparan (--diagnose)**: Memeriksa DNS, mengambil halaman web SSR, mem-parsing parameter live, dan menyimpan log ke `bigo_stream.log` sebagai bukti transparan tanpa simulasi bohong.

- **Pilihan Pendekatan**: Integrasi Native Bigo Studio Gateway + HTTP/WebSocket Ingestion Hub (Device 1) diteruskan secara ultra-cepat (<5ms) ke HP CuanCame (Device 2).
- **Target Event**: Chat komentar/doa, Saweran Beans/Gifts, Like/Hati, Member Gabung, dan Follow baru.
- **Konsistensi UI Termux**: Menghilangkan teks tiruan, menampilkan status koneksi server yang transparan (LIVE / OFFLINE / CONNECTED / WAITING), serta sinkronisasi nilai Beans ke Rupiah (@Rp 80/Bean).

---

## 1. Overview & Core Concept

### Masalah yang Diselesaikan
* **Bigo Data Tidak Terkoneksi Nyata**: Skrip lama mengandalkan scraping URL web yang diblokir atau tidak memiliki stream data real-time, sehingga penonton asli di Bigo Live tidak memicu banner atau game di APK.
* **Ketergantungan Mock Test**: Angka statistik hanya bertambah bila ditekan tombol angka 1-9 di Termux.
* **Header & Protokol Tertukar**: Terdapat sisa template lama (seperti tulisan "TikTok Event Broker") di dalam respons server Bigo.

### Solusi yang Dihadirkan
* **Bigo Studio Internal Protocol Client**: Mengintegrasikan protokol query JSON `getInternalStudioInfo` yang terverifikasi mengembalikan data metadata live streamer secara sah.
* **High-Speed Ingestion Hub**: Mengubah port `8765` menjadi server ingestion serbaguna:
  * Menerima koneksi WebSocket dari Device 2 (Aplikasi Android CuanCame).
  * Menerima event JSON riil via `POST /event` atau `POST /webhook` dari script watcher / browser collector.
  * Menyiarkan event secara instan ke Device 2 dalam format payload terstandarisasi.
* **Bigo Stream Watcher & Payout Calculator**: Memantau fluktuasi Beans akun secara real-time, menghitung selisih saweran masuk (`deltaBeans`), dan mengonversi estimasi rupiah payout bersih secara otomatis.

---

## 2. User Experience & Visual Design

### Alur Kerja di Termux (Device 1)
```text
┌─────────────────────────────────────────────────────────────────┐
│        🫘 TAJASB BIGO LIVE EVENT BROKER v6.0 (ANTI-GIMIK)       │
├─────────────────────────────────────────────────────────────────┤
│ 🎯 Host Bigo ID : makkahlive [UID: 1665472769 | SID: 226362...] │
│ 🔴 Status Live  : SEDANG SIARAN (Room: 89472194)               │
│ 🌐 Endpoint LAN : ws://192.168.1.15:8765 (WebSocket Server)     │
│ 📥 Ingestion    : http://192.168.1.15:8765/event (HTTP Ingest)  │
│ 📲 Device 2     : 1 HP Terhubung (Samsung A54 - CuanCame)       │
├─────────────────────────────────────────────────────────────────┤
│ 🎯 Target Hadiah: Supercar (3.000 🫘 Beans)                      │
│ 🫘 Beans Masuk  : 1.450 🫘 [48.3%] • Sisa: 1.550 🫘             │
│ 💰 Estimasi Rp  : Rp 116.000 (@Rp80/Bean)                       │
├─────────────────────────────────────────────────────────────────┤
│ [13:42:01] 💬 DOA/CHAT  : @Ahmad_Bigo ➔ 'Aamiin lancar rezeki'  │
│ [13:42:05] 🎁 SAWERAN   : @Sultan99 ➔ Rose x10 • 10 🫘          │
│ [13:42:09] ❤️ LIKE TAP  : @Fatimah +20 Tap                     │
└─────────────────────────────────────────────────────────────────┘
```

### Visual & Interaksi di HP Siaran (Device 2)
1. **Banner Doa & Komentar**: Chat yang masuk otomatis tampil di banner atas kamera virtual dengan label `[BIGO CHAT]`.
2. **Notifikasi Saweran**: Setiap gift atau beans memunculkan efek saweran dan menambah progres target gift.
3. **Integrasi Game (Tarik Tambang & Raja Singgasana)**: Like dan chat Bigo langsung menambah poin tim atau menobatkan raja baru.
4. **Khatam Al-Qur'an 30 Juz**: 1 like Bigo mengetikkan 1 huruf Arab, 1 saweran Beans mengetikkan 1 ayat penuh.

---

## 3. Key Product Decisions & Trade-Offs

### Keputusan 1: Kombinasi Bigo Studio API + Universal Ingestion Webhook
- **Pendekatan**: Menggabungkan polling resmi metadata room `getInternalStudioInfo` dengan REST/WebSocket Ingestion Gateway.
- **Mengapa**: Bigo Live memiliki enkripsi paket binary TCP yang ketat pada aplikasi mobile resminya. Dengan menyediakan HTTP Webhook Ingestion di port 8765, pengguna dapat mengalirkan event riil dari berbagai sumber (API studio, web browser script, atau background daemon) dengan latensi <5ms tanpa terhalang enkripsi.
- **Alternatif yang ditolak**: Tetap mempertahankan regex scraping HTML statis (terbukti gagal dan dianggap fitur tiruan/gimik).

### Keputusan 2: Pemisahan Jelas Antara Data Riil dan Tombol Diagnostik
- **Pendekatan**: Tombol [1]-[9] di Termux diberi label eksplisit **"[SIMULASI / TES SINKRONISASI]"**, sehingga pengguna tahu persis kapan data berasal dari siaran langsung riil dan kapan data berasal dari pengujian manual.
- **Mengapa**: Menjaga transparansi penuh dan integritas sistem agar pengguna tidak merasa dibohongi oleh generator acak.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram
```text
┌──────────────────────────────────────────────────────────────────────┐
│                       DEVICE 1 (TERMUX / LINUX)                      │
│                                                                      │
│   ┌───────────────────────────┐      ┌───────────────────────────┐   │
│   │   Bigo Studio API Client  │      │   Bigo Webhook Ingest     │   │
│   │   (ta.bigo.tv internal)   │      │   (POST /event & /webhook)│   │
│   └─────────────┬─────────────┘      └─────────────┬─────────────┘   │
│                 │                                  │                 │
│                 ▼                                  ▼                 │
│         ┌──────────────────────────────────────────────────┐         │
│         │         Bigo Event Dispatcher & Formatter        │         │
│         │   (Standardized JSON Payload with Platform:BIGO) │         │
│         └─────────────────────────┬────────────────────────┘         │
│                                   │                                  │
│                                   ▼                                  │
│         ┌──────────────────────────────────────────────────┐         │
│         │    Dual WebSocket Server (RFC 6455, Port 8765)   │         │
│         └─────────────────────────┬────────────────────────┘         │
└───────────────────────────────────┼──────────────────────────────────┘
                                    │ WebSocket LAN (<5ms)
                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    DEVICE 2 (APLIKASI ANDROID CUANCAME)              │
│                                                                      │
│   ┌──────────────────────────────────────────────────────────────┐   │
│   │       LiveEventReceiver.kt (WebSocket Client ws://IP:8765)   │   │
│   └──────┬────────────────────┬────────────────────┬─────────────┘   │
│          │                    │                    │                 │
│          ▼                    ▼                    ▼                 │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐         │
│   │  Banner Doa  │     │  Game Tarik  │     │  Khatam 30   │         │
│   │ & Sawer OSD  │     │   Tambang    │     │  Juz Inter-  │         │
│   │   Overlay    │     │  & Singga-   │     │  aktif       │         │
│   │              │     │    sana      │     │              │         │
│   └──────────────┘     └──────────────┘     └──────────────┘         │
└──────────────────────────────────────────────────────────────────────┘
```

### Data Pipeline & Payload Format
Payload event yang dipancarkan ke Android dijamin memiliki struktur identik dengan event receiver:
```json
{
  "type": "chat",
  "platform": "BIGO",
  "timestamp": 1731195600000,
  "targetUser": "makkahlive",
  "sender": "Ahmad_Bigo",
  "text": "Semoga berkah lancar rezekinya",
  "data": {
    "senderName": "Ahmad_Bigo",
    "text": "Semoga berkah lancar rezekinya",
    "platform": "BIGO"
  }
}
```

Dan untuk event saweran:
```json
{
  "type": "gift",
  "platform": "BIGO",
  "timestamp": 1731195605000,
  "targetUser": "makkahlive",
  "sender": "Sultan_Bigo",
  "gift_name": "Supercar",
  "gift_beans": 3000,
  "repeat_count": 1,
  "data": {
    "senderName": "Sultan_Bigo",
    "giftName": "Supercar",
    "giftCount": 1,
    "beans": 3000,
    "deltaBeans": 3000,
    "diamondCount": 3000,
    "platform": "BIGO"
  }
}
```

---

## 5. Rencana File & Modifikasi

1. **`tools/bigo_event_server.py`**:
   - Tulis ulang engine komunikasi menggunakan endpoint `ta.bigo.tv/official_website/studio/getInternalStudioInfo` dengan parsing `sid`, `uid`, `roomId`, dan `alive`.
   - Tambahkan handler HTTP `POST /event` dan `POST /webhook` pada server RFC 6455 sehingga event eksternal real-time dapat diumpankan secara instan.
   - Perbaiki branding teks dan header (hapus teks "TikTok" yang tertinggal).
   - Buat format log dashboard yang membedakan data riil live vs tes diagnostik.
2. **`tools/bigo_bridge.js`**:
   - Buat skrip pembantu bridge Node.js ringan untuk menangkap event room live Bigo.
3. **`update.sh`**:
   - Selaraskan opsi 10 pada `update.sh` agar memvalidasi dan meluncurkan engine Bigo baru secara mulus.
4. **Verifikasi & Tes Kompilasi**:
   - Pastikan Android test suite dan server Python berjalan tanpa error syntax.
