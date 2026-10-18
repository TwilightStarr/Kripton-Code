package com.kriptoncod.app.data.ai

/**
 * Üretim sürerken gelen metin parçalarının (canlı akış) alıcısı.
 *
 * Yalnız GÖSTERİM içindir: nihai çıktı her zaman üreticinin döndürdüğü metindir (normalizer, devam
 * birleştirme ve doğrulamadan geçmiş hâli). Bu yüzden canlı metin nihaiden biraz farklı olabilir.
 * Çağrılar herhangi bir iş parçacığından gelebilir; uygulamalar iş parçacığı güvenli olmalıdır.
 */
interface StreamSink {
    /** Yeni görünür metin parçası (muhakeme parçaları buraya gelmez). */
    fun append(delta: String)

    /** Üretim baştan yeniden başlıyor (otomatik yeniden deneme); biriken canlı metin atılmalı. */
    fun reset()

    companion object {
        /** Akış istemeyen çağıranlar için: üreticiler bunu görünce akışsız (tek yanıt) kipte çalışır. */
        val NONE: StreamSink = object : StreamSink {
            override fun append(delta: String) = Unit
            override fun reset() = Unit
        }
    }
}
