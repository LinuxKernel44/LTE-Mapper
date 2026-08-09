package com.ltemapper.app.work

/**
 * Pure retry decision for [OfflineTileDownloadWorker], pulled out of the worker itself so it's
 * testable without any WorkManager/Android machinery. WorkManager's own default backoff
 * (exponential, starting at the platform minimum) applies between attempts; [maxAttempts] caps it
 * so a genuinely permanent failure doesn't retry forever.
 */
object DownloadRetryPolicy {
    const val MAX_ATTEMPTS = 3

    fun shouldRetry(retryable: Boolean, attemptCount: Int, maxAttempts: Int = MAX_ATTEMPTS): Boolean =
        retryable && attemptCount < maxAttempts
}
