package com.localcharacter.app.domain.lore

import com.localcharacter.app.domain.model.LoreEntry

class LoreMatcher {
    fun match(entries: List<LoreEntry>, recentText: String, limit: Int = 8): List<LoreEntry> = entries
        .asSequence()
        .filter { it.enabled && it.keywords.isNotEmpty() }
        .filter { entry ->
            entry.keywords.any { keyword ->
                if (entry.caseSensitive) recentText.contains(keyword)
                else recentText.contains(keyword, ignoreCase = true)
            }
        }
        .sortedByDescending { it.priority }
        .take(limit)
        .toList()
}
