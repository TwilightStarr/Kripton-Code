package com.kriptoncod.app.ui.ais

/** Gizli metin sarmalayıcı: durum nesneleri loglansa/yazdırılsa bile değer sızmaz. */
class SecretText(val value: String) {
    val isEmpty: Boolean get() = value.isEmpty()

    override fun equals(other: Any?): Boolean = other is SecretText && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "***"
}
