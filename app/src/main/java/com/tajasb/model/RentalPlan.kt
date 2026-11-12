package com.tajasb.model

/**
 * Model Data Paket Sewa Dinamis Tajasb PRO.
 * Mendukung kategori JAM, BULAN, TAHUN, dan SELAMANYA.
 * Nilai paket dan harga dapat dikonfigurasi dinamis via Bot Telegram dan tersinkronisasi di Firestore.
 */
data class RentalPlan(
    val id: String,
    val category: String, // "HOURLY", "MONTHLY", "YEARLY", "LIFETIME"
    val durationValue: Int, // misal 1, 3, 6, 12 (jam); 1, 3, 6 (bulan); 1, 2 (tahun); 0 (lifetime)
    val durationUnit: String, // "HOURS", "MONTHS", "YEARS", "LIFETIME"
    val label: String,
    val price: Int,
    val originalPrice: Int = 0,
    val badge: String = "",
    val description: String = "",
    val isActive: Boolean = true
) {
    val discountPercent: Int
        get() = if (originalPrice > price && originalPrice > 0) {
            (((originalPrice - price).toDouble() / originalPrice) * 100).toInt()
        } else 0
}

/**
 * Model Kupon Promo Diskon.
 */
data class PromoCode(
    val code: String,
    val discountPercent: Int = 0,
    val discountAmount: Int = 0,
    val maxUsage: Int = 100,
    val usedCount: Int = 0,
    val isActive: Boolean = true
) {
    fun calculateDiscount(basePrice: Int): Int {
        if (!isActive) return 0
        if (discountPercent > 0) {
            return (basePrice * (discountPercent.coerceIn(0, 100) / 100.0)).toInt()
        }
        if (discountAmount > 0) {
            return discountAmount.coerceAtMost(basePrice)
        }
        return 0
    }
}

/**
 * Model Transaksi Tripay QRIS.
 */
data class TripayTransaction(
    val reference: String,
    val merchantRef: String,
    val paymentMethod: String = "QRIS",
    val amount: Int,
    val status: String, // "UNPAID", "PAID", "EXPIRED", "FAILED"
    val qrUrl: String,
    val qrString: String = "",
    val checkoutUrl: String = "",
    val expiredTime: Long = 0L,
    val planId: String = "",
    val planLabel: String = "Paket Sewa",
    val category: String = "HOURLY",
    val durationValue: Int = 1,
    val durationUnit: String = "HOURS",
    val deviceId: String = "",
    val licenseKey: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
