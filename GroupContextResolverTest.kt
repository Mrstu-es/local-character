package com.localcharacter.app.domain.group

import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.GroupContext
import com.localcharacter.app.domain.model.GroupLorePolicy
import com.localcharacter.app.domain.model.GroupParticipantContext
import com.localcharacter.app.domain.model.LoreEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupContextResolverTest {
    private val resolver = GroupContextResolver()
    private val character = Character("c", "Nora", scenario = "escenario de la ficha")

    @Test fun participantScenarioOverridesGroupAndCard() {
        val result = resolver.resolve(
            character,
            GroupContext("g", scenario = "escenario compartido"),
            GroupParticipantContext("g", "c", scenarioOverride = "escenario de Nora"),
            emptyList(),
        )
        assertEquals("escenario de Nora", result.scenario)
    }

    @Test fun groupScenarioOverridesCardWhenParticipantIsEmpty() {
        val result = resolver.resolve(character, GroupContext("g", scenario = "escenario compartido"), null, emptyList())
        assertEquals("escenario compartido", result.scenario)
    }

    @Test fun adaptiveLoreDropsContradictoryMagic() {
        val lore = listOf(
            LoreEntry("magic", "c", listOf("magia"), "Nora controla magia antigua"),
            LoreEntry("food", "c", listOf("comida"), "Nora cocina muy bien"),
        )
        val result = resolver.resolve(character, GroupContext("g", worldRules = "Mundo real, sin magia"), null, lore)
        assertTrue(result.lore.none { it.id == "magic" })
        assertTrue(result.lore.any { it.id == "food" })
    }

    @Test fun disabledLoreDoesNotLeakCharacterEntries() {
        val result = resolver.resolve(character, GroupContext("g", lorePolicy = GroupLorePolicy.DISABLED), null, listOf(LoreEntry("x", "c", emptyList(), "secreto")))
        assertTrue(result.lore.isEmpty())
    }
}
