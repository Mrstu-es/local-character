package com.localcharacter.app.domain.conversation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateVariableResolverAndroidTest {
    @Test
    fun resolvesVariablesWithAndroidRegexEngine() {
        assertEquals(
            "Alya conoce a Tadeo; {{unknown}} y {{username}} quedan intactos.",
            TemplateVariableResolver.resolve(
                "{{char}} conoce a {{ user }}; {{unknown}} y {{username}} quedan intactos.",
                characterName = "Alya",
                userName = "Tadeo",
            ),
        )
    }
}
