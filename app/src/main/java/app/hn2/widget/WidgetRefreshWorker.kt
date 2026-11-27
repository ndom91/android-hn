package app.hn2.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.hn2.data.Section
import app.hn2.data.Story
import app.hn2.repository
import app.hn2.util.runSuspendCatching
import java.util.concurrent.TimeUnit

/** Fetches stories for every placed widget, once per distinct section. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val ids = GlanceAppWidgetManager(context).getGlanceIds(StoriesWidget::class.java)
        val fetched = mutableMapOf<Section, kotlin.Result<List<Story>>>()
        var failed = false

        for (id in ids) {
            val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            val section = Section.fromName(prefs[WidgetKeys.section])
            val result = fetched.getOrPut(section) { runSuspendCatching { context.repository.stories(section) } }
            updateAppWidgetState(context, id) { state ->
                state[WidgetKeys.loading] = false
                result
                    .onSuccess { stories ->
                        state[WidgetKeys.stories] = encodeWidgetStories(stories)
                        state[WidgetKeys.updatedAt] = System.currentTimeMillis()
                        state.remove(WidgetKeys.error)
                    }
                    .onFailure { error ->
                        failed = true
                        state[WidgetKeys.error] = error.message ?: "error"
                    }
            }
            StoriesWidget().update(context, id)
        }
        return if (failed && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "widget-refresh-periodic"
        private const val NOW = "widget-refresh-now"

        /** No network constraint: offline should show an error, not a spinner that never ends. */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build(),
            )
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
