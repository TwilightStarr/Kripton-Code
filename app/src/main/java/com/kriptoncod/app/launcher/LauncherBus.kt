package com.kriptoncod.app.launcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Etkinlik (MainActivity) ile launcher arasında tek yönlü küçük köprü: başka uygulamadan "Paylaş > Kripton" ile gelen metin.
 * Metin yalnız bellekte durur; tüketilince silinir.
 */
object LauncherBus {
    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    fun share(text: String?) {
        _sharedText.value = text?.takeIf { it.isNotBlank() }?.take(20_000)
    }

    fun consume() {
        _sharedText.value = null
    }
}
