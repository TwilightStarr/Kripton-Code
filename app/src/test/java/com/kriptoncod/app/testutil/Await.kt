package com.kriptoncod.app.testutil

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Durum koşulu sağlanana kadar bekler (Room işleri arka plan iş parçacığında biter). */
fun <T> StateFlow<T>.awaitValue(timeoutMs: Long = 5_000, predicate: (T) -> Boolean): T = runBlocking { withTimeout(timeoutMs) { first(predicate) } }
