package app.hn2.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** The widget's refresh button: show the loading state, then refresh in the background. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[WidgetKeys.loading] = true }
        StoriesWidget().update(context, glanceId)
        WidgetRefreshWorker.refreshNow(context)
    }
}
