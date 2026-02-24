package id.my.bananapixel.quakealert.domain

/**
 * Sealed class representing app-level errors with consistent mapping and handling.
 * Replaces mixed use of exceptions, null returns, and Result types.
 */
sealed class AppError(override val message: String) : Throwable(message) {
    data class NetworkError(val errorMessage: String = "Network connection failed") : AppError(errorMessage)
    data class ParseError(val errorMessage: String = "Failed to parse response") : AppError(errorMessage)
    data class ApiError(val statusCode: Int, val errorMessage: String = "API error") : AppError(errorMessage)
    data class NotFoundError(val errorMessage: String = "Resource not found") : AppError(errorMessage)
    data class ValidationError(val errorMessage: String = "Invalid input") : AppError(errorMessage)
    data class UnknownError(val errorMessage: String = "Unknown error occurred") : AppError(errorMessage)
}

/**
 * Extension to convert common exceptions into AppError.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is java.io.IOException -> AppError.NetworkError(this.message ?: "Network error")
    else -> AppError.UnknownError(this.message ?: "Unknown error")
}

/**
 * Result wrapper using AppError for failures.
 */
typealias AppResult<T> = Result<T>

fun <T> appSuccessResult(value: T): AppResult<T> = Result.success(value)
fun <T> appFailureResult(error: AppError): AppResult<T> = Result.failure(error)
