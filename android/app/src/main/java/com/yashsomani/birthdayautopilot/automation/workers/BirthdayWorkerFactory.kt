package com.yashsomani.birthdayautopilot.automation.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.yashsomani.birthdayautopilot.AppGraph

class BirthdayWorkerFactory(
  private val appGraph: AppGraph,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = when (workerClassName) {
    ReconcileWorker::class.java.name -> ReconcileWorker(appContext, workerParameters, appGraph)
    SmsEvidenceWorker::class.java.name -> SmsEvidenceWorker(appContext, workerParameters, appGraph)
    SmsOutcomeReportWorker::class.java.name ->
      SmsOutcomeReportWorker(appContext, workerParameters, appGraph)
    else -> null
  }
}
