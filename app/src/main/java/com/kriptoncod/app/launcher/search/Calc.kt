package com.kriptoncod.app.launcher.search

import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Arama kutusundaki hesap makinesi: `12*(3+4)`, `2^10`, `%` (mod), `sqrt(16)`, `sin(30)` (derece), `pi`.
 * Yalnızca bir ifade gibi görünen metinlerde sonuç verir; düz kelimede null döner (böylece "tarih" gibi aramalar
 * hesap makinesi sanılmaz). Hata ya da sonsuz/NaN sonuçta null. Saf mantıktır.
 */
object Calc {
    fun eval(input: String): Double? {
        val text = input.trim().replace(',', '.').replace('×', '*').replace('÷', '/').replace('−', '-')
        if (text.isEmpty() || text.length > 120) return null
        // En az bir sayı ve (işleç ya da işlev ya da sabit) olmalı; yalnız "42" için sonuç göstermeye gerek yok.
        if (!text.any { it.isDigit() }) return null
        if (text.all { it.isDigit() || it == '.' }) return null
        return try {
            val p = Parser(text)
            val v = p.parseAll()
            if (v.isNaN() || v.isInfinite()) null else v
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** 12.0 -> "12", 0.1+0.2 -> "0.3" (en çok 10 anlamlı hane, gereksiz sıfırlar atılır). */
    fun format(v: Double): String {
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        var s = "%.10f".format(java.util.Locale.ROOT, v)
        s = s.trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseAll(): Double {
            val v = expr()
            skipWs()
            require(i == s.length) { "beklenmeyen karakter" }
            return v
        }

        private fun skipWs() {
            while (i < s.length && s[i] == ' ') i++
        }

        private fun peek(): Char? {
            skipWs()
            return if (i < s.length) s[i] else null
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v += term() }
                    '-' -> { i++; v -= term() }
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = unary()
            while (true) {
                when (peek()) {
                    '*' -> { i++; v *= unary() }
                    '/' -> { i++; v /= unary() }
                    '%' -> { i++; v %= unary() }
                    else -> return v
                }
            }
        }

        private fun unary(): Double = when (peek()) {
            '-' -> { i++; -unary() }
            '+' -> { i++; unary() }
            else -> power()
        }

        private fun power(): Double {
            val base = atom()
            if (peek() == '^') {
                i++
                // Sağdan bağlanır: 2^3^2 = 2^(3^2)
                return base.pow(unary())
            }
            return base
        }

        private fun atom(): Double {
            val c = peek() ?: throw IllegalArgumentException("ifade bitti")
            if (c == '(') {
                i++
                val v = expr()
                require(peek() == ')') { "parantez kapanmadı" }
                i++
                return v
            }
            if (c.isDigit() || c == '.') return number()
            if (c.isLetter()) {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(start, i).lowercase(java.util.Locale.ROOT)
                when (name) {
                    "pi" -> return PI
                    "e" -> return E
                }
                require(peek() == '(') { "bilinmeyen sözcük" }
                i++
                val arg = expr()
                require(peek() == ')') { "parantez kapanmadı" }
                i++
                return function(name, arg)
            }
            throw IllegalArgumentException("beklenmeyen karakter")
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw IllegalArgumentException("sayı okunamadı")
        }

        private fun function(name: String, x: Double): Double = when (name) {
            "sqrt", "kok" -> sqrt(x)
            "abs" -> abs(x)
            "ln" -> ln(x)
            "log" -> log10(x)
            "exp" -> exp(x)
            "sin" -> sin(Math.toRadians(x))
            "cos" -> cos(Math.toRadians(x))
            "tan" -> tan(Math.toRadians(x))
            "asin" -> Math.toDegrees(asin(x))
            "acos" -> Math.toDegrees(acos(x))
            "atan" -> Math.toDegrees(atan(x))
            "round" -> Math.round(x).toDouble()
            "floor" -> floor(x)
            else -> throw IllegalArgumentException("bilinmeyen işlev")
        }
    }
}
