package com.localcharacter.app.domain.group

import com.localcharacter.app.domain.model.Character
import com.localcharacter.app.domain.model.GroupContext
import com.localcharacter.app.domain.model.GroupLorePolicy
import com.localcharacter.app.domain.model.GroupParticipantContext
import com.localcharacter.app.domain.model.LoreEntry

data class ResolvedGroupContext(
    val scenario: String,
    val hardRules: String,
    val participantInstructions: String,
    val lore: List<LoreEntry>,
)

/** Deterministic precedence: group rules/context > participant override > character card scenario. */
class GroupContextResolver {
    fun resolve(
        character: Character,
        context: GroupContext,
        participant: GroupParticipantContext?,
        lore: List<LoreEntry>,
    ): ResolvedGroupContext {
        val scenario = participant?.scenarioOverride?.trim().takeUnless { it.isNullOrBlank() }
            ?: context.scenario.trim().takeUnless { it.isNullOrBlank() }
            ?: character.scenario
        val rules = listOf(
            context.description,
            context.worldRules,
            context.initialSituation,
            context.currentLocation.takeIf(String::isNotBlank)?.let { "Current location: $it" },
            context.currentSituation,
            context.stateSummary,
        ).filter { !it.isNullOrBlank() }.joinToString("\n")
        val participantInstructions = participant?.let {
            listOf(
                it.role.takeIf(String::isNotBlank)?.let { value -> "Role: $value" },
                it.relationshipToUser.takeIf(String::isNotBlank)?.let { value -> "Relationship to user: $value" },
                it.relationshipToGroup.takeIf(String::isNotBlank)?.let { value -> "Relationship to group: $value" },
                it.notes.takeIf(String::isNotBlank),
            ).filterNotNull().joinToString("\n")
        }.orEmpty()
        val filteredLore = when (context.lorePolicy) {
            GroupLorePolicy.DISABLED -> emptyList()
            GroupLorePolicy.ORIGINAL -> lore
            GroupLorePolicy.ADAPTIVE -> lore.filterNot { contradicts(it.content, rules) }
        }
        return ResolvedGroupContext(scenario, rules, participantInstructions, filteredLore)
    }

    private fun contradicts(lore: String, rules: String): Boolean {
        val text = rules.lowercase()
        val item = lore.lowercase()
        val noMagic = listOf("sin magia", "no magic", "mundo real", "real world", "sin poderes", "no powers", "sin sobrenatural", "no supernatural")
        return noMagic.any { marker -> marker in text } && listOf("magia", "magic", "poder", "power", "sobrenatural", "supernatural", "hechizo", "spell").any { it in item }
    }
}
