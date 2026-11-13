# Arsitektur Event Broker 2-Device: TikTok Webcast Listener & LAN WebSocket Receiver

Solusi modern (*best practice*) pengganti sistem screen scanning untuk siaran live (Konsep Mekah & Siaran Langsung): memisahkan peran pembaca event ke **Device 1 (Termux / Server)** via protokol TikTok Webcast resmi dan penerima aksi ke **Device 2 (Aplikasi Android CuanCame)** via WebSocket LAN lokal berkecepatan tinggi, dilengkapi saklar mandiri untuk setiap jenis event siaran.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Keputusan Terkonfirmasi dari Tanya Jawab (Phase 1):**
> - **Platform Sumber Event**: TikTok Live Webcast via username host (`@username`). Tidak ada login akun atau sandi yang diperlukan di Device 1.
> - **Metode Transmisi Antar Perangkat**: WebSocket LAN lokal berkecepatan tinggi tanpa kuota internet (<5ms latency, stabil di jaringan Wi-Fi/Tethering Hotspot yang sama).
> - **Cakupan Event & Saklar**: Seluruh event siaran TikTok didukung penuh dan masing-masing dilengkapi saklar ON/OFF mandiri di aplikasi Android (Device 2).

---

## 1. Overview & Core Concept

### Masalah Sistem Lama (Screen Scanning)
- Menggunakan `AccessibilityService` untuk memindai node teks di layar Android secara terus-menerus.
- Membebani CPU & RAM di HP siaran (rawan crash RAM 2600MB).
- Rentan macet saat TikTok melakukan update tata letak UI, menu penutup muncul, atau komentar bergulir cepat.

### Solusi Baru (Event Broker 2 Perangkat)
- **Device 1 (Termux / Server)**: Menjalankan skrip Python daemon `tools/tiktok_event_server.py`. Skrip ini langsung membuka koneksi Webcast WebSocket ke TikTok Live room berdasarkan `@username`. Saat ada penonton yang mengirim komentar, doa, gift, like, join, atau share, event JSON murni diterima secara instan. Skrip memancarkan event tersebut melalui WebSocket LAN server lokal di port `8765`.
- **Device 2 (Aplikasi Android CuanCame)**: Berfungsi murni sebagai **Penerima Event & Renderer**. Aplikasi terhubung ke WebSocket Device 1, menerima payload JSON terstruktur, dan memicu tampilan banner doa/hadiah/OSD sesuai konfigurasi saklar pengguna tanpa menyentuh accessibility scanning sama sekali.

---

## 2. User Experience & Visual Design

### 2.1 Alur Pengguna (User Flow)
1. **Device 1 (Termux)**:
   - Pengguna menjalankan perintah: `python tools/tiktok_event_server.py @username_tiktok` atau melalui menu interaktif `update.sh`.
   - Layar Termux menampilkan IP lokal (contoh: `ws://192.168.43.1:8765`), status koneksi room TikTok, dan monitor realtime stream event masuk.
2. **Device 2 (Aplikasi Android)**:
   - Pengguna membuka tab baru **"⚡ Live Event Relay"** di Menu Utama atau Floating Control.
   - Aplikasi otomatis mendeteksi atau menyediakan input IP Device 1 dengan tombol `[Hubungkan]`.
   - Indikator status visual berubah menjadi hijau: `🟢 Terhubung ke Device 1 (@username_tiktok)`.
   - Pengguna dapat mengaktifkan/menonaktifkan masing-masing saklar event sesuai kebutuhan siaran.

### 2.2 Desain Antarmuka Panel Saklar Event (Device 2)
Mengikuti pedoman **Material 3 & Clean Modular Design**:
- **Kartu Koneksi Status**:
  - Kolom input IP Server LAN (default otomatis mengingat IP terakhir).
  - Tombol Hubungkan / Putuskan dengan status latensi (ping ms).
- **Daftar Saklar Event (Granular Event Switches)**:
  1. 💬 **Saklar Komentar & Doa**: Menampilkan pesan penonton di banner OSD / Doa Live.
  2. 🎁 **Saklar Hadiah & Saweran (Gift)**: Menampilkan banner apresiasi gift, nama pengirim, dan nilai koin.
  3. ❤️ **Saklar Tap Like**: Menghitung akumulasi like penonton dan ucapan terima kasih berkala.
  4. 👋 **Saklar Sambutan Penonton (Join)**: Menyapa penonton baru yang masuk ke room live.
  5. ➕ **Saklar Follow Baru**: Memberikan ucapan apresiasi kepada penonton yang follow.
  6. 🔄 **Saklar Share Siaran**: Mengapresiasi penonton yang membagikan tautan live.
  7. 👥 **Saklar Statistik Penonton (Viewer Count)**: Menampilkan badge jumlah penonton aktif di layar kamera virtual.
- **Pilihan Aksi Event**:
  - Pilihan durasi tampil banner (2s - 10s).
  - Filter spam dan kata kunci yang diabaikan.

---

## 3. Key Product Decisions & Trade-Offs

