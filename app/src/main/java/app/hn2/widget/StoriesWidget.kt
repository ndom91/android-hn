package app.hn2.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.hn2.R
import app.hn2.data.Section
import app.hn2.ui.item.ItemActivity
import app.hn2.ui.list.MainActivity
import app.hn2.ui.theme.HotOrange
import app.hn2.util.isHotComments
import app.hn2.util.isHotScore
import java.util.Date

val ItemIdKey = ActionParameters.Key<Long>(ItemActivity.EXTRA_ITEM_ID)

/** Home-screen list of stories for one section. Tapping a story opens its article. */
class StoriesWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent(currentState()) }
    }
}

@Composable
private fun WidgetContent(prefs: Preferences) {
    val context = LocalContext.current
    val section = Section.fromName(prefs[WidgetKeys.section])
    val theme = WidgetTheme.fromName(prefs[WidgetKeys.theme])
    val stories = decodeWidgetStories(prefs[WidgetKeys.stories])
    val loading = prefs[WidgetKeys.loading] == true
    val error = prefs[WidgetKeys.error]
    val updatedAt = prefs[WidgetKeys.updatedAt]

    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(theme.background)
            .padding(horizontal = 12.dp),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                section.title.uppercase(),
                style = TextStyle(color = ColorProvider(theme.text), fontSize = 16.sp),
                maxLines = 1,
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(MainActivity.intent(context, section))),
            )
            val status = when {
                loading -> "…"
                error != null -> "offline"
                updatedAt != null -> DateFormat.getTimeFormat(context).format(Date(updatedAt))
                else -> ""
            }
            Text(status, style = TextStyle(color = ColorProvider(theme.secondary), fontSize = 13.sp))
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                colorFilter = ColorFilter.tint(ColorProvider(theme.text)),
                modifier = GlanceModifier
                    .size(40.dp)
                    .padding(8.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
            )
        }
        Divider(theme.divider)

        if (stories.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (error != null && !loading) "Couldn't load stories. Tap refresh." else "Loading…",
                    style = TextStyle(color = ColorProvider(theme.secondary), fontSize = 14.sp),
                )
            }
        } else {
            LazyColumn {
                items(stories, itemId = { it.id }) { story ->
                    StoryItem(story, theme, section.hotThreshold)
                }
            }
        }
    }
}

@Composable
private fun StoryItem(story: WidgetStory, theme: WidgetTheme, hotThreshold: Int) {
    fun meta(hot: Boolean) = TextStyle(color = ColorProvider(if (hot) HotOrange else theme.secondary), fontSize = 13.sp)
    Column(GlanceModifier.fillMaxWidth()) {
        Column(
            GlanceModifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
                .clickable(actionStartActivity<ItemActivity>(actionParametersOf(ItemIdKey to story.id))),
        ) {
            Text(story.title, style = TextStyle(color = ColorProvider(theme.text), fontSize = 15.sp), maxLines = 3)
            Row {
                Text("${story.score}p", style = meta(isHotScore(story.score, hotThreshold)))
                Text(" - ", style = meta(false))
                Text("${story.comments}c", style = meta(isHotComments(story.comments, hotThreshold)))
            }
        }
        Divider(theme.divider)
    }
}

@Composable
private fun Divider(color: Color) {
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(color)) {}
}
