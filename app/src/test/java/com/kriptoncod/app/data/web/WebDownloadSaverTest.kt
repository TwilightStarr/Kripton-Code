package com.kriptoncod.app.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebDownloadSaverTest {
    @Test fun readsQuotedAndPlainFilename() {
        assertEquals("proje.zip", WebDownloadSaver.filenameFromDisposition("attachment; filename=\"proje.zip\""))
        assertEquals("proje.zip", WebDownloadSaver.filenameFromDisposition("attachment; filename=proje.zip; size=4"))
    }

    @Test fun readsUtf8StarFilenameFirst() {
        val cd = "attachment; filename=\"fallback.zip\"; filename*=UTF-8''g%C3%BCncel%20proje.zip"
        assertEquals("güncel proje.zip", WebDownloadSaver.filenameFromDisposition(cd))
    }

    @Test fun noFilenameGivesNull() {
        assertNull(WebDownloadSaver.filenameFromDisposition(null))
        assertNull(WebDownloadSaver.filenameFromDisposition("attachment"))
    }

    @Test fun sanitizeStripsPathsAndBadChars() {
        assertEquals("passwd", WebDownloadSaver.sanitizeName("../../etc/passwd"))
        assertEquals("a_b_c.zip", WebDownloadSaver.sanitizeName("a:b*c.zip"))
        assertEquals("download", WebDownloadSaver.sanitizeName("   "))
        assertEquals("download", WebDownloadSaver.sanitizeName(null))
    }
}
