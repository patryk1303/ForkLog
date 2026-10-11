package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

internal interface AiProtocolClient {
    suspend fun complete(
        system: String,
        user: String,
        images: List<PreparedImage>,
        config: AiProviderConfig,
    ): String
}

/**
 * Executes [block], mapping malformed response bodies to [AiCalculationException.InvalidResponse]
 * and other unexpected failures to [AiCalculationException.Network].
 */
internal suspend fun executeAiRequest(block: suspend () -> String): String =
    try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: AiCalculationException) {
        throw exception
    } catch (exception: SerializationException) {
        throw AiCalculationException.InvalidResponse
    } catch (exception: Exception) {
        throw AiCalculationException.Network(exception)
    }

/**
 * Throws the matching [AiCalculationException] for a non-2xx response. Must be called before the
 * response body is read.
 */
internal fun HttpResponse.ensureSuccess() {
    if (status.isSuccess()) return
    val exception =
        when (status.value) {
            401,
            403 -> AiCalculationException.Unauthorized
            429 -> AiCalculationException.RateLimited
            in 500..599 -> AiCalculationException.ServerError
            else -> AiCalculationException.InvalidResponse
        }
    throw exception
}
