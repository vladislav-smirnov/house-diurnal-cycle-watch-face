package io.github.airdaydreamers.housediurnalcycle.watchface.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.airdaydreamers.housediurnalcycle.watchface.usecases.GetLocationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class PeriodicWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    companion object {
        const val WORK_NAME = "watchface_periodic_worker"
        const val DATA_RESULT_KEY_LAT = "data_key_lat"
        const val DATA_RESULT_KEY_LONG = "data_key_long"

        fun scheduleWork(context: Context): UUID {
            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<PeriodicWorker>(
                60, TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).also {
                it.enqueueUniquePeriodicWork(
                    WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, periodicWorkRequest
                )
            }

            return periodicWorkRequest.id
        }
    }

    private val lastLocation = GetLocationUseCase(applicationContext)

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val resultData = lastLocation()?.let {
                Data.Builder().putDouble(DATA_RESULT_KEY_LAT, it.latitude).putDouble(
                    DATA_RESULT_KEY_LONG, it.longitude
                ).build()
            }

            // Return the result with output data or failure
            return@withContext resultData?.let { Result.success(it) } ?: Result.failure()
        }
    }
}