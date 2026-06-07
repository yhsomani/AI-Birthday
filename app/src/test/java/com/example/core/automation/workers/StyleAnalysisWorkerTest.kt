package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.domain.usecase.StyleAnalysisUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StyleAnalysisWorkerTest {

    private lateinit var context: Context
    private val styleAnalysisUseCase: StyleAnalysisUseCase = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `doWork calls styleAnalysisUseCase and returns success`() = runTest {
        val worker = TestListenableWorkerBuilder<StyleAnalysisWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return StyleAnalysisWorker(appContext, workerParameters, styleAnalysisUseCase)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { styleAnalysisUseCase() }
    }
}
