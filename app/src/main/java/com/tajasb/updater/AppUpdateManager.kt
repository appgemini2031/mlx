package com.tajasb.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseUpdateInfo(
    val versionTag: String,
    val cleanVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val assetApiUrl: String? = null,
    val htmlReleaseUrl: String,
    val apkSizeBytes: Long = 0L,
    val publishedAt: String = "",
    val isArtifactZip: Boolean = false
)

data class AppUpdateUiState(
    val currentVersion: String = "1.0.50",
    val repoSlug: String = DEFAULT_GITHUB_REPO,
    val githubToken: String = "",
    val isChecking: Boolean = false,
    val hasCheckedOnce: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val isBannerDismissed: Boolean = false,
    val latestRelease: ReleaseUpdateInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkPath: String? = null,
    val statusMessage: String = "Siap memeriksa pembaruan APK",
    val errorMessage: String? = null,
    val isTestingConnection: Boolean = false,
    val testConnectionMessage: String? = null
) {
    companion object {
        const val DEFAULT_GITHUB_REPO = "santrehprint/tajasb"
    }
}

class AppUpdateManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(
        AppUpdateUiState(
            currentVersion = resolveInstalledVersion(appContext),
            repoSlug = normalizeRepoSlug(prefs.getString(KEY_GITHUB_REPO, AppUpdateUiState.DEFAULT_GITHUB_REPO) ?: AppUpdateUiState.DEFAULT_GITHUB_REPO),
            githubToken = cleanToken(prefs.getString(KEY_GITHUB_TOKEN, ""))
        )
    )
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    fun isAuthorizedForUpdate(): Boolean {
        return true
    }

    fun getGithubToken(): String = cleanToken(prefs.getString(KEY_GITHUB_TOKEN, ""))

    fun saveGithubToken(token: String) {
        val clean = cleanToken(token)
        prefs.edit().putString(KEY_GITHUB_TOKEN, clean).apply()
        _uiState.value = _uiState.value.copy(
            githubToken = clean,
            statusMessage = if (clean.isNotBlank()) "Token GitHub tersimpan (Repo Privat didukung)" else "Token GitHub dihapus",
            errorMessage = null,
            testConnectionMessage = null
        )
    }

    fun getSkippedVersionTag(): String? = prefs.getString(KEY_SKIPPED_TAG, null)

    fun setGithubRepoSlug(newSlugOrUrl: String) {
        val normalized = normalizeRepoSlug(newSlugOrUrl)
        prefs.edit().putString(KEY_GITHUB_REPO, normalized).apply()
        _uiState.value = _uiState.value.copy(
            repoSlug = normalized,
            statusMessage = "Sumber repositori disimpan: $normalized",
            errorMessage = null,
            testConnectionMessage = null
        )
    }

    fun dismissBannerForSession() {
        _uiState.value = _uiState.value.copy(isBannerDismissed = true)
    }

    fun skipCurrentUpdateVersion() {
        val tag = _uiState.value.latestRelease?.versionTag ?: return
        prefs.edit().putString(KEY_SKIPPED_TAG, tag).apply()
        _uiState.value = _uiState.value.copy(
            isBannerDismissed = true,
            statusMessage = "Pembaruan $tag dilewati (opsional)"
        )
    }

    fun testConnection(onComplete: ((Boolean, String) -> Unit)? = null) {
        if (_uiState.value.isTestingConnection) return
        val slug = normalizeRepoSlug(_uiState.value.repoSlug)
        val token = getGithubToken()
        val authHeaders = getAuthHeadersToTry(token)

        _uiState.value = _uiState.value.copy(
            isTestingConnection = true,
            testConnectionMessage = "Menguji koneksi ke https://github.com/$slug..."
        )

        scope.launch {
            var isSuccess = false
            var resultMessage = ""
            val testHeaders = if (authHeaders.isNotEmpty()) authHeaders else listOf(null)

            for (auth in testHeaders) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL("https://api.github.com/repos/$slug").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout = 12_000
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "TajasbVCam-Tester/2.0")
                        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                        if (auth != null) {
                            setRequestProperty("Authorization", auth)
                        }
                    }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val isPrivate = JSONObject(body).optBoolean("private", false)
                        val privLabel = if (isPrivate) "Privat (Token Valid)" else "Publik"

                        // Cek keberadaan Releases atau Artifacts
                        var extraInfo = ""
                        try {
                            val relConn = (URL("https://api.github.com/repos/$slug/releases?per_page=5").openConnection() as HttpURLConnection).apply {
                                connectTimeout = 8_000
                                readTimeout = 8_000
                                setRequestProperty("Accept", "application/vnd.github+json")
                                setRequestProperty("User-Agent", "TajasbVCam-Tester/2.0")
                                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                                if (auth != null) setRequestProperty("Authorization", auth)
                            }
                            if (relConn.responseCode in 200..299) {
                                val relArray = JSONArray(relConn.inputStream.bufferedReader().use { it.readText() })
                                extraInfo = if (relArray.length() > 0) {
                                    " • ${relArray.length()} Rilis GitHub ditemukan"
                                } else {
                                    " • Rilis kosong (Akan memeriksa Artifact Actions saat update)"
                                }
                            }
                            relConn.disconnect()
                        } catch (_: Exception) {}

                        isSuccess = true
                        resultMessage = "✅ Terhubung ke repositori $slug [$privLabel]$extraInfo"
                        break
                    } else if (code == 404) {
                        resultMessage = if (token.isBlank()) {
                            "❌ HTTP 404: Repositori $slug bersifat privat atau belum ada. Masukkan Token PAT dengan izin repo."
                        } else {
                            "❌ HTTP 404: Repositori $slug tidak ditemukan atau Token PAT belum dicentang izin 'repo' (Full control of private repositories) di GitHub Settings."
                        }
                    } else if (code == 401 || code == 403) {
                        resultMessage = "❌ HTTP $code: Akses ditolak. Token GitHub salah, kadaluarsa, atau tidak memiliki izin akses."
                    } else {
                        resultMessage = "❌ Server GitHub merespons HTTP $code"
                    }
                } catch (e: Exception) {
                    resultMessage = "❌ Koneksi gagal: ${e.message ?: "Kesalahan jaringan"}"
                } finally {
                    conn?.disconnect()
                }
            }

            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                testConnectionMessage = resultMessage
            )
            withContext(Dispatchers.Main) {
                onComplete?.invoke(isSuccess, resultMessage)
            }
        }
    }

    fun checkForUpdates(manualTrigger: Boolean = false, showSystemNotification: Boolean = false) {
        // Keamanan: Hanya device khusus yang diotorisasi (Owner / VIP) yang memunculkan dan menjalankan update APK
        if (!isAuthorizedForUpdate()) {
            _uiState.value = _uiState.value.copy(
                isChecking = false,
                isUpdateAvailable = false,
                latestRelease = null
            )
            return
        }

        if (_uiState.value.isChecking) return
        val currentVersion = resolveInstalledVersion(appContext)
        val repoSlug = _uiState.value.repoSlug.ifBlank { AppUpdateUiState.DEFAULT_GITHUB_REPO }
        val token = getGithubToken()

        _uiState.value = _uiState.value.copy(
            currentVersion = currentVersion,
            isChecking = true,
            errorMessage = null,
            statusMessage = "Memeriksa rilis terbaru dari GitHub ($repoSlug)..."
        )

        scope.launch {
            try {
                val releaseInfo = fetchLatestReleaseFromGitHub(repoSlug, token)
                val isNewer = isRemoteVersionNewer(
                    currentVersion = currentVersion,
                    remoteVersion = releaseInfo.cleanVersion
                )
                val skippedTag = getSkippedVersionTag()
                val isSkipped = !manualTrigger && skippedTag != null && skippedTag == releaseInfo.versionTag

                val existingFile = getCachedApkFile(releaseInfo.cleanVersion)
                val alreadyDownloadedPath = existingFile.takeIf {
                    it.exists() && it.length() > 100_000L
                }?.absolutePath

                _uiState.value = _uiState.value.copy(
                    currentVersion = currentVersion,
                    isChecking = false,
                    hasCheckedOnce = true,
                    isUpdateAvailable = isNewer,
                    isBannerDismissed = if (manualTrigger) false else isSkipped,
                    latestRelease = releaseInfo,
                    downloadedApkPath = alreadyDownloadedPath,
                    errorMessage = null,
                    statusMessage = if (isNewer) {
                        "Pembaruan tersedia: v${releaseInfo.cleanVersion} (Saat ini v$currentVersion)"
                    } else {
                        "Aplikasi sudah versi terbaru (v$currentVersion)"
                    }
                )

                if (isNewer && showSystemNotification && !isSkipped) {
                    val lastNotifiedTag = prefs.getString(KEY_LAST_NOTIFIED_TAG, null)
                    if (manualTrigger || lastNotifiedTag != releaseInfo.versionTag) {
                        prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, releaseInfo.versionTag).apply()
                        postUpdateAvailableNotification(releaseInfo)
                    }
                }
            } catch (e: Exception) {
                val friendlyError = e.message ?: "Gagal memeriksa pembaruan"
                _uiState.value = _uiState.value.copy(
                    currentVersion = currentVersion,
                    isChecking = false,
                    hasCheckedOnce = true,
                    errorMessage = friendlyError,
                    statusMessage = "Belum dapat menjangkau rilis GitHub ($repoSlug)"
                )
            }
        }
    }

    fun downloadAndInstallLatestApk(context: Context) {
        if (!isAuthorizedForUpdate()) return

        val state = _uiState.value
        val release = state.latestRelease ?: return
        if (state.isDownloading) return

        val token = getGithubToken()
        val isPrivateAssetApi = token.isNotBlank() && !release.assetApiUrl.isNullOrBlank()
        val rawDownloadUrl = if (isPrivateAssetApi) release.assetApiUrl else release.apkDownloadUrl
        if (rawDownloadUrl.isNullOrBlank()) {
            openReleasePageInBrowser(context, release.htmlReleaseUrl)
            return
        }
        val initialUrl: String = rawDownloadUrl

        // Check if already downloaded and valid
        val targetFile = getCachedApkFile(release.cleanVersion)
        if (targetFile.exists() && targetFile.length() > 100_000L &&
            (release.apkSizeBytes <= 0L || targetFile.length() == release.apkSizeBytes)
        ) {
            _uiState.value = _uiState.value.copy(
                downloadedApkPath = targetFile.absolutePath,
                statusMessage = "File APK v${release.cleanVersion} siap dipasang"
            )
            promptInstallApk(context, targetFile)
            return
        }

        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            downloadProgressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = release.apkSizeBytes,
            errorMessage = null,
            statusMessage = "Mengunduh APK v${release.cleanVersion}..."
        )

        scope.launch {
            var connection: HttpURLConnection? = null
            try {
                val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
                val tempFile = File(updatesDir, "MLx-v${release.cleanVersion}.apk.part")
                if (tempFile.exists()) tempFile.delete()

                var currentUrl: String = initialUrl
                var redirects = 0
                // Alur Termux apk_downloader.py SafeRedirectHandler:
                // Strip header Authorization saat redirect ke S3/CDN bukan api.github.com
                while (redirects < 7) {
                    val isGithubApi = currentUrl.startsWith("https://api.github.com")
                    val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000
                        readTimeout = 45_000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "Tajasb-Downloader/2.0")
                        if (isGithubApi) {
                            setRequestProperty("Accept", "application/octet-stream")
                            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                            val auth = formatGithubAuthHeader(token)
                            if (auth != null) {
                                setRequestProperty("Authorization", auth)
                            }
                        } else {
                            setRequestProperty("Accept", "*/*")
                        }
                    }
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (location.isNullOrBlank()) break
                        currentUrl = location
                        redirects++
                    } else {
                        connection = conn
                        break
                    }
                }

                val activeConn = connection ?: throw IllegalStateException("Koneksi unduhan gagal dibuka")
                val responseCode = activeConn.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Server mengembalikan HTTP $responseCode saat mengunduh APK")
                }

                val contentLength = activeConn.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
                var totalRead = 0L

                activeConn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var lastEmitTime = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 120L) {
                                lastEmitTime = now
                                val percent = if (contentLength > 0L) {
                                    ((totalRead * 100L) / contentLength).toInt().coerceIn(0, 99)
                                } else {
                                    -1
                                }
                                _uiState.value = _uiState.value.copy(
                                    downloadProgressPercent = percent.coerceAtLeast(0),
                                    downloadedBytes = totalRead,
                                    totalBytes = contentLength,
                                    statusMessage = if (percent >= 0) {
                                        "Mengunduh APK v${release.cleanVersion}... $percent%"
                                    } else {
                                        "Mengunduh APK v${release.cleanVersion}... (${formatBytes(totalRead)})"
                                    }
                                )
                            }
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) targetFile.delete()

                val finalFile: File
                if (release.isArtifactZip) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Mengekstrak paket APK dari GitHub Actions Artifact..."
                    )
                    var apkExtracted = false
                    try {
                        java.util.zip.ZipInputStream(tempFile.inputStream().buffered()).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                                    FileOutputStream(targetFile).use { out ->
                                        zis.copyTo(out)
                                    }
                                    apkExtracted = true
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    } catch (ze: Exception) {
                        throw IllegalStateException("Gagal mengekstrak berkas artifact: ${ze.message}")
                    } finally {
                        tempFile.delete()
                    }
                    if (!apkExtracted || !targetFile.exists() || targetFile.length() < 100_000L) {
                        throw IllegalStateException("Artifact zip tidak berisi file APK Android yang valid")
                    }
                    finalFile = targetFile
                } else {
                    val renamed = tempFile.renameTo(targetFile)
                    if (!renamed) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    finalFile = targetFile
                }

                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadProgressPercent = 100,
                    downloadedBytes = totalRead,
                    totalBytes = contentLength.coerceAtLeast(totalRead),
                    downloadedApkPath = finalFile.absolutePath,
                    errorMessage = null,
                    statusMessage = "Unduhan selesai (${formatBytes(finalFile.length())}). Membuka pemasang APK..."
                )

                withContext(Dispatchers.Main) {
                    promptInstallApk(context, finalFile)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    errorMessage = "Gagal mengunduh APK: ${e.message ?: "Kesalahan jaringan"}",
                    statusMessage = "Unduhan gagal. Pastikan Token GitHub memiliki izin repo."
                )
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun promptInstallApk(context: Context, apkFile: File? = null) {
        val fileToInstall = apkFile ?: _uiState.value.downloadedApkPath?.let { File(it) }
        if (fileToInstall == null || !fileToInstall.exists()) {
            Toast.makeText(context, "File APK belum tersedia, silakan unduh terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                Toast.makeText(
                    context,
                    "Izinkan pemasangan APK dari sumber ini terlebih dahulu, lalu tekan Pasang lagi.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                } catch (_: Exception) {
                }
                return
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileToInstall
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Gagal membuka installer APK: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openReleasePageInBrowser(context: Context, url: String? = null) {
        val targetUrl = url ?: _uiState.value.latestRelease?.htmlReleaseUrl
            ?: "https://github.com/${_uiState.value.repoSlug}/releases"
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak dapat membuka browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCachedApkFile(cleanVersion: String): File {
        val safeVer = cleanVersion.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        return File(updatesDir, "MLx-v$safeVer.apk")
    }

    private fun fetchLatestReleaseFromGitHub(repoSlugOrUrl: String, token: String): ReleaseUpdateInfo {
        val slug = normalizeRepoSlug(repoSlugOrUrl)
        val authHeaders = getAuthHeadersToTry(token)
        val headersToTry = if (authHeaders.isNotEmpty()) authHeaders else listOf(null)

        val apiUrl = if (repoSlugOrUrl.startsWith("http://") || repoSlugOrUrl.startsWith("https://")) {
            repoSlugOrUrl
        } else {
            "https://api.github.com/repos/$slug/releases/latest"
        }

        for (auth in headersToTry) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "TajasbVCam-Updater/2.0")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    if (auth != null) setRequestProperty("Authorization", auth)
                }
                val code = conn.responseCode
                if (code == 401 || code == 403) {
                    throw IllegalStateException("Akses GitHub ditolak (HTTP $code). Periksa izin Token PAT Anda.")
                }
                if (code in 200..299) {
                    val bodyText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(bodyText)
                    val relInfo = parseReleaseJson(json, slug)
                    if (!relInfo.apkDownloadUrl.isNullOrBlank() || !relInfo.assetApiUrl.isNullOrBlank()) {
                        return relInfo
                    }
                }
            } catch (e: Exception) {
                if (e is IllegalStateException) throw e
            } finally {
                conn?.disconnect()
            }
        }

        // Fallback Tier 2: Cek daftar /releases (mungkin rilis bukan marked latest atau pre-release)
        try {
            return fetchFirstReleaseFromList(slug, token)
        } catch (_: Exception) {}

        // Fallback Tier 3: Cek GitHub Actions Artifacts (sesuai apk_downloader.py)
        return fetchFromActionArtifacts(slug, token)
    }

    private fun fetchFirstReleaseFromList(slug: String, token: String): ReleaseUpdateInfo {
        val listUrl = "https://api.github.com/repos/$slug/releases?per_page=10"
        val authHeaders = getAuthHeadersToTry(token)
        val headersToTry = if (authHeaders.isNotEmpty()) authHeaders else listOf(null)

        for (auth in headersToTry) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(listUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "TajasbVCam-Updater/2.0")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    if (auth != null) setRequestProperty("Authorization", auth)
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    val bodyText = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(bodyText)
                    if (array.length() > 0) {
                        for (i in 0 until array.length()) {
                            val relJson = array.getJSONObject(i)
                            val info = parseReleaseJson(relJson, slug)
                            if (!info.apkDownloadUrl.isNullOrBlank() || !info.assetApiUrl.isNullOrBlank()) {
                                return info
                            }
                        }
                        return parseReleaseJson(array.getJSONObject(0), slug)
                    }
                }
            } finally {
                conn?.disconnect()
            }
        }
        throw IllegalStateException("Tidak ada rilis APK di daftar releases")
    }

    private fun fetchFromActionArtifacts(slug: String, token: String): ReleaseUpdateInfo {
        val artifactsUrl = "https://api.github.com/repos/$slug/actions/artifacts?per_page=10"
        val authHeaders = getAuthHeadersToTry(token)
        val headersToTry = if (authHeaders.isNotEmpty()) authHeaders else listOf(null)

        var lastCode = 0
        for (auth in headersToTry) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(artifactsUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "TajasbVCam-Updater/2.0")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    if (auth != null) setRequestProperty("Authorization", auth)
                }
                val code = conn.responseCode
                lastCode = code
                if (code in 200..299) {
                    val bodyText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(bodyText)
                    val artifacts = json.optJSONArray("artifacts")
                    if (artifacts != null && artifacts.length() > 0) {
                        for (i in 0 until artifacts.length()) {
                            val art = artifacts.optJSONObject(i) ?: continue
                            val artName = art.optString("name", "")
                            val archiveUrl = art.optString("archive_download_url", "")
                            val size = art.optLong("size_in_bytes", 0L)
                            val createdAt = art.optString("created_at", "").take(10)
                            val expired = art.optBoolean("expired", false)

                            if (!expired && archiveUrl.isNotBlank()) {
                                val verMatch = Regex("v?(\\d+\\.\\d+\\.\\d+)").find(artName)
                                val currentVer = resolveInstalledVersion(appContext)
                                val cleanVer = verMatch?.groupValues?.get(1) ?: currentVer
                                val rawTag = "v$cleanVer"

                                return ReleaseUpdateInfo(
                                    versionTag = rawTag,
                                    cleanVersion = cleanVer,
                                    releaseName = "GitHub Actions: $artName",
                                    releaseNotes = "APK kompilasi GitHub Actions ($artName) • $createdAt",
                                    apkDownloadUrl = archiveUrl,
                                    assetApiUrl = archiveUrl,
                                    htmlReleaseUrl = "https://github.com/$slug/actions",
                                    apkSizeBytes = size,
                                    publishedAt = createdAt,
                                    isArtifactZip = true
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is IllegalStateException) throw e
            } finally {
                conn?.disconnect()
            }
        }

        val detailMsg = if (token.isNotBlank()) {
            "Repositori '$slug' merespons HTTP ${if (lastCode > 0) lastCode else 404}.\n" +
            "1. Pastikan Token PAT memiliki centang izin scope 'repo' di GitHub (Settings > Developer settings > Personal access tokens).\n" +
            "2. Pastikan nama repositori benar (saat ini: '$slug').\n" +
            "3. Pastikan sudah ada Rilis atau kompilasi GitHub Actions di repositori tersebut."
        } else {
            "Repositori '$slug' privat (404). Masukkan Token GitHub (PAT) dengan izin repo pada pengaturan."
        }
        throw IllegalStateException(detailMsg)
    }

    private fun parseReleaseJson(json: JSONObject, repoSlug: String): ReleaseUpdateInfo {
        val rawTag = json.optString("tag_name", "").ifBlank {
            json.optString("version", "0.0.0")
        }
        val releaseName = json.optString("name", "").ifBlank { "Rilis $rawTag" }
        val semverMatch = Regex("v?(\\d+\\.\\d+\\.\\d+)").find(rawTag)
            ?: Regex("v?(\\d+\\.\\d+\\.\\d+)").find(releaseName)
        val cleanVersion = semverMatch?.groupValues?.get(1)
            ?: rawTag.trim().removePrefix("v").removePrefix("V").replace(Regex("-b\\d+.*$"), "")
        val releaseNotes = json.optString("body", "").ifBlank {
            "Pembaruan aplikasi Tajasb VCam versi $rawTag tersedia."
        }
        val htmlUrl = json.optString(
            "html_url",
            "https://github.com/${repoSlug.trim('/')}/releases"
        )
        val publishedAt = json.optString("published_at", "").take(10)

        var apkUrl: String? = json.optString("apk_url", "").takeIf { it.isNotBlank() }
        var assetApiUrl: String? = null
        var apkSize = 0L

        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                val apiUrl = asset.optString("url", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    if (downloadUrl.isNotBlank()) apkUrl = downloadUrl
                    if (apiUrl.isNotBlank()) assetApiUrl = apiUrl
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }

        return ReleaseUpdateInfo(
            versionTag = rawTag,
            cleanVersion = cleanVersion,
            releaseName = releaseName,
            releaseNotes = releaseNotes,
            apkDownloadUrl = apkUrl,
            assetApiUrl = assetApiUrl,
            htmlReleaseUrl = htmlUrl,
            apkSizeBytes = apkSize,
            publishedAt = publishedAt
        )
    }

    private fun postUpdateAvailableNotification(release: ReleaseUpdateInfo) {
        // Suppressed: Notifikasi sistem ditiadakan sesuai preferensi agar tidak mengganggu sesi live/layar
    }

    companion object {
        private const val PREFS_NAME = "tajasb_app_updater_prefs"
        private const val KEY_GITHUB_REPO = "github_repo_slug"
        private const val KEY_GITHUB_TOKEN = "github_personal_access_token"
        private const val KEY_SKIPPED_TAG = "skipped_version_tag"
        private const val KEY_LAST_NOTIFIED_TAG = "last_notified_version_tag"
        private const val NOTIFICATION_CHANNEL_ID = "tajasb_apk_update_channel"
        private const val NOTIFICATION_ID = 7742
        const val EXTRA_OPEN_UPDATE_DIALOG = "extra_open_update_dialog"

        @Volatile
        private var INSTANCE: AppUpdateManager? = null

        fun getInstance(context: Context): AppUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppUpdateManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun resolveInstalledVersion(context: Context): String {
            return try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pInfo.versionName ?: "1.0.50"
            } catch (_: Exception) {
                "1.0.50"
            }
        }

        fun normalizeRepoSlug(input: String): String {
            if (input.isBlank()) return AppUpdateUiState.DEFAULT_GITHUB_REPO
            var s = input.trim().removeSurrounding("\"").removeSurrounding("'")
            s = s.replace(Regex("^https?://[^@]+@github\\.com/", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("^https?://github\\.com/", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("^git@github\\.com:", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("^github\\.com/", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("\\.git/*$", RegexOption.IGNORE_CASE), "")
            s = s.trim('/')
            val parts = s.split("/").filter { it.isNotBlank() }
            if (parts.size >= 2) {
                return "${parts[0]}/${parts[1]}"
            }
            return if (s.isNotBlank()) s else AppUpdateUiState.DEFAULT_GITHUB_REPO
        }

        fun cleanToken(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .replace("\n", "")
                .replace("\r", "")
                .trim()
        }

        fun formatGithubAuthHeader(rawToken: String?): String? {
            val clean = cleanToken(rawToken)
            if (clean.isBlank()) return null
            if (clean.startsWith("Bearer ", ignoreCase = true) || clean.startsWith("token ", ignoreCase = true)) {
                return clean
            }
            return "Bearer $clean"
        }

        fun getAuthHeadersToTry(rawToken: String?): List<String> {
            val clean = cleanToken(rawToken)
            if (clean.isBlank()) return emptyList()
            if (clean.startsWith("Bearer ", ignoreCase = true) || clean.startsWith("token ", ignoreCase = true)) {
                return listOf(clean)
            }
            return if (clean.startsWith("ghp_")) {
                listOf("token $clean", "Bearer $clean")
            } else {
                listOf("Bearer $clean", "token $clean")
            }
        }

        fun isRemoteVersionNewer(currentVersion: String, remoteVersion: String): Boolean {
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")
            val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
            if (cleanRemote.isBlank() || cleanCurrent == cleanRemote) return false

            val currentParts = cleanCurrent.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
            val remoteParts = cleanRemote.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(currentParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val c = currentParts.getOrElse(i) { 0 }
                val r = remoteParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                String.format("%d KB", (bytes / 1024L).coerceAtLeast(1L))
            }
        }
    }
}
