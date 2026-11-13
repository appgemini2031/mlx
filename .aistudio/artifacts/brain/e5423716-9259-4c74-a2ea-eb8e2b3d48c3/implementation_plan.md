# Rencana Implementasi: Resolusi Live Stream YouTube Web ke Kamera Virtual (2 Device)

## 1. Analisis Masalah YouTube Live pada Web Sumber
Halaman `https://makkahlive.net/makkahlive.aspx` memiliki 3 tombol pilihan saluran:
- **Stream 1**: YouTube Live feed (Masjidil Haram)
- **Stream 2**: YouTube Live feed (Saudi Quran TV)
- **Stream 3**: YouTube Live feed (Holy Kaaba / Masjid an-Nabawi)

Android `MediaPlayer` pada `GlVideoRenderer` membutuhkan stream video langsung berkas **HLS (`.m3u8`)**, bukan iframe web atau URL watch YouTube biasa.

## 2. Solusi 2 Perangkat yang Diimplementasikan
1. **Device 1 (Termux CLI):**
   - Mendeteksi 3 tombol stream di web `makkahlive.net` (`eC4LfEVxvKg`, `ySmDHi76itE`, `Hr7uPQSmHzM`).
   - Resolusi otomatis menggunakan `yt-dlp` jika tersedia.
   - Menyediakan saluran satelit resmi 24/7 Saudi Quran TV HLS (`http://m.live.net.sa:1935/live/quran/playlist.m3u8` & mirror Akamai) yang memancarkan tayangan live Masjidil Haram yang sama secara stabil tanpa buffering atau gangguan bot check.
   - Pilihan interaktif di CLI: Opsi 2 untuk beralih Stream 1, Stream 2, atau Stream 3 secara langsung.

2. **Device 2 (Aplikasi Android TikTok Live):**
   - Menambahkan tombol cepat `🕋 Stream 1`, `🕋 Stream 2`, dan `🕌 Stream 3` pada kartu *Live Stream Web Relay*.
   - Sinkronisasi instan via SharedPreferences, ContentProvider, dan HookBroadcastReceiver ke `GlVideoRenderer` kamera virtual.
