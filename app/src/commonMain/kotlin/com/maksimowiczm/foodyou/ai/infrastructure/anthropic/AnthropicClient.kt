package com.maksimowiczm.foodyou.ai.infrastructure.anthropic

import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import com.maksimowiczm.foodyou.ai.infrastructure.AiProtocolClient
import com.maksimowiczm.foodyou.ai.infrastructure.ensureSuccess
import com.maksimowiczm.foodyou.ai.infrastructure.executeAiRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class AnthropicClient(private val client: HttpClient) : AiProtocolClient {

    override suspend fun complete(
        system: String,
        user: String,
        images: List<PreparedImage>,
        config: AiProviderConfig,
    ): String = executeAiRequest {
        val apiKey = config.apiKey ?: throw AiCalculationException.NotConfigured
        val model = config.model ?: throw AiCalculationException.NotConfigured

        val response =
            client.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(requestBody(model = model, system = system, user = user, images = images))
            }
        response.ensureSuccess()

        val body = json.decodeFromString(MessagesResponse.serializer(), response.bodyAsText())
        body.content.firstOrNull { it.type == "text" }?.text
            ?: throw AiCalculationException.InvalidResponse
    }

    private fun requestBody(
        model: String,
        system: String,
        user: String,
        images: List<PreparedImage>,
    ) = buildJsonObject {
        put("model", model)
        put("max_tokens", 1024)
        put("system", system)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    images.forEach { image ->
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", image.base64)
                            }
                        }
                    }
                    addJsonObject {
                        put("type", "text")
                        put("text", user)
                    }
                }
            }
        }
    }

    @Serializable
    private data class MessagesResponse(val content: List<Block> = emptyList()) {

        @Serializable
        data class Block(val type: String? = null, val text: String? = null)
    }

    private companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
