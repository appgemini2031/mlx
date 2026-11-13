# Panduan & Rencana Privasi Maksimal Bot Telegram (Invisible Stealth Mode)

Strategi komprehensif untuk memastikan Bot Telegram Admin 100% tidak terlihat publik, tidak dapat ditemukan oleh pihak luar, dan hanya dapat diakses dalam obrolan pribadi 1-on-1 oleh Admin sah.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Keputusan Privasi Terkonfirmasi**:
> - **Ruang Obrolan**: **Chat Pribadi 1-on-1** (Bukan grup, karena grup berisiko membocorkan keberadaan bot kepada anggota lain atau tersimpan di log grup).
> - **Izin Grup**: **Dinonaktifkan Total** (Bot dilarang masuk grup mana pun, baik dari setelan BotFather maupun script).
> - **Profil Bot**: **Disamarkan (Camouflage)** menjadi nama aplikasi utilitas umum yang tidak mencurigakan.

---

## 1. Mengapa Chat Pribadi Jauh Lebih Aman daripada Grup?

```
┌──────────────────────────────────────────────┐       ┌──────────────────────────────────────────────┐
│          RISIKO JIKA MENGGUNAKAN GRUP        │       │    KEUNGGULAN CHAT PRIBADI 1-ON-1 (PILIHAN)   │
├──────────────────────────────────────────────┤       ├──────────────────────────────────────────────┤
│ ❌ ID Bot terlihat oleh semua anggota grup  │       │ ✅ Zero Footprint: Hanya Anda & Bot          │
│ ❌ Bot bisa di-invite / di-mention orang lain│       │ ✅ Di BotFather: /setjoingroups -> DISABLE   │
│ ❌ Log grup bisa diakses pihak lain          │       │ ✅ Script otomatis reject & leave chat grup  │
│ ❌ Tidak bisa menerapkan auto-delete PIN     │       │ ✅ Pesan input PIN dihapus otomatis          │
└──────────────────────────────────────────────┘       └──────────────────────────────────────────────┘
```

---

## 2. Langkah Hardening di @BotFather (5 Menit Selesai)

Langkah-langkah resmi di aplikasi Telegram untuk mengunci privasi bot:

### A. Kunci Bot Agar DILARANG Masuk Grup
1. Buka Telegram dan cari **`@BotFather`**.
2. Ketik perintah: `/mybots` lalu pilih bot Anda.
3. Klik menu **`Bot Settings`** ➔ **`Allow Groups?`** (atau ketik `/setjoingroups`).
4. Pilih **`Turn groups off`** (atau *Disable*).
   * *Hasil:* Siapa pun yang mencoba menambahkan bot Anda ke grup/saluran akan otomatis ditolak oleh server Telegram.

### B. Matikan Mode Inline (Anti-Mention di Obrolan Luar)
1. Di `@BotFather`, ketik: `/setinline`.
2. Pilih bot Anda ➔ Klik **`Turn inline mode off`**.
   * *Hasil:* Bot tidak akan pernah bisa dipanggil atau di-tag (`@namabot`) di obrolan orang lain.

### C. Samarkan Identitas Bot (Camouflage Profile)
Agar bot tidak menarik perhatian orang yang tidak sengaja mencari username:
1. **Ganti Nama Tampilan**:
   * Di BotFather ketik: `/setname`
   * Beri nama yang netral dan umum, contoh:
     * `Personal Task Keeper`
     * `Daily Notes Sync`
     * `Simple Utility Tool`
   * *Hindari kata:* "Cuan", "Came", "VCAM", "Sewa", "Hook", atau "Admin".
2. **Kosongkan Deskripsi & About**:
   * Ketik `/setdescription` ➔ Kosongkan atau isi: `Personal note keeping utility`.
   * Ketik `/setabouttext` ➔ Kosongkan.

---

## 3. Peningkatan Keamanan di Script (`tools/telegram_bot.py`)

Selain setelan di BotFather, script Python akan diperkuat dengan pertahanan tingkat kode:

1. **Auto-Reject & Auto-Leave Group**:
   * Jika ada update yang bertipe selain `private` (`group`, `supergroup`, atau `channel`):
     * Script langsung memanggil API `leaveChat` untuk keluar dari grup tersebut secara otomatis.
     * Tidak membalas pesan apa pun di grup (*Silent Drop*).
2. **Karantina Ketat 1-on-1**:
   * Hanya memproses pesan jika:
     `chat.get("type") == "private"` **DAN** `chat_id == admin_id` **DAN** `PIN terverifikasi`.
3. **Pembersihan Log Terminal**:
   * Di log Termux, pesan PIN tidak pernah ditampilkan atau dicetak ke layar untuk mencegah orang di sebelah Anda mengintip.

---

## 4. Alur Verifikasi
1. Menguji pengiriman pesan dari obrolan pribadi non-admin ➔ Terbukti Silent Drop (100% bungkam).
2. Menguji deteksi grup pada script ➔ Otomatis ditolak dan ditinggalkan.
3. Menjalankan kompilasi proyek dan unit test untuk memastikan stabilitas sistem.