### Keputusan 1: TikTok Webcast Protocol di Device 1 vs. Screen Scanning di Device 2
- **Pendekatan Terpilih**: Membaca event via Webcast WebSocket di Device 1 dan mengirim via Local LAN WebSocket ke Device 2.
- **Alasan**:
  - 100% bebas dari perubahan UI TikTok di masa depan (protokol data langsung).
  - Meringankan beban CPU/GPU HP siaran hingga 70%, mencegah overheating dan crash RAM 2600MB.
  - Menangkap 100% gift, komentar, dan like tanpa ada yang terlewat akibat scroll cepat.

### Keputusan 2: WebSocket LAN Lokal vs. Cloud Server / Firebase
- **Pendekatan Terpilih**: WebSocket LAN lokal langsung antar perangkat (Wi-Fi atau Hotspot HP).
- **Alasan**:
  - Latensi super rendah (<5 ms), respons teks dan gift langsung muncul seketika saat penonton berinteraksi.
  - Nol biaya cloud dan tidak memerlukan kuota internet tambahan untuk komunikasi antar perangkat.
  - Mandiri dan aman dari pemblokiran eksternal.

---

## 4. Technical Architecture & Data Strategy

### 4.1 Diagram Sistem Komunikasi

```
┌────────────────────────────────────────────────────────┐
│                   TIKTOK LIVE SERVER                   │
│          (Webcast WebSocket Data Stream)               │
└──────────────────────────┬─────────────────────────────┘
                           │ Webcast Protocol
                           ▼
┌────────────────────────────────────────────────────────┐
│             DEVICE 1: TERMUX / MINI SERVER             │
│      tools/tiktok_event_server.py                      │
│                                                        │
│  ┌────────────────────────┐  ┌──────────────────────┐  │
│  │ TikTok Webcast Client  │─►│ Event Filter & Parser│  │
│  └────────────────────────┘  └──────────┬───────────┘  │
│                                         │              │
│                              ┌──────────▼───────────┐  │
│                              │ Local WebSocket Srv  │  │
│                              │ (Port 8765 / LAN)    │  │
│                              └──────────┬───────────┘  │
└─────────────────────────────────────────┼──────────────┘
                                          │ Local LAN (<5ms)
                                          │ ws://192.168.x.x:8765
                                          ▼
┌────────────────────────────────────────────────────────┐
│             DEVICE 2: ANDROID (CUANCAME)               │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ LiveEventReceiver (WebSocket Client)             │  │
│  └──────────────────────────┬───────────────────────┘  │
│                             │ Dispatched JSON Event    │
│  ┌──────────────────────────▼───────────────────────┐  │
│  │ Granular Event Router (Saklar Filter)            │  │
│  │  - Komentar/Doa [ON/OFF]  - Gift/Sawer  [ON/OFF] │  │
│  │  - Tap Like     [ON/OFF]  - User Join   [ON/OFF] │  │
│  │  - Follow Baru  [ON/OFF]  - Share Live  [ON/OFF] │  │
│  │  - Viewer Count [ON/OFF]                         │  │
│  └──────────────────────────┬───────────────────────┘  │
│                             │ Trigger Active Action    │
│  ┌──────────────────────────▼───────────────────────┐  │
│  │ Action Handlers & Visual Renderers:              │  │
│  │  - OverlayBannerBroadcaster (Doa/Komentar/Gift)  │  │
│  │  - LiveCommentManager / OSD Stats View           │  │
│  │  - Floating Control Status Badge                 │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

### 4.2 Format Pesan JSON Antar Perangkat
Setiap event dikirim dalam format JSON ringkas:
```json
{
  "type": "chat" | "gift" | "like" | "member" | "follow" | "share" | "room_stats",
  "timestamp": 1730973600000,
  "user": {
    "unique_id": "ahmad_hamba_allah",
    "nickname": "Ahmad",
    "avatar_url": "https://..."
  },
  "data": {
    "text": "Semoga keluarga sehat dan berkah selalu",
    "gift_name": "Rose",
    "gift_count": 5,
    "diamond_count": 5,
    "like_count": 12,
    "viewer_count": 842
  }
}
```

### 4.3 Modul yang Akan Dibangun
1. **Device 1 (Termux Script)**:
   - `tools/tiktok_event_server.py`: Server mandiri yang mengelola koneksi Webcast TikTok ke room `@username`, membuat server WebSocket lokal (menggunakan library Python standar `asyncio`/`websockets` atau socket server HTTP/WS ringan zero-dependency), dan menyiarkan event ke Device 2.
   - Integrasi ke `update.sh` agar dapat dijalankan dengan 1 klik dari menu utama Termux.
2. **Device 2 (Android Core & UI)**:
   - `com/tajasb/eventrelay/LiveEventReceiver.kt`: Service/Manager koneksi WebSocket client di background yang mengelola reconnection otomatis jika jaringan Wi-Fi terputus.
   - `com/tajasb/eventrelay/LiveEventModels.kt`: Data class untuk semua jenis event dan konfigurasi saklar.
   - `com/tajasb/eventrelay/LiveEventPreferences.kt`: Penyimpanan state saklar on/off dan riwayat IP server.
   - `com/tajasb/floating/tabs/LiveEventTabBuilder.kt`: UI Tab baru di Floating Window dan Menu Utama dengan saklar modern untuk setiap jenis event siaran.
   - Integrasi ke `OverlayBannerBroadcaster` untuk menampilkan banner doa/hadiah langsung di atas kamera virtual.
