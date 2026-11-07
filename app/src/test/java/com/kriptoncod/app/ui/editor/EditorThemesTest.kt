package com.kriptoncod.app.ui.editor

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorThemesTest {
    private fun asset(name: String) = File("src/main/assets/textmate/$name.json")

    @Test fun idsAreUniqueAndDefaultIsFirst() {
        val ids = EditorThemes.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(EditorThemes.DEFAULT_ID, ids.first())
        assertEquals(EditorThemes.all.first(), EditorThemes.byId("bilinmeyen"))
        assertEquals(EditorThemes.all.first(), EditorThemes.byId(null))
    }

    @Test fun everyOwnThemeHasPaletteAndValidSyntaxFile() {
        for (t in EditorThemes.all) {
            assertEquals(5, t.swatch.size)
            val syntax = t.syntax ?: continue
            assertTrue("${t.id}: palet yok", t.spec != null)
            val f = asset(syntax)
            assertTrue("${t.id}: ${f.path} yok", f.isFile)
            val root = Json.parseToJsonElement(f.readText()).jsonObject
            assertEquals(syntax, root["name"]?.jsonPrimitive?.content)
            val rules = root["settings"] as JsonArray
            assertTrue(rules.size > 10)
            val first = (rules.first() as JsonObject)["settings"] as JsonObject
            for (key in listOf("background", "foreground", "caret", "lineHighlight", "selection")) {
                assertTrue("${t.id}: $key eksik", first[key]?.jsonPrimitive?.content?.matches(Regex("#[0-9A-Fa-f]{6}")) == true)
            }
        }
    }

    @Test fun syntaxBackgroundMatchesInterfacePalette() {
        for (t in EditorThemes.all) {
            val syntax = t.syntax ?: continue
            val spec = t.spec ?: continue
            val root = Json.parseToJsonElement(asset(syntax).readText()).jsonObject
            val bg = (((root["settings"] as JsonArray).first() as JsonObject)["settings"] as JsonObject)["background"]!!.jsonPrimitive.content
            val argb = spec.background.value.shr(32).toInt() and 0xFFFFFF
            assertEquals("${t.id}: zemin uyuşmuyor", bg.removePrefix("#").uppercase(), "%06X".format(argb))
        }
    }
}
