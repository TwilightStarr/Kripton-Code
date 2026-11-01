package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.KChip

/**
 * Arşiv: bitmiş işlerin hepsi tek yerde. Üstte iki alt sekme: Geçmiş (run kayıtları) ve Zip'ler
 * (GitHub'dan çekilen projeler, AI değişiklikleri, run sonuç zip'leri). Seçili sekme döndürmede korunur.
 */
@Composable
fun ArchiveScreen(container: AppContainer, onOpenRun: (Long) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KChip(stringResource(R.string.nav_history), tab == 0, { tab = 0 })
            KChip(stringResource(R.string.nav_zips), tab == 1, { tab = 1 })
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) HistoryScreen(onOpenRun = onOpenRun) else ZipsScreen(container = container)
        }
    }
}
