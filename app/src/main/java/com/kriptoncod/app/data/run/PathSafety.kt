package com.kriptoncod.app.data.run

/** AI üretimi dosya yollarını doğrular: dışarı taşma, mutlak yol, tehlikeli karakter yok. */
object PathSafety {

    sealed interface Result {
        /** [normalized] depo köküne göre göreli, `/` ayraçlı güvenli yoldur. */
        data class Safe(val normalized: String) : Result
        data class Rejected(val reason: String) : Result
    }

    private val ILLEGAL = Regex("[\\\\:*?\"<>|\\u0000-\\u001F]")
    private val ALLOWED = Regex("^[A-Za-z0-9_./-]+$")

    /**
     * [raw] yolunu doğrular ve normalize eder.
     * - `..` bileşenleri reddedilir.
     * - Sadece harf, rakam, `_`, `-`, `.`, `/` içerebilir.
     * - Başlangıç `/` veya `./` kırpılır.
     */
    fun validate(raw: String): Result {
        val path = raw.trim().removePrefix("/").removePrefix("./")
        if (path.isEmpty()) return Result.Rejected("Boş yol")
        if (path.startsWith("..") || "/../" in path || path.endsWith("/..")) {
            return Result.Rejected("Üst dizin referansı: $raw")
        }
        if (ILLEGAL.containsMatchIn(path)) return Result.Rejected("Geçersiz karakter: $raw")
        if (!ALLOWED.matches(path)) return Result.Rejected("İzin verilmeyen karakter: $raw")
        if (path.split('/').any { it.isEmpty() || it == "." }) {
            return Result.Rejected("Boş dizin bileşeni: $raw")
        }
        return Result.Safe(path)
    }

    /** [path]'in dosya uzantısını döndürür (ör. "kt", "py"); yoksa boş string. */
    fun extensionOf(path: String): String = path.substringAfterLast('.', "")
}
