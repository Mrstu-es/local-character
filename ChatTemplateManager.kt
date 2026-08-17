package com.localcharacter.app.domain.prompt

enum class ChatTemplate { AUTO, CHAT_ML, LLAMA_3, GEMMA, QWEN, CUSTOM, RAW }

class ChatTemplateManager {
    fun apply(prompt: String, template: ChatTemplate, customTemplate: String? = null): String = when (template) {
        ChatTemplate.AUTO, ChatTemplate.RAW -> prompt
        ChatTemplate.CHAT_ML, ChatTemplate.QWEN -> "<|im_start|>system\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        ChatTemplate.LLAMA_3 -> "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$prompt<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
        ChatTemplate.GEMMA -> "<bos><start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
        ChatTemplate.CUSTOM -> customTemplate?.replace("{{prompt}}", prompt) ?: prompt
    }
}
