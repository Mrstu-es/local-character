package com.localcharacter.app.domain.conversation

import com.localcharacter.app.domain.model.CharacterContentOverride
import com.localcharacter.app.domain.model.ContentMode
import com.localcharacter.app.domain.model.TtsReadMode

enum class ComposerMode { NORMAL, ACTION, NARRATION }

enum class GenerationMode { NORMAL_REPLY, CHARACTER_CONTINUE }

object GenerationModeResolver {
    fun temporaryInstruction(mode: GenerationMode, characterName: String, userName: String): String? = when (mode) {
        GenerationMode.NORMAL_REPLY -> null
        GenerationMode.CHARACTER_CONTINUE -> """
            Continue the current scene naturally as $characterName. $userName has not spoken again.
            You may speak, react, perform an action, or advance the immediate situation naturally.
            Stay consistent with the character, relationship, memories, unresolved events, lore, and recent scene.
            Do not write dialogue, thoughts, choices, or decisions for $userName. Do not repeat the previous response.
        """.trimIndent()
    }
}

object ActionModeFormatter {
    fun format(text: String, mode: ComposerMode): String {
        val trimmed = text.trim()
        if (mode != ComposerMode.ACTION || trimmed.isBlank()) return trimmed
        val alreadyWrapped = trimmed.length >= 4 && trimmed.startsWith("**") && trimmed.endsWith("**")
        return if (alreadyWrapped) trimmed else "**$trimmed**"
    }
}

/** Cleans model output so every character uses the same readable roleplay layout. */
object RoleplayTextFormatter {
    private val thinkingBlock = Regex("<(?:think|thinking|analysis)\\b[^>]*>[\\s\\S]*?</(?:think|thinking|analysis)>", RegexOption.IGNORE_CASE)
    private val unfinishedThinkingBlock = Regex("<(?:think|thinking|analysis)\\b[^>]*>[\\s\\S]*$", RegexOption.IGNORE_CASE)
    private val controlToken = Regex("<\\|(?:thinking|thought|analysis|im_start|im_end|assistant|user|system|eot_id|end_of_turn|end_of_text)\\|>", RegexOption.IGNORE_CASE)
    private val metaLine = Regex("(?im)^\\s*(?:el modelo (?:está|esta) (?:pensando|razonando|generando)[^\\n]*|the model is (?:thinking|reasoning|generating)[^\\n]*|system (?:is )?thinking[^\\n]*|por favor,? (?:ten|tenga) paciencia[^\\n]*)\\s*$")
    private val metaPrefix = Regex("(?is)^\\s*(?:(?:el|la)\\s+(?:modelo|sistema|asistente|ia)|the\\s+(?:model|system|assistant|ai)|system)\\b[\\s\\S]*?(?=\\*{1,3}(?=\\S)|[\\\"\\u201c\\u00ab])")
    private val reasoningPrefix = Regex(
        "(?is)^\\s*(?:step[- ]by[- ]step (?:reasoning|analysis)(?: process)?|reasoning process|analysis process|chain of thought|proceso de razonamiento|razonamiento paso a paso|análisis paso a paso)\\s*:?\\s*[\\s\\S]*?(?=\\*{1,3}(?=\\S)|[\\\"\\u201c\\u00ab])",
    )
    private val processingOnlyLine = Regex(
        "(?im)^\\s*\\*{0,2}\\s*(?:" +
            "(?:hace|toma|mantiene|guarda|deja escapar|suelta|respira|observa|retoma|procesa|considera)[^\\n*]{0,120}\\b(?:pausa|pensativ[oa]|atención|hilo|escena|responder|continuar|calma|respiración)[^\\n*]{0,120}" +
            "|(?:mantiene la atención en la escena y retoma el hilo con cuidado)" +
            "|(?:observa la escena con atención,? sin dar nada por hecho)" +
        ")\\s*\\*{0,2}\\s*$",
    )

