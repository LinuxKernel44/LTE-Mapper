package com.ltemapper.app.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRetryPolicyTest {

    @Test
    fun `a non-retryable failure is never retried, even on the first attempt`() {
        assertFalse(DownloadRetryPolicy.shouldRetry(retryable = false, attemptCount = 0))
    }

    @Test
    fun `a retryable failure is retried while under the attempt cap`() {
        assertTrue(DownloadRetryPolicy.shouldRetry(retryable = true, attemptCount = 0, maxAttempts = 3))
        assertTrue(DownloadRetryPolicy.shouldRetry(retryable = true, attemptCount = 2, maxAttempts = 3))
    }

    @Test
    fun `a retryable failure stops retrying once the attempt cap is reached`() {
        assertFalse(DownloadRetryPolicy.shouldRetry(retryable = true, attemptCount = 3, maxAttempts = 3))
        assertFalse(DownloadRetryPolicy.shouldRetry(retryable = true, attemptCount = 10, maxAttempts = 3))
    }

    @Test
    fun `user cancellation and structural failures (retryable = false) are never retried regardless of attempt count`() {
        assertFalse(DownloadRetryPolicy.shouldRetry(retryable = false, attemptCount = 0, maxAttempts = 3))
    }
}
