package com.kriptoncod.app.data.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubTokenGuideTest {
    @Test fun defaultUrlAsksOnlyForContentsAndActions() {
        val u = GitHubTokenGuide.createUrl()
        assertTrue(u.startsWith("https://github.com/settings/personal-access-tokens/new?"))
        assertTrue("contents=write" in u)
        assertTrue("actions=read" in u)
        assertFalse("workflows" in u)
        assertTrue("expires_in=90" in u)
    }

    @Test fun workflowsOnlyWhenAsked() {
        assertTrue("workflows=write" in GitHubTokenGuide.createUrl(withWorkflows = true))
    }

    @Test fun descriptionIsUrlEncoded() {
        assertFalse(' ' in GitHubTokenGuide.createUrl())
    }

    @Test fun classicTokensAreFlagged() {
        // Token biçimli dizeler kaynakta düz yazılmaz (sır tarayıcıları gerçek sanır); çalışma anında birleştirilir.
        assertTrue(GitHubTokenGuide.isClassic("ghp" + "_" + "a".repeat(36)))
        assertFalse(GitHubTokenGuide.isClassic("github" + "_pat_" + "b".repeat(30)))
        assertFalse(GitHubTokenGuide.isClassic(""))
        assertEquals("write", GitHubTokenGuide.REQUIRED["contents"])
    }
}
