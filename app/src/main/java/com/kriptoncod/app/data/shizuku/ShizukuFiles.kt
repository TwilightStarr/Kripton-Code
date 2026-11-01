package com.kriptoncod.app.data.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.kriptoncod.app.BuildConfig
import java.io.InputStream
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** Shizuku durumu (kullanıcıya gösterilen adımlar bu sırayla ilerler). */
enum class ShizukuStatus { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

data class ShizukuState(val status: ShizukuStatus, val uid: Int?) {
    val mode: PrivMode get() = PrivProtocol.modeOf(uid)
}

/** Listeleme sonucu. */
sealed interface PrivListing {
    data class Ok(val entries: List<PrivEntry>) : PrivListing
    data class Fail(val reason: PrivFail) : PrivListing
}

enum class PrivFail { NOT_READY, DENIED, NOT_FOUND, NOT_DIR, ERROR }

/**
 * Shizuku üzerinden (adb ya da root) salt-okunur dosya erişimi. Ayarlar'da açılmadıkça ([setEnabled]) Shizuku'ya
 * HİÇ bağlanılmaz. Shizuku yoksa ya da izin verilmemişse çağıranlar normal Scoped Storage / tüm dosyalar erişimine döner.
 *
 * Erişim düzeyi Shizuku'nun nasıl başlatıldığına bağlıdır: root = tüm /data (diğer uygulamalar dâhil);
 * adb = shell yetkisi (/storage/emulated/0/Android/data, /data/local/tmp vb.; diğer uygulamaların /data/data'sı kapalı kalır).
 */
object ShizukuFiles {
    private const val PERMISSION_REQUEST = 4711
    private const val SERVICE_VERSION = 1
    private const val BIND_TIMEOUT_MS = 8_000L
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val _state = MutableStateFlow(ShizukuState(ShizukuStatus.NOT_INSTALLED, null))
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile private var appContext: Context? = null

    @Volatile private var enabled = false

    @Volatile private var service: IPrivFileService? = null

