package com.kriptoncod.app.data.settings

/** Masaüstü modunun kullanıcı ayarları (hepsi Ayarlar > Masaüstü modu'ndan düzenlenir). */
data class DesktopPrefs(
    /** Sistem çubuklarını gizle (tam ekran). */
    val fullscreen: Boolean = true,
    /** Tam ekranda alt çubuğu (dock) da gizle; alt kenardaki tutamaçla geçici açılır. */
    val fullscreenHidesDock: Boolean = true,
    /** Tam ekran olmasa da alt çubuğu hep gizle (tutamaçla geçici açılır). */
    val dockAutoHide: Boolean = false,
    /** Alt çubukta açık sekmelerin adı görünsün (kapalıysa yalnız simge). */
    val dockLabels: Boolean = true,
    val showClock: Boolean = true,
    /** Pencerelerin altındaki ipucu yazıları (varsayılan kapalı). */
    val showHints: Boolean = false,
    /** Dosyalar penceresinin varsayılanları (pencerede o an değiştirilebilir). */
    val filesHidden: Boolean = false,
    val filesExt: Boolean = true,
    val filesGrid: Boolean = false,
    /** Pencere/sekme animasyonları (açılış, sekme geçişi, kapanış). Genel "Animasyonlar" anahtarı kapalıysa zaten oynamaz. */
    val windowAnimations: Boolean = true,
    /** Geçiş hızı çarpanı: 1 = normal, 2 = iki kat hızlı (süreler yarıya iner), 0.5 = yarı hızda (süreler iki katına çıkar). */
    val motionSpeed: Float = 1f,
    /** Sistemin "Animatör süre ölçeği"ni (Geliştirici seçenekleri) de uygula; 0 ise animasyon oynamaz. */
    val followSystemAnimatorScale: Boolean = true,
) {
    companion object {
        const val MOTION_SPEED_MIN = 0.5f
        const val MOTION_SPEED_MAX = 2f

        /** Kayıtlı/gelen hız değerini geçerli aralığa çeker (bozuk ya da sonsuz değer → 1). */
        fun cleanSpeed(v: Float): Float = if (v.isNaN() || v.isInfinite()) 1f else v.coerceIn(MOTION_SPEED_MIN, MOTION_SPEED_MAX)
    }
}
