package com.splashcleaner.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Scheduling shell only. No endpoint is configured in v0.1; update work is intentionally inert. */
class RuleUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
