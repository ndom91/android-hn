package app.hn2.widget

import android.content.Context
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.hn2.R
import app.hn2.data.Section
import app.hn2.ui.item.ItemActivity
import app.hn2.ui.list.MainActivity
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
    val sizes = WidgetTextSize.fromName(prefs[WidgetKeys.textSize])
    val spacing = WidgetSpacing.fromName(prefs[WidgetKeys.spacing])
    val rankColor = if (prefs[WidgetKeys.orangeRanks] == true) HnOrange else theme.text
    val stories = decodeWidgetStories(prefs[WidgetKeys.stories])
    val loading = prefs[WidgetKeys.loading] == true
    val error = prefs[WidgetKeys.error]
    val updatedAt = prefs[WidgetKeys.updatedAt]
    val mono = MonoText(context)

    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(theme.background)
            .padding(horizontal = 14.dp),
    ) {
        // "HN / TOP": which list this widget shows. Tapping it opens that list in the app.
        Row(
            GlanceModifier.fillMaxWidth().padding(top = spacing.header.dp + 6.dp, bottom = spacing.header.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val header = mono.draw(
                listOf(
                    MonoRun("HN", HnOrange, bold = true),
                    MonoRun(" / ", theme.secondary),
                    MonoRun(section.name, theme.text, bold = true),
                ),
                sizes.header,
            )
            Box(
                GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(MainActivity.intent(context, section))),
            ) {
                Image(
                    provider = ImageProvider(header.bitmap),
                    contentDescription = "Hacker News, ${section.title}",
                    modifier = GlanceModifier.size(header.widthDp.dp, header.heightDp.dp),
                )
            }
            val status = when {
                loading -> "…"
                error != null -> "offline"
                updatedAt != null -> DateFormat.getTimeFormat(context).format(Date(updatedAt))
                else -> ""
            }
            if (status.isNotEmpty()) {
                val time = mono.draw(listOf(MonoRun(status, theme.secondary)), sizes.header - 3)
                Image(
                    provider = ImageProvider(time.bitmap),
                    contentDescription = status,
                    modifier = GlanceModifier.size(time.widthDp.dp, time.heightDp.dp),
                )
            }
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                colorFilter = ColorFilter.tint(ColorProvider(theme.text)),
                modifier = GlanceModifier
                    .size(36.dp)
                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 0.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
            )
        }

        if (stories.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (error != null && !loading) "Couldn't load stories. Tap refresh." else "Loading…",
                    style = TextStyle(color = ColorProvider(theme.secondary), fontSize = sizes.title.sp),
                )
            }
        } else {
            // Every rank is as wide as "00", so the titles all start at the same place.
            val rankWidth = mono.draw(listOf(MonoRun("00", theme.text)), sizes.rank).widthDp + 10
            LazyColumn {
                itemsIndexed(stories, itemId = { _, story -> story.id }) { index, story ->
                    val rank = mono.draw(listOf(MonoRun(rankLabel(index + 1), Color.White)), sizes.rank, mask = true)
                    StoryItem(rank, rankWidth, story, theme, sizes, spacing, rankColor)
                }
            }
        }
    }
}

/** One story: a big rank number, then the title and a points · comments · site line. */
@Composable
private fun StoryItem(
    rank: MonoImage,
    rankWidth: Float,
    story: WidgetStory,
    theme: WidgetTheme,
    sizes: WidgetTextSize,
    spacing: WidgetSpacing,
    rankColor: Color,
) {
    Row(
        GlanceModifier
            .fillMaxWidth()
            .padding(vertical = spacing.story.dp)
            .clickable(actionStartActivity<ItemActivity>(actionParametersOf(ItemIdKey to story.id))),
    ) {
        Box(GlanceModifier.width(rankWidth.dp)) {
            Image(
                provider = ImageProvider(rank.bitmap),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(rankColor)),
                modifier = GlanceModifier.size(rank.widthDp.dp, rank.heightDp.dp),
            )
        }
        Column(GlanceModifier.defaultWeight()) {
            AndroidRemoteViews(storyTitle(LocalContext.current, story.title, theme.text, sizes.title), GlanceModifier.fillMaxWidth())
            Text(
                story.meta(),
                style = TextStyle(color = ColorProvider(theme.secondary), fontSize = sizes.meta.sp, fontFamily = FontFamily.Monospace),
                maxLines = 1,
                modifier = GlanceModifier.padding(top = 2.dp),
            )
        }
    }
}

/** A bold story title from `widget_story_title.xml`; see there for why it isn't a Glance Text. */
private fun storyTitle(context: Context, title: String, color: Color, sizeSp: Int) =
    RemoteViews(context.packageName, R.layout.widget_story_title).apply {
        setTextViewText(R.id.title, title)
        setTextColor(R.id.title, color.toArgb())
        setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
    }
