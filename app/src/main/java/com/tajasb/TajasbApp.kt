package com.tajasb

import android.app.Application
import com.tajasb.debug.CrashShieldManager

/**
 * TajasbApp: Kelas Application utama untuk CuanCame / TAJASB.
 * Menginisialisasi CrashShieldManager sejak awal proses aplikasi dijalankan,
 * sebelum Activity atau Service apapun dimulai.
 */
class TajasbApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Pasang proteksi Crash Shield secara global
        CrashShieldManager.install(this)
    }
}
