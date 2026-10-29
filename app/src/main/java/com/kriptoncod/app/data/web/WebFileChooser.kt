package com.kriptoncod.app.data.web

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

/**
 * Web sayfasındaki "dosya ekle" düğmesi ([WebChromeClient.onShowFileChooser]) ile ekranın dosya seçicisi arasındaki köprü.
 * Seçiciyi yalnız ekranda görünen, kullanıcının elle gezdiği sayfa açabilir; ekran [handler]'ı kurar, ayrılırken kaldırır.
 * Otomasyon dosyayı betikle ekler (WebScripts.attach*); seçiciye hiç ihtiyaç duymaz.
 */
object WebFileChooser {
    @Volatile
    var handler: ((WebChromeClient.FileChooserParams, ValueCallback<Array<Uri>>) -> Boolean)? = null
}