    private var connection: ServiceConnection? = null
    private var waiters = ArrayList<CancellableContinuation<IPrivFileService?>>()
    private var listenersAdded = false
    private val lock = Any()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        refresh()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(requireNotNull(appContext).packageName, PrivFileService::class.java.name))
            .daemon(false)
            .processNameSuffix("privfiles")
            .debuggable(BuildConfig.DEBUG)
            .version(SERVICE_VERSION)
    }

    /** Ayarlardaki anahtar değişince çağrılır; kapatılırsa servis bağlantısı bırakılır. */
    fun setEnabled(context: Context, on: Boolean) {
        appContext = context.applicationContext
        if (on == enabled) {
            if (on) refresh()
            return
        }
        enabled = on
        if (on) {
            synchronized(lock) {
                if (!listenersAdded) {
                    Shizuku.addBinderReceivedListenerSticky(binderReceived)
                    Shizuku.addBinderDeadListener(binderDead)
                    Shizuku.addRequestPermissionResultListener(permissionResult)
                    listenersAdded = true
                }
            }
            refresh()
        } else {
            release()
            synchronized(lock) {
                if (listenersAdded) {
                    Shizuku.removeBinderReceivedListener(binderReceived)
                    Shizuku.removeBinderDeadListener(binderDead)
                    Shizuku.removeRequestPermissionResultListener(permissionResult)
                    listenersAdded = false
                }
            }
            _state.value = ShizukuState(ShizukuStatus.NOT_INSTALLED, null)
        }
    }

    fun isInstalled(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Durumu yeniden hesaplar (Shizuku başlatılınca / izin verilince / ekran öne gelince). */
    fun refresh() {
        val ctx = appContext ?: return
        if (!enabled) return
        val status = when {
            !isInstalled(ctx) && !Shizuku.pingBinder() -> ShizukuStatus.NOT_INSTALLED
            !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
            Shizuku.isPreV11() -> ShizukuStatus.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.NEEDS_PERMISSION
            else -> ShizukuStatus.READY
        }
        val uid = if (status == ShizukuStatus.READY) runCatching { Shizuku.getUid() }.getOrNull() else null
        _state.value = ShizukuState(status, uid)
    }

    /** Shizuku iznini ister (Shizuku kendi onay ekranını gösterir). İstenemiyorsa false. */
    fun requestPermission(): Boolean {
        if (!enabled || !Shizuku.pingBinder() || Shizuku.isPreV11()) return false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refresh()
            return true
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) return false // kullanıcı "bir daha sorma" dedi: Shizuku uygulamasından verilmeli
        return runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }.isSuccess
    }

    /** Shizuku uygulamasını açar (kuruluysa). */
    fun launchShizukuApp(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    // ---- Servis bağlantısı ----

    private suspend fun bound(): IPrivFileService? {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        if (_state.value.status != ShizukuStatus.READY) return null
        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine<IPrivFileService?> { cont ->
                synchronized(lock) { waiters.add(cont) }
                cont.invokeOnCancellation { synchronized(lock) { waiters.remove(cont) } }
                ensureBinding()
            }
        }
    }

    private fun ensureBinding() {
        synchronized(lock) {
            if (connection != null) return
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val s = binder?.takeIf { it.pingBinder() }?.let { IPrivFileService.Stub.asInterface(it) }
                    service = s
                    val pending = synchronized(lock) { waiters.also { waiters = ArrayList() } }
                    pending.forEach { if (it.isActive) it.resumeWith(Result.success(s)) }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                    synchronized(lock) { connection = null }
                }
            }
            connection = conn
            runCatching { Shizuku.bindUserService(userServiceArgs, conn) }.onFailure {
                connection = null
                val pending = waiters.also { waiters = ArrayList() }
                pending.forEach { c -> if (c.isActive) c.resumeWith(Result.success(null)) }
            }
        }
    }

    private fun release() {
        val conn = synchronized(lock) { connection.also { connection = null } }
        service = null
        if (conn != null) runCatching { Shizuku.unbindUserService(userServiceArgs, conn, true) }
        val pending = synchronized(lock) { waiters.also { waiters = ArrayList() } }
        pending.forEach { if (it.isActive) it.resumeWith(Result.success(null)) }
    }

    // ---- İşlemler (hepsi salt-okunur) ----

    /** [path] klasörünün içeriği (klasörler önce, ada göre). Shizuku hazır değilse [PrivFail.NOT_READY]. */
    suspend fun list(path: String, showHidden: Boolean): PrivListing = withContext(Dispatchers.IO) {
        val s = bound() ?: return@withContext PrivListing.Fail(PrivFail.NOT_READY)
        runCatching {
            val all = ArrayList<PrivEntry>()
            var offset = 0
            while (true) {
                val text = s.list(path, offset, PrivProtocol.PAGE)
                PrivProtocol.parseError(text)?.let { return@withContext PrivListing.Fail(failOf(it)) }
                val page = PrivProtocol.parseEntries(text)
                all.addAll(page)
                if (page.size < PrivProtocol.PAGE) break
                offset += PrivProtocol.PAGE
                if (offset > 200_000) break // aşırı büyük klasörlerde bellek koruması
            }
            val ok: PrivListing = PrivListing.Ok(PrivProtocol.arrange(all, showHidden))
            ok
        }.getOrElse { PrivListing.Fail(PrivFail.ERROR) }
    }

    /** Kurulu paketler (veri klasörü seçici için). Hazır değilse boş liste. */
    suspend fun packages(): List<String> = withContext(Dispatchers.IO) {
        val s = bound() ?: return@withContext emptyList()
        runCatching {
            val text = s.packages()
            if (PrivProtocol.parseError(text) != null) emptyList() else PrivProtocol.parsePackages(text)
        }.getOrDefault(emptyList())
    }

    /** Bir paketin veri klasörünün yolu (erişim düzeyine göre). */
    fun appDataPath(pkg: String, externalRoot: String): String = PrivProtocol.appDataPath(pkg, _state.value.uid ?: -1, externalRoot)

    /**
     * Dosyayı salt okunur akış olarak açar; yalnız servis zaten bağlıysa çalışır (Dosyalar penceresi listelerken bağlar).
     * Kapatılınca tanıtıcı da kapanır. Açılamazsa null. Bloklayıcıdır: arka plan iş parçacığından çağır.
     */
    fun openRead(path: String): InputStream? {
        val s = service?.takeIf { it.asBinder().pingBinder() } ?: return null
        val pfd: ParcelFileDescriptor = runCatching { s.openRead(path) }.getOrNull() ?: return null
        return ParcelFileDescriptor.AutoCloseInputStream(pfd)
    }

    /** Boyut (bayt) ya da bilinmiyorsa null. */
    fun sizeOf(path: String): Long? {
        val s = service?.takeIf { it.asBinder().pingBinder() } ?: return null
        val text = runCatching { s.stat(path) }.getOrNull() ?: return null
        if (PrivProtocol.parseError(text) != null) return null
        return text.split('\t').getOrNull(1)?.toLongOrNull()
    }

    private fun failOf(e: PrivError): PrivFail = when (e) {
        PrivError.DENIED -> PrivFail.DENIED
        PrivError.NOT_FOUND -> PrivFail.NOT_FOUND
        PrivError.NOT_DIR -> PrivFail.NOT_DIR
        else -> PrivFail.ERROR
    }
}
