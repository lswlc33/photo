package com.lc33.photoorganizer.processing

import androidx.annotation.StringRes

/**
 * Failure raised by the local processing pipeline. The message lives in
 * resources so the UI can render it in the user's language instead of leaking
 * an English developer string.
 */
class ProcessingException(
    @StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
    cause: Throwable? = null,
) : Exception(cause)
