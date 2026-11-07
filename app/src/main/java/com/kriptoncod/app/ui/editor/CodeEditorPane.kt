package com.kriptoncod.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kriptoncod.app.R
import com.kriptoncod.app.data.files.AtomicFiles
import com.kriptoncod.app.launcher.ui.LocalInAppMessage
import com.kriptoncod.app.ui.theme.KriptonTheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.subscribeAlways
import java.io.File
import java.util.regex.PatternSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Düzenleyici ile Compose arasındaki gözlenebilir durum. */
@Stable
private class CodeEditorState {
    var editor: CodeEditor? = null
    var scheme: EditorColorScheme? = null
    var modified by mutableStateOf(false)
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)
    var line by mutableIntStateOf(1)
    var column by mutableIntStateOf(1)
    var matchCount by mutableIntStateOf(0)
    var matchIndex by mutableIntStateOf(-1)
    var appliedSyntax: String? = null
    var appliedKey: Int = 0
    var bom: Boolean = false
    var base: Long = 0L
    var appliedFont: Float = -1f

    fun refreshHistory() {
        val e = editor ?: return
        canUndo = e.canUndo()
        canRedo = e.canRedo()
    }

    fun refreshMatches() {
        val s = editor?.searcher ?: return
        if (!s.hasQuery()) {
            matchCount = 0
            matchIndex = -1
        } else {
            matchCount = s.matchedPositionCount
            matchIndex = s.currentMatchedPositionIndex
        }
    }

    fun release() {
        SoraSetup.release(scheme)
        scheme = null
        runCatching { editor?.release() }
        editor = null
    }
}

private class ChangedOnDisk : Exception()

/** Üç nokta menüsüne eklenen dış eylem; [onClick] düzenleyicinin o anki metnini alır. */
class EditorMenuAction(val label: String, val onClick: (text: String) -> Unit)

