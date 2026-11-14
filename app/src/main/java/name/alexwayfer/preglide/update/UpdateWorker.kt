package name.alexwayfer.preglide.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import name.alexwayfer.preglide.BuildConfig
import name.alexwayfer.preglide.PreGlideApplication

class UpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        (applicationContext as PreGlideApplication).updates.check(notify = true, force = false).join()
        return Result.success()
    }
}

internal fun scheduleUpdates(context: Context) {
    if (BuildConfig.LOCAL_TRACING) return
    val request = PeriodicWorkRequestBuilder<UpdateWorker>(UPDATE_INTERVAL.inWholeMinutes, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "app-updates", ExistingPeriodicWorkPolicy.UPDATE, request,
    )
}
