# Rencana Implementasi: Fitur Decoy Mode (Sabotase Halus & Tolak Halus) pada Generator Kunci CuanCame

## Gambaran Umum & Tujuan
Fitur ini dirancang untuk menyelesaikan dilema sosial pengguna yang "tidak enakan menolak" saat teman, kerabat, atau orang lain meminta/meminjam APK CuanCame. 

Tujuan utama:
1. **Melindungi Rahasia Dapur Dagang & Privasi:** Mencegah trik live streaming Anda ditiru atau disalahgunakan orang lain (termasuk potensi penipuan/scamming atas nama Anda).
2. **Penolakan Alami Tanpa Konflik Sosial:** Orang lain tetap diberikan APK dan Kunci Lisensi resmi, namun kunci tersebut secara tersembunyi disetel ke **Decoy Mode**.
3. **Efek Sabotase yang Terlihat Alami:** Di HP mereka, APK akan memperlihatkan gejala seolah-olah HP merekalah yang "kentang", RAM tidak kuat, atau GPU tidak kompatibel (video patah-patah, audio stutter, dan auto-disconnect berkala), sehingga mereka menyerah dengan sendirinya tanpa mencurigai Anda.

---

## 1. Arsitektur & Perubahan Komponen

### A. Generator Lisensi Termux (`update.sh`)
* Menambahkan opsi tersembunyi saat generate kunci:
  - **Tipe 1: Kunci Asli / Owner (Full Performance):** 30–60 FPS, Zero Freeze, Auto-Rocket aktif, stabil tanpa batas.
  - **Tipe 2: Kunci Decoy / Sabotase Halus (Untuk Orang Lain):**
    - `[A] HP Berat / Kentang (Lag Patah-Patah):` Frame rate dijatuhkan sengaja ke 8–12 FPS, mikro-jeda audio.
    - `[B] Simulasi Peringatan Akun (Risk Alert):` Menampilkan popup peringatan keamanan server palsu.
    - `[C] Auto-Exit / Mental Berkala:` Siaran live menutup sendiri tiap 3–5 menit dengan dalih out of memory.
    - `[D] Mode Combo (Rekomendasi):` Kombinasi alami (menit 1 lancar, menit 2 mulai ngelag, menit 4 mental keluar sendiri).
* Payload lisensi ECDSA: Menyematkan flag mode (`DECOY_LAG`, `DECOY_RISK`, `DECOY_EXIT`, `DECOY_COMBO`) pada parameter `plan_str` tanpa mengubah struktur kriptografi signature.

### B. Module Decoy Engine di APK (`LicenseManager.kt` & `VcamModels.kt`)
* `LicenseInfo` mengekstrak atribut `decoyType`:
  - `isDecoyLicense: Boolean`
  - `decoyProfile: DecoyProfile (NONE, LAG_STUTTER, WARNING_ALERT, AUTO_EXIT, COMBO)`
* Di layar Aktivasi APK orang lain: Tetap menampilkan pesan sukses resmi `"🎉 CuanCame PRO Berhasil Diaktivasi!"` sehingga peminjam merasa mendapatkan kunci asli.

### C. Eksekusi Efek di Hook (`GlVideoRenderer.kt` & `MainHook.kt`)
* **Simulasi Lag Patah-Patah:**
  - Menyelipkan jeda render buatan (`Thread.sleep(60-90ms)` atau drop interval) sehingga video terlihat patah-patah (8–12 FPS).
  - Audio hook sesekali melompati 10–20ms buffer suara untuk memberi efek suara tersendat alami.
* **Simulasi Mental / Exit:**
  - Timer tersembunyi (3–5 menit): Secara halus memicu `Activity.finish()` atau simulasi dialog "Sistem Android: Memori RAM tidak mencukupi untuk memproses kamera".

---

## 2. Rincian File yang Akan Dimodifikasi

1. **`update.sh`**:
   - Menambahkan menu pilihan tipe kunci (1: Asli Full Speed vs 2: Kunci Decoy/Sabotase).
   - Menghasilkan token yang dienkripsi dan ditandatangani dengan private key EC master.
2. **`app/src/main/java/com/vcam/model/VcamModels.kt`**:
   - Menambahkan enum `DecoyModeProfile` dan properti `decoyMode` pada `LicenseInfo` dan `VcamConfig`.
3. **`app/src/main/java/com/vcam/license/LicenseManager.kt`**:
   - Membaca dan memvalidasi `planName` untuk mendeteksi profil Decoy secara aman.
   - Menyimpan status Decoy ke SharedPreferences internal dan mengabarkannya ke `VcamRepository`.
4. **`app/src/main/java/com/vcam/GlVideoRenderer.kt`**:
   - Menerapkan efek drop frame dan simulasi lag jika profil Decoy aktif.
5. **`app/src/main/java/com/vcam/MainHook.kt`**:
   - Menerapkan simulasi dialog memori penuh / auto-exit berkala untuk profil Decoy.
6. **`app/src/test/java/com/vcam/DecoyLicenseTest.kt`**:
   - Unit test verifikasi bahwa kunci Decoy tervalidasi sah secara kriptografi, namun flag sabotase terbaca dengan presisi.

---

## 3. Langkah Verifikasi & Pengujian
- [ ] Pengujian kriptografi: Memastikan kunci Decoy tetap valid ditandatangani openssl ECDSA secp256r1.
- [ ] Pengujian aktivasi: Layar aktivasi tetap memberi respons sukses warna hijau tanpa tulisan "Decoy" agar peminjam tidak curiga.
- [ ] Pengujian simulasi bug: Memastikan efek patah-patah dan simulasi crash bekerja alami tanpa merusak APK untuk akun asli Anda.
- [ ] Verifikasi build aplikasi dengan `compile_applet` dan unit test otomatis.
