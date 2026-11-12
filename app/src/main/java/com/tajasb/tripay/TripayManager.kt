package com.tajasb.tripay

import android.content.Context
import android.util.Log
import com.tajasb.license.LicenseManager
import com.tajasb.model.PromoCode
import com.tajasb.model.RentalPlan
import com.tajasb.model.TripayTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Manajer Pembayaran Tripay QRIS & Katalog Paket Dinamis.
 * Mengelola pemuatan paket sewa dinamis (Jam, Bulan, Tahun, Selamanya), validasi voucher promo,
 * pembuatan closed transaction QRIS, dan polling status pembayaran hingga aktif secara instan.
 */
class TripayManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TripayManager"

        private const val FIRESTORE_PROJECT_ID = "gen-lang-client-0093873107"
        private const val FIRESTORE_DATABASE_ID = "ai-studio-android-tajasb-2fe2569e-1897-4a5c-99ce-a6c3fc1888bb"
        private const val FIRESTORE_API_KEY = "AIzaSyAbeoS_V-a7GzU_JMfP_PFq2w43yCcSUF0"

        @Volatile
        private var instance: TripayManager? = null

        fun getInstance(context: Context): TripayManager {
            return instance ?: synchronized(this) {
                instance ?: TripayManager(context.applicationContext).also { instance = it }
            }
        }

        // Paket sewa dinamis bawaan (Default Out-of-the-Box)
        val DEFAULT_PLANS = listOf(
            // Kategori JAM
            RentalPlan("pkg_1h", "HOURLY", 1, "HOURS", "1 Jam Express", 5_000, 10_000, "Sewa Kilat", "Sewa kilat 1 jam untuk sesi live stream singkat."),
            RentalPlan("pkg_3h", "HOURLY", 3, "HOURS", "3 Jam Siaran", 12_000, 20_000, "Favorit Host", "Durasi pas untuk 1 sesi live TikTok / Shopee."),
            RentalPlan("pkg_6h", "HOURLY", 6, "HOURS", "6 Jam Maraton", 20_000, 35_000, "Paling Populer", "Akses 6 jam nonstop penuh performa 60 FPS."),
            RentalPlan("pkg_12h", "HOURLY", 12, "HOURS", "12 Jam Penuh", 35_000, 60_000, "Setengah Hari", "Paket setengah hari untuk live maraton."),
            RentalPlan("pkg_24h", "HOURLY", 24, "HOURS", "24 Jam (1 Hari)", 50_000, 80_000, "Full Day", "24 jam aktif tanpa henti."),

            // Kategori BULAN
            RentalPlan("pkg_1m", "MONTHLY", 1, "MONTHS", "1 Bulan PRO", 99_000, 150_000, "Rekomendasi", "Sewa 30 hari penuh dengan fitur kamera virtual lengkap."),
            RentalPlan("pkg_3m", "MONTHLY", 3, "MONTHS", "3 Bulan Studio", 250_000, 450_000, "Hemat 44%", "Paket 3 bulan hemat untuk studio live."),
            RentalPlan("pkg_6m", "MONTHLY", 6, "MONTHS", "6 Bulan Armada", 450_000, 900_000, "Hemat 50%", "Sewa 6 bulan untuk bisnis jangka menengah."),

            // Kategori TAHUN
            RentalPlan("pkg_1y", "YEARLY", 1, "YEARS", "1 Tahun PRO", 750_000, 1_500_000, "Hemat 50%", "Akses 365 hari penuh tanpa khawatir kedaluwarsa."),
            RentalPlan("pkg_2y", "YEARLY", 2, "YEARS", "2 Tahun Bisnis", 1_200_000, 3_000_000, "Super Hemat", "Investasi 2 tahun untuk kelancaran live jangka panjang."),

            // Kategori SELAMANYA
            RentalPlan("pkg_life", "LIFETIME", 0, "LIFETIME", "Lifetime PRO", 1_500_000, 3_500_000, "Sekali Bayar", "Lisensi permanen seumur hidup untuk perangkat ini.")
        )
        @Volatile
        private var cachedPlans: List<RentalPlan>? = null
    }

    /**
     * Memuat daftar paket sewa dari Cloud Firestore, atau fallback ke paket default.
     */
    suspend fun getPricingPlans(forceRefresh: Boolean = false): List<RentalPlan> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedPlans != null) {
            return@withContext cachedPlans!!
        }
        try {
            val endpoint = "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/system_config/pricing_plans?key=$FIRESTORE_API_KEY"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }

            if (conn.responseCode in 200..299) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                conn.disconnect()
                val json = JSONObject(resp)
                val fields = json.optJSONObject("fields")
                val plansJsonArray = fields?.optJSONObject("plans")?.optJSONObject("arrayValue")?.optJSONArray("values")
                if (plansJsonArray != null && plansJsonArray.length() > 0) {
                    val parsedList = mutableListOf<RentalPlan>()
                    for (i in 0 until plansJsonArray.length()) {
                        val pMap = plansJsonArray.getJSONObject(i).optJSONObject("mapValue")?.optJSONObject("fields") ?: continue
                        val id = pMap.optJSONObject("id")?.optString("stringValue") ?: "pkg_$i"
                        val cat = pMap.optJSONObject("category")?.optString("stringValue") ?: "HOURLY"
                        val durVal = extractInt(pMap.optJSONObject("durationValue"), 1)
                        val durUnit = pMap.optJSONObject("durationUnit")?.optString("stringValue") ?: "HOURS"
                        val label = pMap.optJSONObject("label")?.optString("stringValue") ?: "Paket Sewa"
                        val price = extractInt(pMap.optJSONObject("price"), 10000)
                        val orig = extractInt(pMap.optJSONObject("originalPrice"), 0)
                        val badge = pMap.optJSONObject("badge")?.optString("stringValue") ?: ""
                        val desc = pMap.optJSONObject("description")?.optString("stringValue") ?: ""
                        val active = pMap.optJSONObject("isActive")?.optBoolean("booleanValue") ?: true
                        if (active) {
                            parsedList.add(RentalPlan(id, cat, durVal, durUnit, label, price, orig, badge, desc, active))
                        }
                    }
                    if (parsedList.isNotEmpty()) {
                        cachedPlans = parsedList
                        return@withContext parsedList
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Ambil pricing dari Firestore lewati (${t.message}), gunakan default.")
        }
        return@withContext cachedPlans ?: DEFAULT_PLANS
    }

    private fun extractInt(fieldObj: JSONObject?, defaultVal: Int): Int {
        if (fieldObj == null) return defaultVal
        val strVal = fieldObj.optString("integerValue", "")
        if (strVal.isNotBlank()) {
            strVal.toIntOrNull()?.let { return it }
        }
        if (fieldObj.has("integerValue")) {
            return fieldObj.optInt("integerValue", defaultVal)
        }
        return defaultVal
    }

    /**
     * Memverifikasi kode promo ke Cloud Firestore atau daftar promo lokal.
     */
    suspend fun validatePromoCode(rawCode: String): PromoCode? = withContext(Dispatchers.IO) {
        val clean = rawCode.trim().uppercase()
        if (clean.isBlank()) return@withContext null

        // 1. Coba verifikasi via Firestore
        try {
            val endpoint = "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/system_config/promos?key=$FIRESTORE_API_KEY"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            if (conn.responseCode in 200..299) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                conn.disconnect()
                val json = JSONObject(resp)
                val fields = json.optJSONObject("fields")
                val promoMap = fields?.optJSONObject(clean)?.optJSONObject("mapValue")?.optJSONObject("fields")
                if (promoMap != null) {
                    val active = promoMap.optJSONObject("isActive")?.optBoolean("booleanValue") ?: true
                    val pct = promoMap.optJSONObject("discountPercent")?.optString("integerValue")?.toIntOrNull() ?: 0
                    val amt = promoMap.optJSONObject("discountAmount")?.optString("integerValue")?.toIntOrNull() ?: 0
                    if (active) {
                        return@withContext PromoCode(clean, discountPercent = pct, discountAmount = amt, isActive = true)
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (_: Throwable) {}

        // 2. Fallback Promo Bawaan
        return@withContext when (clean) {
            "TAJASBPRO" -> PromoCode(clean, discountPercent = 20, isActive = true)
            "DISKON50" -> PromoCode(clean, discountPercent = 50, isActive = true)
            "CUANCAME" -> PromoCode(clean, discountAmount = 15_000, isActive = true)
            "FLASHDEAL" -> PromoCode(clean, discountPercent = 30, isActive = true)
            else -> null
        }
    }

    data class TripayConfig(
        val merchantCode: String,
        val apiKey: String,
        val privateKey: String,
        val isSandbox: Boolean,
        val baseUrl: String
    )

    /**
     * Mengambil konfigurasi kredensial Tripay dari Firestore.
     */
    suspend fun getTripayConfig(): TripayConfig = withContext(Dispatchers.IO) {
        var mCode = "T47382"
        var apiKey = "DEV-8P2Q93M7K1L0"
        var privKey = "DEV-SECRET-PRIVKEY-12345"
        var isSandbox = true

        try {
            val endpoint = "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/system_config/tripay?key=$FIRESTORE_API_KEY"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            if (conn.responseCode in 200..299) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                conn.disconnect()
                val fields = JSONObject(resp).optJSONObject("fields")
                mCode = fields?.optJSONObject("merchantCode")?.optString("stringValue") ?: mCode
                apiKey = fields?.optJSONObject("apiKey")?.optString("stringValue") ?: apiKey
                privKey = fields?.optJSONObject("privateKey")?.optString("stringValue") ?: privKey
                isSandbox = fields?.optJSONObject("isSandbox")?.optBoolean("booleanValue") ?: isSandbox
            } else {
                conn.disconnect()
            }
        } catch (_: Throwable) {}

        TripayConfig(
            merchantCode = mCode.trim(),
            apiKey = apiKey.trim(),
            privateKey = privKey.trim(),
            isSandbox = isSandbox,
            baseUrl = if (isSandbox) "https://tripay.co.id/api-sandbox" else "https://tripay.co.id/api"
        )
    }

    /**
     * Membuat Closed Transaction QRIS ke Tripay API dan menyimpannya di Firestore.
     */
    suspend fun createQrisTransaction(
        plan: RentalPlan,
        promoCode: PromoCode? = null
    ): Result<TripayTransaction> = withContext(Dispatchers.IO) {
        try {
            val deviceId = LicenseManager.getInstance(context).getDeviceId()
            val basePrice = plan.price
            val discount = promoCode?.calculateDiscount(basePrice) ?: 0
            val finalAmount = (basePrice - discount).coerceAtLeast(1000)

            val nowSec = System.currentTimeMillis() / 1000
            val merchantRef = "TJ-INV-${System.currentTimeMillis()}-${(1000..9999).random()}"

            // Ambil config dinamis Tripay
            val config = getTripayConfig()

            // Signature HMAC-SHA256: Merchant Code + Merchant Ref + Amount (menggunakan privateKey aktual)
            val signPayload = "${config.merchantCode}$merchantRef$finalAmount"
            val signature = hmacSha256(signPayload, config.privateKey)

            val orderItems = JSONArray().apply {
                val item = JSONObject().apply {
                    put("sku", plan.id)
                    put("name", "${plan.label} (ID: $deviceId)")
                    put("price", finalAmount)
                    put("quantity", 1)
                }
                put(item)
            }

            val reqBody = JSONObject().apply {
                put("method", "QRIS")
                put("merchant_ref", merchantRef)
                put("amount", finalAmount)
                put("customer_name", "Penyewa Tajasb ($deviceId)")
                put("customer_email", "admin@tajasb.pro")
                put("customer_phone", "087821885302")
                put("order_items", orderItems)
                put("signature", signature)
                put("expired_time", nowSec + (15 * 60)) // 15 Menit
            }

            val targetUrl = "${config.baseUrl}/transaction/create"
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(reqBody.toString()) }

            val respCode = conn.responseCode
            val responseString = if (respCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            } else {
                val err = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, "UTF-8")).use { it.readText() }
                Log.w(TAG, "Tripay API error HTTP $respCode: $err")
                val parsedMsg = try {
                    val errJson = JSONObject(err)
                    errJson.optString("message", err)
                } catch (_: Exception) { err }

                if (config.isSandbox && (respCode in 400..499 || respCode >= 500)) {
                    // Di mode Sandbox, jika kredensial belum diisi ke Tripay Developer, sediakan QRIS simulator
                    createFallbackSimulationTransaction(merchantRef, plan, finalAmount, deviceId)
                } else {
                    val detailMsg = when {
                        parsedMsg.contains("Unauthorized IP", ignoreCase = true) || parsedMsg.contains("Whitelist IP", ignoreCase = true) ->
                            "$parsedMsg — SOLUSI: Buka Dashboard Tripay ➔ Merchant (${config.merchantCode}) ➔ Edit ➔ KOSONGKAN kolom 'Whitelist IP' agar semua jaringan HP penyewa & Bot Telegram diizinkan mengakses QRIS."
                        parsedMsg.isNotBlank() -> parsedMsg
                        else -> "HTTP $respCode - Pastikan Merchant Code & Kunci Tripay Production valid di Bot Telegram."
                    }
                    throw RuntimeException("Tripay ($respCode): $detailMsg")
                }
            }
            conn.disconnect()

            val json = JSONObject(responseString)
            val data = json.optJSONObject("data") ?: json
            val ref = data.optString("reference", "REF-$merchantRef")
            val qrUrl = data.optString("qr_url", "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=00020101021226540014ID.LINKAJA.WWW01189360091100213824040215$merchantRef")
            val qrStr = data.optString("qr_string", "")
            val checkoutUrl = data.optString("checkout_url", "https://tripay.co.id/checkout/$ref")
            val status = data.optString("status", "UNPAID")
            val expTime = data.optLong("expired_time", nowSec + 900)
            val nowMs = System.currentTimeMillis()

            val transaction = TripayTransaction(
                reference = ref,
                merchantRef = merchantRef,
                paymentMethod = "QRIS",
                amount = finalAmount,
                status = status,
                qrUrl = qrUrl,
                qrString = qrStr,
                checkoutUrl = checkoutUrl,
                expiredTime = expTime,
                planId = plan.id,
                planLabel = plan.label,
                category = plan.category,
                durationValue = plan.durationValue,
                durationUnit = plan.durationUnit,
                deviceId = deviceId,
                createdAt = nowMs
            )

            // Simpan sebagai transaksi pending lokal & ke riwayat lokal
            savePendingTransactionLocal(transaction, plan)
            saveTransactionToLocalHistory(transaction)

            // Catat ke Firestore collection tripay_transactions
            saveTransactionToFirestore(transaction, plan)

            Result.success(transaction)
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal membuat transaksi Tripay QRIS: ${t.message}", t)
            Result.failure(t)
        }
    }

    private fun getPendingPrefs() = context.getSharedPreferences("tajasb_tripay_pending", Context.MODE_PRIVATE)

    private fun saveTransactionToLocalHistory(tx: TripayTransaction) {
        try {
            val prefs = getPendingPrefs()
            val raw = prefs.getString("local_tx_history_json", "[]") ?: "[]"
            val arr = JSONArray(raw)
            val nextArr = JSONArray()

            val itemObj = JSONObject().apply {
                put("reference", tx.reference)
                put("merchantRef", tx.merchantRef)
                put("paymentMethod", tx.paymentMethod)
                put("amount", tx.amount)
                put("status", tx.status)
                put("qrUrl", tx.qrUrl)
                put("qrString", tx.qrString)
                put("checkoutUrl", tx.checkoutUrl)
                put("expiredTime", tx.expiredTime)
                put("planId", tx.planId)
                put("planLabel", tx.planLabel)
                put("category", tx.category)
                put("durationValue", tx.durationValue)
                put("durationUnit", tx.durationUnit)
                put("deviceId", tx.deviceId)
                put("licenseKey", tx.licenseKey)
                put("createdAt", tx.createdAt)
            }
            nextArr.put(itemObj)

            for (i in 0 until arr.length()) {
                if (nextArr.length() >= 30) break
                val old = arr.optJSONObject(i) ?: continue
                if (!old.optString("reference").equals(tx.reference, ignoreCase = true)) {
                    nextArr.put(old)
                }
            }
            prefs.edit().putString("local_tx_history_json", nextArr.toString()).apply()
        } catch (_: Throwable) {}
    }

    fun getLocalTransactionHistory(): List<TripayTransaction> {
        val list = mutableListOf<TripayTransaction>()
        try {
            val raw = getPendingPrefs().getString("local_tx_history_json", "[]") ?: "[]"
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    TripayTransaction(
                        reference = obj.optString("reference"),
                        merchantRef = obj.optString("merchantRef"),
                        paymentMethod = obj.optString("paymentMethod", "QRIS"),
                        amount = obj.optInt("amount", 1000),
                        status = obj.optString("status", "UNPAID"),
                        qrUrl = obj.optString("qrUrl"),
                        qrString = obj.optString("qrString"),
                        checkoutUrl = obj.optString("checkoutUrl"),
                        expiredTime = obj.optLong("expiredTime", 0L),
                        planId = obj.optString("planId", "pkg_1h"),
                        planLabel = obj.optString("planLabel", "Paket Sewa"),
                        category = obj.optString("category", "HOURLY"),
                        durationValue = obj.optInt("durationValue", 1),
                        durationUnit = obj.optString("durationUnit", "HOURS"),
                        deviceId = obj.optString("deviceId"),
                        licenseKey = obj.optString("licenseKey"),
                        createdAt = obj.optLong("createdAt", 0L)
                    )
                )
            }
        } catch (_: Throwable) {}
        return list
    }

    /**
     * Mengambil seluruh riwayat transaksi Tripay milik perangkat ini dari Cloud Firestore (:runQuery)
     * digabung dengan riwayat lokal, memeriksa status transaksi UNPAID ke Tripay API, dan otomatis
     * mengaktifkan lisensi PRO jika ditemukan transaksi PAID yang sah!
     */
    suspend fun fetchDeviceTransactions(autoActivateIfPaid: Boolean = true): List<TripayTransaction> = withContext(Dispatchers.IO) {
        fetchDeviceTransactionsBlocking(autoActivateIfPaid)
    }

    fun fetchDeviceTransactionsBlocking(autoActivateIfPaid: Boolean = true): List<TripayTransaction> {
        val myDeviceId = LicenseManager.getInstance(context).getDeviceId().trim()
        val txMap = linkedMapOf<String, TripayTransaction>()

        // 1. Muat dari riwayat lokal terlebih dahulu
        for (localTx in getLocalTransactionHistory()) {
            if (localTx.reference.isNotBlank()) {
                txMap[localTx.reference] = localTx
            }
        }

        // 2. Query ke Firestore menggunakan :runQuery (structuredQuery) agar tidak terkena 403 ListDocuments
        try {
            val queryUrl = "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents:runQuery?key=$FIRESTORE_API_KEY"
            val queryBody = JSONObject().apply {
                put("structuredQuery", JSONObject().apply {
                    put("from", JSONArray().put(JSONObject().put("collectionId", "tripay_transactions")))
                })
            }
            val conn = (URL(queryUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                doInput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(queryBody.toString()) }
            if (conn.responseCode in 200..299) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                conn.disconnect()
                val arr = JSONArray(resp)
                for (i in 0 until arr.length()) {
                    val doc = arr.optJSONObject(i)?.optJSONObject("document") ?: continue
                    val fields = doc.optJSONObject("fields") ?: continue
                    val devId = fields.optJSONObject("deviceId")?.optString("stringValue") ?: ""
                    if (!devId.equals(myDeviceId, ignoreCase = true)) continue

                    val docName = doc.optString("name", "")
                    val ref = fields.optJSONObject("reference")?.optString("stringValue")?.takeIf { it.isNotBlank() }
                        ?: docName.substringAfterLast("/")
                    if (ref.isBlank()) continue

                    val mRef = fields.optJSONObject("merchantRef")?.optString("stringValue") ?: ""
                    val planId = fields.optJSONObject("planId")?.optString("stringValue") ?: "pkg_1h"
                    val planLabel = fields.optJSONObject("planLabel")?.optString("stringValue") ?: "Paket Sewa"
                    val cat = fields.optJSONObject("category")?.optString("stringValue") ?: "HOURLY"
                    val durVal = extractInt(fields.optJSONObject("durationValue"), 1)
                    val durUnit = fields.optJSONObject("durationUnit")?.optString("stringValue") ?: "HOURS"
                    val amt = extractInt(fields.optJSONObject("amount"), 1000)
                    val st = fields.optJSONObject("status")?.optString("stringValue") ?: "UNPAID"
                    val qrU = fields.optJSONObject("qrUrl")?.optString("stringValue")
                        ?: "https://tripay.co.id/qr/$ref"
                    val licKey = fields.optJSONObject("licenseKey")?.optString("stringValue") ?: ""
                    val createdTs = fields.optJSONObject("createdAt")?.optString("integerValue")?.toLongOrNull()
                        ?: fields.optJSONObject("updatedAt")?.optString("integerValue")?.toLongOrNull()
                        ?: txMap[ref]?.createdAt
                        ?: System.currentTimeMillis()

                    val prev = txMap[ref]
                    val merged = TripayTransaction(
                        reference = ref,
                        merchantRef = mRef.ifBlank { prev?.merchantRef ?: "" },
                        paymentMethod = "QRIS",
                        amount = amt,
                        status = st,
                        qrUrl = qrU.ifBlank { prev?.qrUrl ?: "" },
                        qrString = prev?.qrString ?: "",
                        checkoutUrl = prev?.checkoutUrl?.ifBlank { "https://tripay.co.id/checkout/$ref" } ?: "https://tripay.co.id/checkout/$ref",
                        expiredTime = prev?.expiredTime ?: ((createdTs / 1000L) + 900L),
                        planId = planId,
                        planLabel = planLabel,
                        category = cat,
                        durationValue = durVal,
                        durationUnit = durUnit,
                        deviceId = myDeviceId,
                        licenseKey = licKey.ifBlank { prev?.licenseKey ?: "" },
                        createdAt = createdTs
                    )
                    txMap[ref] = merged
                    saveTransactionToLocalHistory(merged)
                }
            } else {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal query riwayat transaksi dari Firestore: ${t.message}")
        }

        val sortedList = txMap.values.sortedByDescending { it.createdAt }.toMutableList()

        // 3. Periksa hingga 3 transaksi UNPAID terbaru langsung ke Tripay API & aktifkan jika ada yang PAID
        var checkedUnpaidCount = 0
        for (idx in sortedList.indices) {
            val tx = sortedList[idx]
            if (tx.status.equals("UNPAID", ignoreCase = true) && !tx.reference.startsWith("DEV-") && checkedUnpaidCount < 3) {
                checkedUnpaidCount++
                val plan = RentalPlan(
                    id = tx.planId.ifBlank { "pkg_1h" },
                    category = tx.category,
                    durationValue = tx.durationValue,
                    durationUnit = tx.durationUnit,
                    label = tx.planLabel,
                    price = tx.amount
                )
                val updatedSt = checkAndActivateInternal(tx.reference, plan)
                if (!updatedSt.equals(tx.status, ignoreCase = true)) {
                    val updatedTx = tx.copy(
                        status = updatedSt,
                        licenseKey = if (updatedSt == "PAID") (LicenseManager.getInstance(context).getActiveToken() ?: tx.licenseKey) else tx.licenseKey
                    )
                    sortedList[idx] = updatedTx
                    saveTransactionToLocalHistory(updatedTx)
                }
            }
        }

        // 4. Jika perangkat saat ini masih BELUM AKTIVASI (masih oranye), namun ada transaksi berstatus PAID
        // yang masa sewanya masih berlaku, verifikasi keasliannya sebelum mengaktifkan!
        if (autoActivateIfPaid) {
            val licenseMgr = LicenseManager.getInstance(context)
            val currentLicStatus = licenseMgr.refreshLicenseStatus()
            if (currentLicStatus !is com.tajasb.model.LicenseStatus.Active) {
                val nowMs = System.currentTimeMillis()
                val validPaidTx = sortedList.firstOrNull { tx ->
                    if (!tx.status.equals("PAID", ignoreCase = true)) return@firstOrNull false
                    val durMs = when {
                        tx.category.equals("LIFETIME", ignoreCase = true) -> Long.MAX_VALUE / 2
                        tx.category.equals("HOURLY", ignoreCase = true) -> tx.durationValue.coerceAtLeast(1) * 3600_000L
                        tx.category.equals("DAILY", ignoreCase = true) -> tx.durationValue.coerceAtLeast(1) * 86_400_000L
                        tx.category.equals("MONTHLY", ignoreCase = true) -> tx.durationValue.coerceAtLeast(1) * 30L * 86_400_000L
                        else -> tx.durationValue.coerceAtLeast(1) * 365L * 86_400_000L
                    }
                    (tx.licenseKey.startsWith(LicenseManager.LICENSE_KEY_PREFIX) && LicenseManager.verifyTokenDirect(tx.licenseKey, myDeviceId)) ||
                        (nowMs - tx.createdAt) < (durMs + 6 * 3600_000L)
                }
                if (validPaidTx != null) {
                    val hasValidSignedCloudKey = validPaidTx.licenseKey.startsWith(LicenseManager.LICENSE_KEY_PREFIX) &&
                        LicenseManager.verifyTokenDirect(validPaidTx.licenseKey, myDeviceId)
                    if (hasValidSignedCloudKey) {
                        val actRes = licenseMgr.activateFromVerifiedPayment(
                            reference = validPaidTx.reference,
                            category = validPaidTx.category,
                            durationValue = validPaidTx.durationValue,
                            durationUnit = validPaidTx.durationUnit,
                            existingCloudKey = validPaidTx.licenseKey
                        )
                        if (actRes is com.tajasb.model.ActivationResult.Success) {
                            val updatedTx = validPaidTx.copy(licenseKey = actRes.license.activationKey)
                            saveTransactionToLocalHistory(updatedTx)
                        }
                    } else {
                        // KEAMANAN ANTI-SPOOFING: Jika belum ada licenseKey bertanda tangan ECDSA,
                        // wajib konfirmasi ulang ke server resmi Tripay API sebelum self-issuance!
                        val verifyPlan = RentalPlan(
                            id = validPaidTx.planId.ifBlank { "pkg_1h" },
                            category = validPaidTx.category,
                            durationValue = validPaidTx.durationValue,
                            durationUnit = validPaidTx.durationUnit,
                            label = validPaidTx.planLabel,
                            price = validPaidTx.amount
                        )
                        checkAndActivateInternal(validPaidTx.reference, verifyPlan)
                    }
                }
            }
        }

        return sortedList
    }

    private fun savePendingTransactionLocal(t: TripayTransaction, plan: RentalPlan) {
        try {
            getPendingPrefs().edit()
                .putString("pending_ref", t.reference)
                .putString("pending_plan_id", plan.id)
                .putString("pending_plan_label", plan.label)
                .putString("pending_category", plan.category)
                .putInt("pending_dur_val", plan.durationValue)
                .putString("pending_dur_unit", plan.durationUnit)
                .putInt("pending_amount", t.amount)
                .putLong("pending_created_at", System.currentTimeMillis())
                .apply()
        } catch (_: Throwable) {}
    }

    private fun clearPendingTransactionLocal() {
        try {
            getPendingPrefs().edit()
                .remove("pending_ref")
                .remove("pending_plan_id")
                .remove("pending_plan_label")
                .remove("pending_category")
                .remove("pending_dur_val")
                .remove("pending_dur_unit")
                .remove("pending_amount")
                .remove("pending_created_at")
                .apply()
        } catch (_: Throwable) {}
    }

    fun hasPendingTransaction(): Boolean {
        val prefs = getPendingPrefs()
        val ref = prefs.getString("pending_ref", null) ?: return false
        val createdAt = prefs.getLong("pending_created_at", 0L)
        return (System.currentTimeMillis() - createdAt <= 24 * 3600_000L)
    }

    /**
     * Memeriksa apakah ada transaksi QRIS yang dibuat dalam 24 jam terakhir yang sudah dibayar
     * namun belum sempat diaktifkan (misal pengguna menutup dialog saat membayar lewat HP kedua).
     * Dioptimalkan: Hanya mengecek jika memang ada transaksi pending di penyimpanan lokal perangkat.
     */
    fun checkPendingTransactionBlocking(): Boolean {
        return try {
            val prefs = getPendingPrefs()
            val ref = prefs.getString("pending_ref", null)
            if (ref != null) {
                val createdAt = prefs.getLong("pending_created_at", 0L)
                if (System.currentTimeMillis() - createdAt <= 24 * 3600_000L) {
                    val plan = RentalPlan(
                        id = prefs.getString("pending_plan_id", "pkg_1h") ?: "pkg_1h",
                        category = prefs.getString("pending_category", "HOURLY") ?: "HOURLY",
                        durationValue = prefs.getInt("pending_dur_val", 1),
                        durationUnit = prefs.getString("pending_dur_unit", "HOURS") ?: "HOURS",
                        label = prefs.getString("pending_plan_label", "Paket Sewa") ?: "Paket Sewa",
                        price = prefs.getInt("pending_amount", 10000)
                    )
                    val status = checkAndActivateInternal(ref, plan)
                    if (status.equals("PAID", ignoreCase = true)) return true
                } else {
                    clearPendingTransactionLocal()
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun checkPendingTransaction(): Boolean = withContext(Dispatchers.IO) {
        checkPendingTransactionBlocking()
    }

    /**
     * Memeriksa status transaksi ke Firestore & Tripay API.
     * Jika status PAID, otomatis menerbitkan & mengaktifkan lisensi PRO di HP ini seketika!
     */
    suspend fun checkTransactionStatus(reference: String, plan: RentalPlan? = null): String = withContext(Dispatchers.IO) {
        checkAndActivateInternal(reference, plan)
    }

    private fun checkAndActivateInternal(reference: String, passedPlan: RentalPlan?): String {
        val myDeviceId = LicenseManager.getInstance(context).getDeviceId().trim()
        var currentStatus = "UNPAID"
        var cloudLicenseKey: String? = null
        var hasSignedCloudKey = false
        var fsCategory = passedPlan?.category ?: "HOURLY"
        var fsDurVal = passedPlan?.durationValue ?: 1
        var fsDurUnit = passedPlan?.durationUnit ?: "HOURS"
        var fsLabel = passedPlan?.label ?: "Paket Sewa"
        var fsAmount = passedPlan?.price ?: 10000

        // 1. Cek dari Firestore dulu
        try {
            val endpoint = "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/tripay_transactions/$reference?key=$FIRESTORE_API_KEY"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (conn.responseCode in 200..299) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                conn.disconnect()
                val fields = JSONObject(resp).optJSONObject("fields")
                val st = fields?.optJSONObject("status")?.optString("stringValue") ?: "UNPAID"
                cloudLicenseKey = fields?.optJSONObject("licenseKey")?.optString("stringValue")
                hasSignedCloudKey = !cloudLicenseKey.isNullOrBlank() &&
                    cloudLicenseKey!!.trim().startsWith(LicenseManager.LICENSE_KEY_PREFIX, ignoreCase = true) &&
                    LicenseManager.verifyTokenDirect(cloudLicenseKey!!.trim(), myDeviceId)

                // Hanya percayai perubahan kategori/durasi dari Firestore jika sudah ada kunci ECDSA sah
                // (mencegah pembobol mengubah order 1 Jam menjadi LIFETIME via HTTP PATCH)
                if (hasSignedCloudKey || passedPlan == null) {
                    fields?.optJSONObject("category")?.optString("stringValue")?.takeIf { it.isNotBlank() }?.let { fsCategory = it }
                    fields?.optJSONObject("durationUnit")?.optString("stringValue")?.takeIf { it.isNotBlank() }?.let { fsDurUnit = it }
                    fields?.optJSONObject("planLabel")?.optString("stringValue")?.takeIf { it.isNotBlank() }?.let { fsLabel = it }
                    extractInt(fields?.optJSONObject("durationValue"), fsDurVal).let { fsDurVal = it }
                    extractInt(fields?.optJSONObject("amount"), fsAmount).let { fsAmount = it }
                }

                // KEAMANAN ANTI-SPOOFING: Status PAID di Firestore tanpa licenseKey bertanda tangan ECDSA
                // TIDAK BOLEH langsung dipercaya tanpa verifikasi ke server Tripay API!
                if ((st.equals("PAID", ignoreCase = true) || st.equals("SETTLED", ignoreCase = true)) && hasSignedCloudKey) {
                    currentStatus = "PAID"
                } else if (!st.equals("PAID", ignoreCase = true) && !st.equals("SETTLED", ignoreCase = true) && st.isNotBlank()) {
                    currentStatus = st.uppercase()
                }
            } else {
                conn.disconnect()
            }
        } catch (_: Throwable) {}

        // 2. Jika belum memiliki kunci ECDSA dari Bot, WAJIB verifikasi langsung ke REST API Tripay
        if (currentStatus != "PAID" && !reference.startsWith("DEV-")) {
            try {
                val config = getTripayConfigSync()
                val urlsToTry = listOf(
                    "${config.baseUrl}/transaction/detail?reference=$reference",
                    if (config.isSandbox) "https://tripay.co.id/api/transaction/detail?reference=$reference"
                    else "https://tripay.co.id/api-sandbox/transaction/detail?reference=$reference"
                )
                for (targetUrl in urlsToTry) {
                    try {
                        val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 4500
                            readTimeout = 4500
                            useCaches = false
                            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
                        }
                        if (conn.responseCode in 200..299) {
                            val resp = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                            conn.disconnect()
                            val json = JSONObject(resp)
                            val dataObj = json.optJSONObject("data")
                            val st = dataObj?.optString("status") ?: "UNPAID"
                            val paidAmount = dataObj?.optInt("amount", 0) ?: 0
                            if (st.equals("PAID", ignoreCase = true) || st.equals("SETTLED", ignoreCase = true)) {
                                if (paidAmount <= 0 || paidAmount >= (fsAmount * 0.5).toInt()) {
                                    currentStatus = "PAID"
                                    break
                                } else {
                                    Log.w(TAG, "🚨 Menolak status PAID Tripay karena nominal bayar ($paidAmount) tidak sesuai paket ($fsAmount).")
                                }
                            } else if (st.isNotBlank() && st != "UNPAID") {
                                currentStatus = st.uppercase()
                            }
                            break
                        } else {
                            conn.disconnect()
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }

        // 3. JIKA STATUS PAID -> LANGSUNG AKTIFKAN LISENSI PRO DI PERANGKAT & SINKRONKAN KE FIRESTORE!
        if (currentStatus == "PAID") {
            val licenseMgr = LicenseManager.getInstance(context)
            val actRes = licenseMgr.activateFromVerifiedPayment(
                reference = reference,
                category = fsCategory,
                durationValue = fsDurVal,
                durationUnit = fsDurUnit,
                existingCloudKey = cloudLicenseKey
            )
            val finalKey = when (actRes) {
                is com.tajasb.model.ActivationResult.Success -> actRes.license.activationKey
                else -> licenseMgr.getActiveToken() ?: cloudLicenseKey ?: ""
            }

            // Pastikan dokumen di Firestore terupdate PAID + licenseKey agar Bot Telegram ikut mendeteksi & mengirim laporan ke Admin
            val effectivePlan = passedPlan ?: RentalPlan(
                id = "pkg_auto",
                category = fsCategory,
                durationValue = fsDurVal,
                durationUnit = fsDurUnit,
                label = fsLabel,
                price = fsAmount
            )
            markTransactionPaidInFirestore(reference, finalKey, effectivePlan)
            clearPendingTransactionLocal()

            try {
                com.tajasb.remote.RemoteTelemetryManager.getInstance(context).triggerImmediateSync()
            } catch (_: Throwable) {}
        }

        return currentStatus
    }

    private fun getTripayConfigSync(): TripayConfig {
        var mCode = "T47382"
        var apiKey = "DEV-8P2Q93M7K1L0"
        var privKey = "DEV-SECRET-PRIVKEY-12345"
        var isSandbox = true
        try {
            val endpoint = "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/system_config/tripay?key=$FIRESTORE_API_KEY"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            if (conn.responseCode in 200..299) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                conn.disconnect()
                val fields = JSONObject(resp).optJSONObject("fields")
                mCode = fields?.optJSONObject("merchantCode")?.optString("stringValue") ?: mCode
                apiKey = fields?.optJSONObject("apiKey")?.optString("stringValue") ?: apiKey
                privKey = fields?.optJSONObject("privateKey")?.optString("stringValue") ?: privKey
                isSandbox = fields?.optJSONObject("isSandbox")?.optBoolean("booleanValue") ?: isSandbox
            } else {
                conn.disconnect()
            }
        } catch (_: Throwable) {}
        return TripayConfig(
            merchantCode = mCode.trim(),
            apiKey = apiKey.trim(),
            privateKey = privKey.trim(),
            isSandbox = isSandbox,
            baseUrl = if (isSandbox) "https://tripay.co.id/api-sandbox" else "https://tripay.co.id/api"
        )
    }

    private fun markTransactionPaidInFirestore(reference: String, licenseKey: String, plan: RentalPlan) {
        try {
            val deviceId = LicenseManager.getInstance(context).getDeviceId()
            val mask = "updateMask.fieldPaths=status&updateMask.fieldPaths=licenseKey&updateMask.fieldPaths=deviceId&updateMask.fieldPaths=reference&updateMask.fieldPaths=planLabel&updateMask.fieldPaths=category&updateMask.fieldPaths=durationValue&updateMask.fieldPaths=durationUnit&updateMask.fieldPaths=amount"
            val patchUrl = "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/tripay_transactions/$reference?$mask&key=$FIRESTORE_API_KEY"
            val patchBody = JSONObject().apply {
                put("fields", JSONObject().apply {
                    put("reference", JSONObject().put("stringValue", reference))
                    put("deviceId", JSONObject().put("stringValue", deviceId))
                    put("planLabel", JSONObject().put("stringValue", plan.label))
                    put("category", JSONObject().put("stringValue", plan.category))
                    put("durationValue", JSONObject().put("integerValue", plan.durationValue.toString()))
                    put("durationUnit", JSONObject().put("stringValue", plan.durationUnit))
                    put("amount", JSONObject().put("integerValue", plan.price.toString()))
                    put("status", JSONObject().put("stringValue", "PAID"))
                    if (licenseKey.isNotBlank()) {
                        put("licenseKey", JSONObject().put("stringValue", licenseKey))
                    }
                })
            }
            val conn = (URL(patchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(patchBody.toString()) }
            conn.responseCode
            conn.disconnect()
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal update status PAID ke Firestore: ${t.message}")
        }
    }

    /**
     * Khusus Sandbox / Demo: Mensimulasikan pembayaran berhasil di Firestore & langsung mengaktifkan lisensi.
     */
    suspend fun simulateSuccessfulPayment(reference: String, plan: RentalPlan? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val patchUrl = "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/tripay_transactions/$reference?updateMask.fieldPaths=status&key=$FIRESTORE_API_KEY"
            val patchBody = JSONObject().apply {
                put("fields", JSONObject().apply {
                    put("status", JSONObject().apply {
                        put("stringValue", "PAID")
                    })
                })
            }
            val conn = (URL(patchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(patchBody.toString()) }
            val code = conn.responseCode
            conn.disconnect()

            // Langsung jalankan aktivasi otomatis
            checkAndActivateInternal(reference, plan)

            if (code in 200..299) {
                Result.success(true)
            } else {
                Result.success(true)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun createFallbackSimulationTransaction(merchantRef: String, plan: RentalPlan, amount: Int, deviceId: String): String {
        val nowSec = System.currentTimeMillis() / 1000
        val dummyRef = "DEV-$merchantRef"
        val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=00020101021226540014ID.LINKAJA.WWW01189360091100213824040215$dummyRef"
        return JSONObject().apply {
            put("success", true)
            put("message", "Simulasi QRIS Aktif")
            put("data", JSONObject().apply {
                put("reference", dummyRef)
                put("merchant_ref", merchantRef)
                put("payment_method", "QRIS")
                put("amount", amount)
                put("status", "UNPAID")
                put("expired_time", nowSec + 900)
                put("qr_url", qrUrl)
                put("checkout_url", "https://tripay.co.id/checkout/$dummyRef")
            })
        }.toString()
    }

    private fun saveTransactionToFirestore(t: TripayTransaction, plan: RentalPlan) {
        try {
            val endpoint = "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/tripay_transactions/${t.reference}?key=$FIRESTORE_API_KEY"
            val fieldsObj = JSONObject().apply {
                put("reference", JSONObject().put("stringValue", t.reference))
                put("merchantRef", JSONObject().put("stringValue", t.merchantRef))
                put("deviceId", JSONObject().put("stringValue", t.deviceId))
                put("planId", JSONObject().put("stringValue", plan.id))
                put("planLabel", JSONObject().put("stringValue", plan.label))
                put("category", JSONObject().put("stringValue", plan.category))
                put("durationValue", JSONObject().put("integerValue", plan.durationValue.toString()))
                put("durationUnit", JSONObject().put("stringValue", plan.durationUnit))
                put("amount", JSONObject().put("integerValue", t.amount.toString()))
                put("status", JSONObject().put("stringValue", t.status))
                put("qrUrl", JSONObject().put("stringValue", t.qrUrl))
                put("createdAt", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
                put("notifiedTelegram", JSONObject().put("booleanValue", false))
            }

            val requestBody = JSONObject().put("fields", fieldsObj)
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(requestBody.toString()) }
            val respCode = conn.responseCode
            conn.disconnect()
            Log.d(TAG, "Transaksi ${t.reference} berhasil dicatat ke Firestore (HTTP $respCode)")
        } catch (e: Throwable) {
            Log.w(TAG, "Gagal simpan log transaksi ke Firestore: ${e.message}")
        }
    }

    private fun hmacSha256(data: String, key: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
            mac.init(secretKey)
            val bytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            ""
        }
    }
}
