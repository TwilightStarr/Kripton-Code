package com.kriptoncod.app.ui.desktop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.OutputMarkdown
import com.kriptoncod.app.ui.components.OutputMarkdownView
import com.kriptoncod.app.ui.editor.CodeEditorPane
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Uygulama içi görüntüleyici/düzenleyici: kod ve metin dosyaları Sora Editor ile (renklendirme, düzenleme, arama, kaydetme),
 * markdown önce önizleme olarak (isteğe bağlı düzenleme), PDF (PdfRenderer) ve görüntü (yakınlaştırma).
 * Başka türler için [onOpenExternal].
 */
@Composable
fun FileViewer(file: File, kind: FileKind, onClose: () -> Unit, onOpenExternal: () -> Unit) {
    // Metin dosyaları tam ekran düzenleyicide açılır (kendi üst çubuğu var).
    if (kind == FileKind.TEXT) {
        CodeEditorPane(file = file, onClose = onClose, onOpenExternal = onOpenExternal)
        return
    }
    var editing by remember(file.path) { mutableStateOf(false) }
    var version by remember(file.path) { mutableIntStateOf(0) }
    if (kind == FileKind.MARKDOWN && editing) {
        CodeEditorPane(
            file = file,
            onClose = { editing = false; version++; onClose() },
            onOpenExternal = onOpenExternal,
            onPreview = { editing = false; version++ },
        )
        return
    }
    val k = KriptonTheme.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desktop_files_up))
            }
            Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (kind == FileKind.MARKDOWN) {
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.editor_edit), maxLines = 1) }
            }
            TextButton(onClick = onOpenExternal) { Text(stringResource(R.string.desktop_files_open_other), maxLines = 1) }
        }
        androidx.compose.material3.HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.5f))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (kind) {
                FileKind.MARKDOWN -> TextBody(file, kind, version)
                FileKind.PDF -> PdfBody(file)
                FileKind.IMAGE -> ImageBody(file)
                else -> Unit
            }
        }
    }
}

@Composable
private fun BoxScope.TextBody(file: File, kind: FileKind, version: Int) {
    val loaded by produceState<Pair<List<String>, Boolean>?>(null, file.path, version) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val (text, cut) = FileOps.readText(file.inputStream())
                val md = if (kind == FileKind.MARKDOWN) text else FileOps.asFencedMarkdown(text, file.name)
                OutputMarkdown.segments(md) to cut
            }.getOrNull() ?: (emptyList<String>() to false)
        }
    }
    val l = loaded
    when {
        l == null -> Unit

        l.first.isEmpty() -> Text(stringResource(R.string.desktop_files_unreadable), Modifier.align(Alignment.Center).padding(16.dp))

        else -> Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            if (l.second) {
                Text(
                    stringResource(R.string.desktop_files_truncated),
                    style = MaterialTheme.typography.labelSmall,
                    color = KriptonTheme.colors.muted,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            OutputMarkdownView(l.first)
        }
    }
}

@Composable
private fun BoxScope.ImageBody(file: File) {
    val bmp by produceState<Bitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    val b = bmp
    if (b == null) {
        Text(stringResource(R.string.desktop_files_unreadable), Modifier.align(Alignment.Center).padding(16.dp), color = KriptonTheme.colors.muted)
    } else {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        if (scale == 1f) {
                            ox = 0f
                            oy = 0f
                        } else {
                            ox += pan.x
                            oy += pan.y
                        }
                    }
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy),
        )
    }
}

/** PdfRenderer aynı anda tek sayfa açar ve iş parçacığı güvenli değildir: sayfalar sırayla, kilit altında çizilir. */
@Composable
private fun BoxScope.PdfBody(file: File) {
    val holder by produceState<PdfHolder?>(null, file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfHolder(pfd, PdfRenderer(pfd))
            }.getOrNull()
        }
    }
    DisposableEffect(holder) { onDispose { holder?.close() } }
    val h = holder
    if (h == null) {
        Text(stringResource(R.string.desktop_files_unreadable), Modifier.align(Alignment.Center).padding(16.dp), color = KriptonTheme.colors.muted)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items((0 until h.renderer.pageCount).toList(), key = { it }) { index ->
            val page by produceState<Bitmap?>(null, index) { value = h.render(index) }
            val p = page
            if (p == null) {
                Box(Modifier.fillMaxWidth().size(240.dp), contentAlignment = Alignment.Center) { Text("…", color = KriptonTheme.colors.muted) }
            } else {
                Image(p.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private class PdfHolder(private val pfd: ParcelFileDescriptor, val renderer: PdfRenderer) {
    private val lock = Mutex()
    private var closed = false

    suspend fun render(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) return@withLock null
            runCatching {
                renderer.openPage(index).use { page ->
                    val w = 1080
                    val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }.getOrNull()
        }
    }

    fun close() {
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}
