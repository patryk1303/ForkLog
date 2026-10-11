package com.maksimowiczm.foodyou.ai.infrastructure.openai

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
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class OpenAiCompatibleClient(private val client: HttpClient) : AiProtocolClient {

    override suspend fun complete(
        system: String,
        user: String,
        images: List<PreparedImage>,
        config: AiProviderConfig,
    ): String = executeAiRequest {
        val baseUrl = config.baseUrl ?: throw AiCalculationException.NotConfigured
        val model = config.model ?: throw AiCalculationException.NotConfigured
        val apiKey = config.apiKey ?: throw AiCalculationException.NotConfigured

        val response =
            client.post("${baseUrl.trimEnd('/')}/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(requestBody(model = model, system = system, user = user, images = images))
            }
        response.ensureSuccess()

        val body =
            json.decodeFromString(ChatCompletionResponse.serializer(), response.bodyAsText())
        body.choices.firstOrNull()?.message?.content
            ?: throw AiCalculationException.InvalidResponse
    }

    private fun requestBody(
        model: String,
        system: String,
        user: String,
        images: List<PreparedImage>,
    ) = buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", system)
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", user)
                    }
                    images.forEach { image ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,${image.base64}")
                            }
                        }
                    }
                }
            }
        }
        putJsonObject("response_format") { put("type", "json_object") }
    }

    @Serializable
    private data class ChatCompletionResponse(val choices: List<Choice> = emptyList()) {

        @Serializable
        data class Choice(val message: Message = Message()) {

            @Serializable
            data class Message(val content: String? = null)
        }
    }

    private companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
