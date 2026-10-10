package com.maksimowiczm.foodyou.ai.domain

enum class AiProvider {
    OpenAi,
    Gemini,
    Anthropic,
    OpenRouter,
    Custom,
    ;

    val defaultModel: String?
        get() =
            when (this) {
                OpenAi -> "gpt-4o-mini"
                Gemini -> "gemini-2.5-flash"
                Anthropic -> "claude-sonnet-4-5"
                OpenRouter -> "openai/gpt-4o-mini"
                Custom -> null
            }

    val defaultBaseUrl: String?
        get() =
            when (this) {
                OpenAi -> "https://api.openai.com/v1"
                OpenRouter -> "https://openrouter.ai/api/v1"
                Gemini,
                Anthropic,
                Custom -> null
            }
}
