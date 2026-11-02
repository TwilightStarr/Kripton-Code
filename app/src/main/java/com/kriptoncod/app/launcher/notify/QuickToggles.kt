package com.kriptoncod.app.launcher.notify

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bildirim panelinin hızlı düğmeleri. Android 10+ uygulamaların Wi-Fi/Bluetooth'u doğrudan açıp kapatmasına izin vermez;
 * bu düğmeler sistemin kendi hızlı panelini açar. El feneri izinsiz doğrudan çalışır.
 */
object QuickToggles {
    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()
    private var registered = false

    private fun torchId(cm: CameraManager): String? = runCatching {
        cm.cameraIdList.firstOrNull { id ->
            val c = cm.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    /** El fenerinin durumunu izlemeye başlar (bir kez). */
    fun watchTorch(context: Context) {
        if (registered) return
        val cm = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        registered = true
        runCatching {
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId(cm)) _torch.value = enabled
                }
            }, null)
        }
    }

    /** Cihazda flaş var mı? */
    fun hasTorch(context: Context): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return torchId(cm) != null
    }

    fun toggleTorch(context: Context): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        val id = torchId(cm) ?: return false
        return runCatching {
            cm.setTorchMode(id, !_torch.value)
            true
        }.getOrDefault(false)
    }

    /** Ayar panelleri: ilk açılabilen intent kullanılır. */
    fun internetPanel(): List<Intent> = listOf(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), Intent(Settings.ACTION_WIFI_SETTINGS))

    fun bluetooth(): List<Intent> = listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun volume(): List<Intent> = listOf(Intent(Settings.Panel.ACTION_VOLUME))

    fun systemSettings(): List<Intent> = listOf(Intent(Settings.ACTION_SETTINGS))

    fun start(context: Context, intents: List<Intent>): Boolean =
        intents.any { i -> runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
}