/** Kodu düzenleyici bölmesi: renklendirme, arama/değiştirme, geri al/yinele, sembol çubuğu, kaydetme. */
@Composable
fun CodeEditorPane(
    file: File,
    onClose: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
    /** Markdown dosyalarında düzenleme ile önizleme arasında geçiş; null ise menüde görünmez. */
    onPreview: (() -> Unit)? = null,
    /** Renklendirme teması (TextMate adı); null ise uygulamanın açık/koyu durumuna göre. */
    syntaxTheme: String? = null,
    /**
     * Editör sekmesi kipi: geri oku yerine kapat düğmesi; sekme değişince kaydedilmemiş metin taslak olarak saklanır
     * ([EditorDrafts]) ve dosya yeniden açılınca geri gelir.
     */
    embedded: Boolean = false,
    onModifiedChange: ((Boolean) -> Unit)? = null,
    extraMenu: List<EditorMenuAction> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val message = LocalInAppMessage.current
    val state = remember(file.path) { CodeEditorState() }
    DisposableEffect(state) {
        onDispose {
            val ed = state.editor
            if (embedded && ed != null) {
                if (state.modified) {
                    EditorDrafts.put(file.path, EditorDrafts.Draft(ed.text.toString(), state.bom, state.base))
                } else {
                    EditorDrafts.remove(file.path)
                }
            }
            state.release()
        }
    }
    LaunchedEffect(state.modified) { onModifiedChange?.invoke(state.modified) }

    var settings by remember { mutableStateOf(EditorPrefs.load(context)) }
    fun updateSettings(s: EditorSettings) {
        settings = s.normalized()
        EditorPrefs.save(context, settings)
    }

    val loaded by produceState<EditorSupport.Loaded?>(null, file.path) {
        value = withContext(Dispatchers.IO) { runCatching { EditorSupport.read(file) }.getOrNull() }
    }
    val l = loaded
    val k = KriptonTheme.colors
    val cs = MaterialTheme.colorScheme

    Column(modifier.fillMaxSize().background(cs.background).imePadding()) {
        if (l == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("…", color = k.muted) }
            return@Column
        }
        if (l.binary) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.editor_binary), color = k.muted, modifier = Modifier.padding(16.dp))
                    TextButton(onClick = onOpenExternal) { Text(stringResource(R.string.desktop_files_open_other)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.editor_close)) }
                }
            }
            return@Column
        }

        val writable = remember(file.path, l) {
            l.safeToWrite && runCatching { file.canWrite() }.getOrDefault(false) &&
                !file.path.startsWith(context.cacheDir.path)
        }
        var lastKnown by remember(file.path, l) {
            mutableStateOf((if (embedded) EditorDrafts.get(file.path)?.base else null) ?: l.lastModified)
        }
        val scopeName = remember(file.name) { EditorSupport.scopeFor(file.name) }

        // --- arama / değiştirme durumu ---
        var showSearch by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        var replacement by remember { mutableStateOf("") }
        var matchCase by remember { mutableStateOf(false) }
        var useRegex by remember { mutableStateOf(false) }
        var wholeWord by remember { mutableStateOf(false) }
        var regexError by remember { mutableStateOf(false) }

        // --- iletişim kutuları ---
        var menuOpen by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showGoto by remember { mutableStateOf(false) }
        var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
        var askOverwrite by remember { mutableStateOf(false) }

        fun runSearch() {
            val ed = state.editor ?: return
            regexError = false
            if (query.isEmpty()) {
                ed.searcher.stopSearch()
            } else {
                val type = when {
                    useRegex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                    wholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                    else -> EditorSearcher.SearchOptions.TYPE_NORMAL
                }
                try {
                    ed.searcher.search(query, EditorSearcher.SearchOptions(type, !matchCase, RegexBackrefGrammar.DEFAULT))
                } catch (_: PatternSyntaxException) {
                    regexError = true
                    ed.searcher.stopSearch()
                }
            }
            state.refreshMatches()
        }

        fun closeSearch() {
            showSearch = false
            state.editor?.searcher?.stopSearch()
            state.refreshMatches()
        }

        fun save(force: Boolean) {
            val ed = state.editor ?: return
            val text = ed.text.toString()
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (!force && file.lastModified() != lastKnown) throw ChangedOnDisk()
                        AtomicFiles.write(file, EditorSupport.encode(text, l.bom))
                        file.lastModified()
                    }
                }
                result.onSuccess {
                    lastKnown = it
                    state.base = it
                    state.modified = false
                    message(context.getString(R.string.editor_saved))
                }.onFailure {
                    if (it is ChangedOnDisk) askOverwrite = true else message(context.getString(R.string.editor_save_failed))
                }
            }
        }

        /** Kaydedilmemiş değişiklik varsa sorar; yoksa doğrudan [action]. */
        fun leave(action: () -> Unit) {
            if (state.modified) pendingLeave = action else action()
        }

        // Sekme kipinde geri tuşu yalnız aramayı kapatır; kaydedilmemiş metin zaten taslak olarak korunur.
        BackHandler(enabled = showSearch || (!embedded && state.modified)) {
            if (showSearch) closeSearch() else leave(onClose)
        }

        // Arama sonuçları arka planda hesaplanır: sayaç aralıklarla yenilenir.
        LaunchedEffect(showSearch, query, matchCase, useRegex, wholeWord) {
            while (showSearch && isActive) {
                state.refreshMatches()
                delay(200)
            }
        }

        // --- üst çubuk ---
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (embedded) {
                Text(
                    (if (state.modified) "● " else "") + EditorSupport.languageLabel(scopeName),
                    style = MaterialTheme.typography.labelLarge,
                    color = k.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            } else {
                IconButton(onClick = { leave(onClose) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desktop_files_up))
                }
                Text(
                    (if (state.modified) "● " else "") + file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (writable) {
                GlyphButton("↶", stringResource(R.string.editor_undo), state.canUndo) {
                    state.editor?.undo()
                    state.refreshHistory()
                }
                GlyphButton("↷", stringResource(R.string.editor_redo), state.canRedo) {
                    state.editor?.redo()
                    state.refreshHistory()
                }
                IconButton(onClick = { save(false) }, enabled = state.modified) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.editor_save))
                }
            }
            IconButton(onClick = { if (showSearch) closeSearch() else showSearch = true }) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.editor_search))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.editor_goto_line)) }, onClick = {
                        menuOpen = false
                        showGoto = true
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.editor_select_all)) }, onClick = {
                        menuOpen = false
                        state.editor?.selectAll()
                    })
                    DropdownMenuItem(
                        text = { Text((if (settings.wordWrap) "✓ " else "") + stringResource(R.string.editor_wrap)) },
                        onClick = {
                            menuOpen = false
                            updateSettings(settings.copy(wordWrap = !settings.wordWrap))
                        },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.editor_settings)) }, onClick = {
                        menuOpen = false
                        showSettings = true
                    })
                    if (onPreview != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_preview)) }, onClick = {
                            menuOpen = false
                            leave(onPreview)
                        })
                    }
                    for (action in extraMenu) {
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                menuOpen = false
                                state.editor?.let { action.onClick(it.text.toString()) }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.desktop_files_open_other)) },
                        onClick = {
                            menuOpen = false
                            leave(onOpenExternal)
                        },
                    )
                }
            }
            if (embedded) {
                IconButton(onClick = { leave(onClose) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.editor_close))
                }
            }
        }
        HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.5f))

        // --- uyarı şeridi (salt okunur nedenleri) ---
        if (!writable) {
            val why = when {
                l.truncated -> R.string.editor_readonly_truncated
                l.lossy -> R.string.editor_readonly_encoding
                else -> R.string.editor_readonly
            }
            Text(
                stringResource(why),
                style = MaterialTheme.typography.labelSmall,
                color = k.muted,
                modifier = Modifier.fillMaxWidth().background(k.nodeFill).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // --- arama çubuğu ---
        if (showSearch) {
            Column(Modifier.fillMaxWidth().background(k.nodeFill).padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactField(
                        value = query,
                        onChange = {
                            query = it
                            runSearch()
                        },
                        hint = stringResource(R.string.editor_search_hint),
                        modifier = Modifier.weight(1f),
                        error = regexError,
                    )
                    Text(
                        if (state.matchCount == 0) stringResource(R.string.editor_no_match) else "${state.matchIndex + 1}/${state.matchCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = k.muted,
                        maxLines = 1,
                    )
                    GlyphButton("▲", stringResource(R.string.editor_prev), state.matchCount > 0) {
                        state.editor?.searcher?.gotoPrevious()
                        state.refreshMatches()
                    }
                    GlyphButton("▼", stringResource(R.string.editor_next), state.matchCount > 0) {
                        state.editor?.searcher?.gotoNext()
                        state.refreshMatches()
                    }
                    IconButton(onClick = { closeSearch() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.editor_close))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToggleChip("Aa", matchCase, stringResource(R.string.editor_match_case)) {
                        matchCase = !matchCase
                        runSearch()
                    }
                    ToggleChip(".*", useRegex, stringResource(R.string.editor_regex)) {
                        useRegex = !useRegex
                        if (useRegex) wholeWord = false
                        runSearch()
                    }
                    ToggleChip("\\b", wholeWord, stringResource(R.string.editor_whole_word)) {
                        wholeWord = !wholeWord
                        if (wholeWord) useRegex = false
                        runSearch()
                    }
                }
                if (writable) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactField(
                            value = replacement,
                            onChange = { replacement = it },
                            hint = stringResource(R.string.editor_replace_hint),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            state.editor?.searcher?.replaceCurrentMatch(replacement)
                            state.refreshMatches()
                        }, enabled = state.matchCount > 0) {
                            Text(stringResource(R.string.editor_replace), maxLines = 1)
                        }
                        TextButton(onClick = {
                            state.editor?.searcher?.replaceAll(replacement)
                            state.refreshMatches()
                        }, enabled = state.matchCount > 0) {
                            Text(stringResource(R.string.editor_replace_all), maxLines = 1)
                        }
                    }
                }
            }
            HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.5f))
        }

        // --- düzenleyici ---
        val chrome = EditorChrome(
            dark = cs.background.luminance() < 0.5f,
            background = cs.background.toArgb(),
            text = cs.onBackground.toArgb(),
            muted = k.muted.toArgb(),
            raised = k.nodeFill.toArgb(),
            border = k.nodeBorder.toArgb(),
            accent = cs.primary.toArgb(),
            danger = k.danger.toArgb(),
            selection = cs.primary.copy(alpha = 0.30f).toArgb(),
            syntax = syntaxTheme,
        )
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                SoraSetup.ensure(ctx)
                CodeEditor(ctx).also { ed ->
                    state.editor = ed
                    ed.typefaceText = android.graphics.Typeface.MONOSPACE
                    ed.setLineSpacing(2f, 1.1f)
                    ed.setDividerWidth(0f)
                    ed.setPinLineNumber(true)
                    // Renk şeması dilden ÖNCE verilir (TextMate dili TextMate şeması ister).
                    SoraSetup.createScheme(chrome)?.let { s ->
                        state.scheme = s
                        ed.colorScheme = s
                    }
                    state.appliedSyntax = chrome.syntaxName()
                    state.appliedKey = chrome.key()
                    ed.setScalable(false)
                    SoraSetup.createLanguage(scopeName)?.let { lang -> ed.setEditorLanguage(lang) }
                    val draft = if (embedded) EditorDrafts.get(file.path) else null
                    state.bom = draft?.bom ?: l.bom
                    state.base = draft?.base ?: l.lastModified
                    ed.setText(draft?.text ?: l.text)
                    ed.isEditable = writable
                    ed.subscribeAlways<ContentChangeEvent> { event ->
                        if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) state.modified = true
                        state.refreshHistory()
                    }
                    ed.subscribeAlways<SelectionChangeEvent> { event ->
                        state.line = event.left.line + 1
                        state.column = event.left.column + 1
                    }
                    // Taslak geri yüklendiyse dosya "değişmiş" sayılır (setText bunu işaretlemez).
                    if (draft != null) {
                        // Taslak düzenleyiciye devredildi; sekme "değişmiş" göstergesi artık canlı durumdan gelir.
                        EditorDrafts.remove(file.path)
                        if (writable) {
                            state.modified = true
                            state.refreshHistory()
                        }
                    }
                }
            },
            update = { ed ->
                val s = settings
                if (state.appliedFont != s.fontSizeSp) {
                    ed.setTextSize(s.fontSizeSp)
                    state.appliedFont = s.fontSizeSp
                }
                if (ed.isWordwrap != s.wordWrap) ed.isWordwrap = s.wordWrap
                if (ed.isLineNumberEnabled != s.lineNumbers) ed.isLineNumberEnabled = s.lineNumbers
                ed.isHighlightCurrentLine = s.highlightLine
                ed.isHighlightBracketPair = s.highlightBrackets
                ed.props.stickyScroll = s.stickyScroll
                ed.props.autoIndent = s.autoIndent
                ed.props.symbolPairAutoCompletion = s.autoClosePairs
                ed.setTabWidth(s.tabWidth)
                ed.nonPrintablePaintingFlags = if (s.showWhitespace) {
                    CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
                        CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR
                } else {
                    0
                }
                runCatching { ed.getComponent<EditorAutoCompletion>().isEnabled = s.autoComplete && writable }
                // Renklendirme teması değiştiyse yeni şema; yalnız renkler değiştiyse mevcut şema tazelenir.
                val current = state.scheme
                val key = chrome.key()
                if (current == null || state.appliedSyntax != chrome.syntaxName()) {
                    SoraSetup.release(current)
                    SoraSetup.createScheme(chrome)?.let { ns ->
                        state.scheme = ns
                        ed.colorScheme = ns
                    }
                } else if (state.appliedKey != key) {
                    chrome.applyTo(current)
                    ed.invalidate()
                }
                state.appliedKey = key
                state.appliedSyntax = chrome.syntaxName()
            },
        )

        // --- sembol çubuğu ---
        if (writable && settings.symbolBar) {
            HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.5f))
            Row(
                Modifier.fillMaxWidth().background(k.nodeFill).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SymbolKey("⇥", stringResource(R.string.editor_tab)) { state.editor?.indentOrCommitTab() }
                SymbolKey("←", null) { state.editor?.let { moveCursor(it, -1) } }
                SymbolKey("→", null) { state.editor?.let { moveCursor(it, 1) } }
                for (sym in SYMBOLS) SymbolKey(sym, null) { state.editor?.commitText(sym) }
            }
        }

        // --- durum çubuğu ---
        HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.5f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.editor_status, state.line, state.column), style = MaterialTheme.typography.labelSmall, color = k.muted)
            Text("UTF-8", style = MaterialTheme.typography.labelSmall, color = k.muted)
            Text(EditorSupport.languageLabel(scopeName), style = MaterialTheme.typography.labelSmall, color = k.muted)
        }

        // --- iletişim kutuları ---
        if (showGoto) {
            GotoDialog(
                maxLine = state.editor?.text?.lineCount ?: 1,
                onDismiss = { showGoto = false },
                onGo = { line ->
                    showGoto = false
                    state.editor?.jumpToLine(line - 1)
                },
            )
        }
        if (showSettings) {
            SettingsDialog(settings, onChange = ::updateSettings, onDismiss = { showSettings = false })
        }
        val leaving = pendingLeave
        if (leaving != null) {
            AlertDialog(
                onDismissRequest = { pendingLeave = null },
                title = { Text(stringResource(R.string.editor_unsaved_title)) },
                text = { Text(stringResource(R.string.editor_unsaved_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingLeave = null
                        scope.launch {
                            val ed = state.editor
                            if (ed != null) {
                                val text = ed.text.toString()
                                val ok = withContext(Dispatchers.IO) { runCatching { AtomicFiles.write(file, EditorSupport.encode(text, l.bom)) }.isSuccess }
                                if (ok) {
                                    state.modified = false
                                    leaving()
                                } else {
                                    message(context.getString(R.string.editor_save_failed))
                                }
                            }
                        }
                    }) { Text(stringResource(R.string.editor_save)) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            pendingLeave = null
                            state.modified = false
                            leaving()
                        }) { Text(stringResource(R.string.editor_discard)) }
                        TextButton(onClick = { pendingLeave = null }) { Text(stringResource(R.string.editor_cancel)) }
                    }
                },
            )
        }
        if (askOverwrite) {
            AlertDialog(
                onDismissRequest = { askOverwrite = false },
                title = { Text(stringResource(R.string.editor_overwrite_title)) },
                text = { Text(stringResource(R.string.editor_overwrite_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        askOverwrite = false
                        save(true)
                    }) { Text(stringResource(R.string.editor_overwrite)) }
                },
                dismissButton = { TextButton(onClick = { askOverwrite = false }) { Text(stringResource(R.string.editor_cancel)) } },
            )
        }
    }
}