    fun normalize(value: String): String {
        var text = value.replace("\r\n", "\n").replace('\r', '\n')
        text = text.replace(thinkingBlock, "").replace(unfinishedThinkingBlock, "").replace(controlToken, "")
        text = text.replace(reasoningPrefix, "")
        text = text.replace(metaPrefix, "")
        text = text.replace(Regex("(?im)^\\s*(?:(?:el|la)\\s+(?:modelo|sistema|asistente|ia)|the\\s+(?:model|system|assistant|ai)|system)\\b[^\\n]*$"), "")
        text = text.replace(metaLine, "")
        text = text.replace(processingOnlyLine, "")
        text = text.replace(Regex("(?im)^\\s*(?:system|assistant|user|developer|thinking|reasoning|analysis|prompt|generation)\\s*:\\s*"), "")
        text = text.replace(Regex("\\*{2,}"), "*")
        text = text.replace(Regex("(\\*[^*\\n]+\\*)[ \\t]+(?=[\\\"\\u201c])"), "$1\n\n")
        text = text.replace(Regex("([\\\"\\u201d])[ \\t]+(\\*[^*\\n]+\\*)"), "$1\n\n$2")
        text = text.replace(Regex("[ \\t]+\\n"), "\n")
        text = text.replace(Regex("\\n{3,}"), "\n\n")
        return text.trim()
    }
}

object TemplateVariableResolver {
    // Escape both closing braces explicitly. Android's ICU regex engine rejects a
    // bare `}}` quantifier sequence even though the desktop JVM Pattern accepts it.
    private val variable = Regex("\\{\\{\\s*(user|char)\\s*\\}\\}", RegexOption.IGNORE_CASE)

    fun resolve(text: String, characterName: String, userName: String): String =
        variable.replace(text) { match ->
            when (match.groupValues[1].lowercase()) {
                "char" -> characterName
                "user" -> userName
                else -> match.value
            }
        }
}

object ContentPolicyResolver {
    fun resolve(global: ContentMode, override: CharacterContentOverride): ContentMode = when (override) {
        CharacterContentOverride.USE_GLOBAL -> global
        CharacterContentOverride.STANDARD -> ContentMode.STANDARD
        CharacterContentOverride.ADULT_ENABLED -> ContentMode.ADULT_ENABLED
    }
}

sealed interface RoleplaySegment {
    val text: String

    data class Dialogue(override val text: String) : RoleplaySegment
    data class Action(override val text: String) : RoleplaySegment
    data class Narration(override val text: String) : RoleplaySegment
}

object RoleplayTextParser {
    /** A deliberately small parser: **actions**, *narration*, and everything else as dialogue. */
    fun parse(value: String): List<RoleplaySegment> {
        if (value.isBlank()) return emptyList()
        val result = mutableListOf<RoleplaySegment>()
        var cursor = 0
        while (cursor < value.length) {
            val actionStart = value.indexOf("**", cursor)
            val narrationStart = value.indexOf('*', cursor).takeIf { it >= 0 && !value.startsWith("**", it) } ?: -1
            val next = listOf(actionStart, narrationStart).filter { it >= 0 }.minOrNull() ?: value.length
            addDialogue(result, value.substring(cursor, next))
            if (next == value.length) break
            if (next == actionStart) {
                val end = value.indexOf("**", next + 2)
                if (end < 0) {
                    addDialogue(result, value.substring(next))
                    break
                }
                addSegment(result, RoleplaySegment.Action(value.substring(next + 2, end)))
                cursor = end + 2
            } else {
                val end = value.indexOf('*', next + 1)
                if (end < 0) {
                    addDialogue(result, value.substring(next))
                    break
                }
                addSegment(result, RoleplaySegment.Narration(value.substring(next + 1, end)))
                cursor = end + 1
            }
        }
        return result
    }

    private fun addDialogue(target: MutableList<RoleplaySegment>, text: String) =
        addSegment(target, RoleplaySegment.Dialogue(text))

    private fun addSegment(target: MutableList<RoleplaySegment>, segment: RoleplaySegment) {
        if (segment.text.isNotBlank()) target += segment
    }
}

object TtsTextSanitizer {
    fun sanitize(text: String, readMode: TtsReadMode): String = RoleplayTextParser.parse(text)
        .filter { segment -> readMode == TtsReadMode.DIALOGUE_AND_ACTIONS || segment is RoleplaySegment.Dialogue }
        .joinToString(" ") { it.text.trim().trim('"', '\u201c', '\u201d') }
        .replace(Regex("\\s+"), " ")
        .trim()
}
