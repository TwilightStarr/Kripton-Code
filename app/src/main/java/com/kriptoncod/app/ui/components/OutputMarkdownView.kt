package com.kriptoncod.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * Hazırlanmış bölümleri ([OutputMarkdown.segments]) markdown olarak, kod bloklarını renklendirerek çizer.
 * Her bölüm tembel listede ayrı bir öğedir; böylece çok uzun çıktılar da akıcı kaydırılır.
 * Küçük bölümler eşzamanlı ayrıştırılır (kaydırırken "yükleniyor" titremesi olmaz).
 */
@Composable
fun OutputMarkdownView(segments: List<String>, modifier: Modifier = Modifier) {
    val components = markdownComponents(
        codeBlock = highlightedCodeBlock,
        codeFence = highlightedCodeFence,
    )
    SelectionContainer {
        LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(segments.size) { i ->
                val text = segments[i]
                val state = rememberMarkdownState(text, immediate = text.length < IMMEDIATE_LIMIT)
                Markdown(state, components = components)
            }
        }
    }
}

private const val IMMEDIATE_LIMIT = 6_000