private val SYMBOLS = listOf("{", "}", "(", ")", "[", "]", "<", ">", "\"", "'", ";", ":", "=", "+", "-", "*", "/", "\\", "|", "&", "!", "?", "_", "$", "#", "@", "%", "~", "^", ",", ".")

private fun moveCursor(ed: CodeEditor, delta: Int) {
    val c = ed.cursor
    var line = c.leftLine
    var col = c.leftColumn + delta
    val text = ed.text
    if (col < 0) {
        if (line == 0) return
        line--
        col = text.getLine(line).length
    } else if (col > text.getLine(line).length) {
        if (line >= text.lineCount - 1) return
        line++
        col = 0
    }
    ed.setSelection(line, col)
}

@Composable
private fun GlyphButton(glyph: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val k = KriptonTheme.colors
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (enabled) MaterialTheme.colorScheme.onBackground else k.muted.copy(alpha = 0.4f), fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
private fun SymbolKey(label: String, description: String?, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 38.dp, height = 36.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace, fontSize = 17.sp, maxLines = 1)
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, description: String, onClick: () -> Unit) {
    val k = KriptonTheme.colors
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) cs.primary else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) cs.primary else k.nodeBorder), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = FontFamily.Monospace, color = if (selected) cs.onPrimary else cs.onBackground, fontSize = 13.sp, maxLines = 1)
        Text(description, color = if (selected) cs.onPrimary else k.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun CompactField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, error: Boolean = false) {
    val k = KriptonTheme.colors
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onBackground),
        cursorBrush = SolidColor(cs.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(cs.background)
                    .border(BorderStroke(1.dp, if (error) k.danger else k.nodeBorder), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (value.isEmpty()) Text(hint, color = k.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
private fun GotoDialog(maxLine: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val line = text.toIntOrNull()?.takeIf { it in 1..maxLine }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_goto_line)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.editor_goto_hint, maxLine), style = MaterialTheme.typography.labelMedium, color = KriptonTheme.colors.muted)
                BasicTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(9) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, KriptonTheme.colors.nodeBorder), RoundedCornerShape(8.dp)).padding(10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { line?.let(onGo) }, enabled = line != null) { Text(stringResource(R.string.editor_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

@Composable
private fun SettingsDialog(s: EditorSettings, onChange: (EditorSettings) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_settings)) },
        text = {
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.editor_font_size) + ": " + s.fontSizeSp.toInt(), Modifier.weight(1f))
                    GlyphButton("−", "", s.fontSizeSp > EditorSettings.MIN_FONT) { onChange(s.copy(fontSizeSp = s.fontSizeSp - EditorSettings.FONT_STEP)) }
                    GlyphButton("+", "", s.fontSizeSp < EditorSettings.MAX_FONT) { onChange(s.copy(fontSizeSp = s.fontSizeSp + EditorSettings.FONT_STEP)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.editor_tab_width), Modifier.weight(1f))
                    for (n in listOf(2, 4, 8)) ToggleChip(n.toString(), s.tabWidth == n, "") { onChange(s.copy(tabWidth = n)) }
                }
                SwitchRow(stringResource(R.string.editor_wrap), s.wordWrap) { onChange(s.copy(wordWrap = it)) }
                SwitchRow(stringResource(R.string.editor_line_numbers), s.lineNumbers) { onChange(s.copy(lineNumbers = it)) }
                SwitchRow(stringResource(R.string.editor_whitespace), s.showWhitespace) { onChange(s.copy(showWhitespace = it)) }
                SwitchRow(stringResource(R.string.editor_sticky), s.stickyScroll) { onChange(s.copy(stickyScroll = it)) }
                SwitchRow(stringResource(R.string.editor_hl_line), s.highlightLine) { onChange(s.copy(highlightLine = it)) }
                SwitchRow(stringResource(R.string.editor_hl_brackets), s.highlightBrackets) { onChange(s.copy(highlightBrackets = it)) }
                SwitchRow(stringResource(R.string.editor_auto_indent), s.autoIndent) { onChange(s.copy(autoIndent = it)) }
                SwitchRow(stringResource(R.string.editor_auto_close), s.autoClosePairs) { onChange(s.copy(autoClosePairs = it)) }
                SwitchRow(stringResource(R.string.editor_autocomplete), s.autoComplete) { onChange(s.copy(autoComplete = it)) }
                SwitchRow(stringResource(R.string.editor_symbol_bar), s.symbolBar) { onChange(s.copy(symbolBar = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_ok)) } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
