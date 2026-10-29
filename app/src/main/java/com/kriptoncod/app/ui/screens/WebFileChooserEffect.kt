package com.kriptoncod.app.ui.screens

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.kriptoncod.app.data.web.WebFileChooser

/**
 * Bu ekran görünürken web sayfasındaki "dosya ekle" düğmesi sistemin dosya seçicisini açar; seçilen dosyalar sayfaya verilir.
 * Ekrandan ayrılınca (ya da seçici iptal edilince) bekleyen istek null ile kapatılır; aksi hâlde sayfadaki düğme bir daha çalışmaz.
 */
@Composable
fun WebFileChooserEffect() {
    val pending = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = pending.value
        pending.value = null
        cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
    }
    DisposableEffect(launcher) {
        WebFileChooser.handler = { params, callback ->
            pending.value?.onReceiveValue(null) // önceki açık istek varsa iptal et
            pending.value = callback
            val ok = runCatching { launcher.launch(params.createIntent()) }.isSuccess
            if (!ok) {
                pending.value = null
                callback.onReceiveValue(null)
            }
            true
        }
        onDispose {
            WebFileChooser.handler = null
            pending.value?.onReceiveValue(null)
            pending.value = null
        }
    }
}
