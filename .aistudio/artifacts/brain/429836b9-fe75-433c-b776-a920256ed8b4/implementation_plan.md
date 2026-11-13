# Rencana Implementasi: Pembaruan APK Otomatis Repositori Privat (Alur Termux) - SELESAI

Perbaikan logika rilis dan pembaruan APK otomatis telah diimplementasikan sesuai alur kerja Termux (`tools/apk_downloader.py` & `update.sh`):

1. **Proteksi Akses Perangkat Khusus (Owner / VIP)**:
   - Gerbang keamanan `isAuthorizedForUpdate()` berbasis `LicenseManager.isVipDecoyBypassed()`.
   - Pada perangkat klien umum (bukan perangkat khusus Owner), fitur pembaruan otomatis (top banner, header update chip, kartu update tab sistem, background update check, dan dialog pembaruan) **sama sekali tidak muncul dan tidak pernah melakukan request jaringan**.

2. **Dukungan Repositori Privat via GitHub Token (PAT)**:
   - Disediakan input Personal Access Token (PAT) dengan penyamaran sandi (toggle mata), tombol simpan, dan tombol hapus token di dialog dan kartu pembaruan APK (hanya pada perangkat terotorisasi).
   - Query rilis GitHub (`releases/latest` & `releases?per_page=5`) mengirimkan header `Authorization: Bearer <token>` dan `Accept: application/vnd.github+json`.
   - Mengunduh APK aset binary privat via GitHub Releases Asset API (`https://api.github.com/repos/.../releases/assets/{id}`) dengan header `Accept: application/octet-stream` dan `Authorization: Bearer <token>`.
   - Mengimplementasikan `SafeRedirectHandler` (mirip alur Termux `apk_downloader.py`), yang secara otomatis melepas header `Authorization` saat pengalihan URL (HTTP 302 redirect) diarahkan ke AWS S3 / CDN pihak ketiga agar unduhan tidak ditolak (anti-400 / anti-404).

3. **Status Kompilasi**:
   - Berhasil dikompilasi dengan status sukses tanpa kesalahan (`BUILD SUCCESSFUL`).
