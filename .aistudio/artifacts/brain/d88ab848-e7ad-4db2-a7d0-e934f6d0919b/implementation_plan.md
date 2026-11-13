# Rencana Implementasi: Fitur Ganti Akun Firebase (Termux CLI & Bot Telegram)

Fitur ini menyediakan kemampuan mengganti atau menambah akun Firebase Firestore langsung dari **Termux CLI** (`update.sh`) dan **Bot Telegram Admin** (`tools/telegram_bot.py`), sehingga pengguna tidak perlu mengubah kode program secara manual saat kuota habis atau ingin beralih project.

---

## 1. Analisis Kebutuhan & Titik Integrasi

Saat ini, konfigurasi Firebase tersebar pada beberapa komponen:
1. `firebase-applet-config.json` (root project, dibaca oleh backend & bot).
2. `app/google-services.json` (konfigurasi Android Google Services SDK).
3. `app/src/main/res/values/firebase_applet_config.xml` (menyimpan `firestore_database_id`).
4. `tools/telegram_bot.py` (membaca pool node Firestore dan menampilkan kuota Spark Plan).
5. `app/src/main/java/com/tajasb/remote/RemoteTelemetryManager.kt` (klien Firestore di aplikasi Android yang mendukung multi-node).
6. `firebase_nodes.json` (pool daftar project/database cadangan untuk auto-switch kuota).

---

## 2. Rencana Perubahan

### A. Modul Shared Helper Baru (`tools/firebase_account_manager.py`)
Membuat modul utilitas independen berkecepatan tinggi yang dapat dipanggil dari Bash (`update.sh`) maupun Python (`telegram_bot.py`):
- `load_current_firebase_config()`: Membaca konfigurasi aktif dari `firebase-applet-config.json` dan `app/google-services.json`.
- `parse_google_services_json(filepath_or_content)`: Mengekstrak otomatis `projectId`, `apiKey`, `appId`, `projectNumber`, dan `clientId` dari file `google-services.json`.
- `apply_firebase_account(project_id, api_key, database_id, app_id, project_number, client_id)`:
  - Memperbarui `firebase-applet-config.json`.
  - Memperbarui `app/google-services.json` (jika direktori `app/` ada).
  - Memperbarui `app/src/main/res/values/firebase_applet_config.xml`.
  - Menyimpan cadangan akun sebelumnya ke `firebase_nodes.json` (multi-node pool) agar tidak hilang.
- `test_firestore_connection(project_id, database_id, api_key)`: Menguji coba query langsung ke REST API Firestore (`projects/{p}/databases/{d}/documents`) untuk memvalidasi kredensial sebelum disimpan.

### B. Integrasi ke Menu Termux CLI (`update.sh`)
- Menambahkan opsi menu baru di `update.sh`:
  - `11) 🔥 Ganti / Tambah Akun Firebase (Project ID & google-services.json)`
- Menyediakan sub-menu interaktif:
  1. *Input Kredensial Langsung* (Project ID, API Key, Database ID).
  2. *Scan & Terapkan dari Berkas `google-services.json`* (mencari di Download/SDCard/Termux).
  3. *Uji Koneksi & Cek Kuota Akun Aktif*.
  4. *Kelola Pool Multi-Project* (lihat dan hapus node cadangan di `firebase_nodes.json`).

### C. Integrasi ke Bot Telegram Admin (`tools/telegram_bot.py`)
- Menambahkan tombol menu di Telegram:
  - Menambahkan menu `🔥 Kelola Akun Firebase` di `MAIN_KEYBOARD` atau di dalam submenu `📊 Limit Firebase`.
- Fitur Bot Telegram:
  1. **Upload Dokumen**: Admin cukup mengirim file `google-services.json` langsung ke chat Telegram bot! Bot otomatis mengekstrak `projectId`, `apiKey`, `appId`, menguji koneksi, lalu menerapkannya.
  2. **Ganti via Teks**: Command `/setfirebase <project_id> <api_key> [database_id]` atau klik tombol inline `Ganti Project ID`, `Ganti API Key`, `Ganti Database ID`.
  3. **Multi-Project Auto-Switch**: Tombol `➕ Tambah Akun Cadangan ke Pool` untuk rotasi kuota otomatis.

---

## 3. Rencana Pengujian & Verifikasi
1. Menjalankan modul CLI `tools/firebase_account_manager.py --test` untuk verifikasi parsing dan penyimpanan.
2. Menjalankan unit test untuk memastikan file konfigurasi terupdate dengan benar dan valid JSON/XML.
3. Menjalankan `compile_applet` untuk memastikan tidak ada konflik build pada Android dan bot.
