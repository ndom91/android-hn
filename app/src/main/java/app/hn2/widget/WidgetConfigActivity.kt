package app.hn2.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.GlanceId
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.hn2.R
import app.hn2.data.Section
import app.hn2.ui.theme.Hn2Theme
import kotlinx.coroutines.launch

/** Widget settings, when placing the widget or later from its long-press menu. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()

        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            val prefs = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
            val current = WidgetSettings(
                section = Section.fromName(prefs[WidgetKeys.section]),
                theme = WidgetTheme.fromName(prefs[WidgetKeys.theme]),
                textSize = WidgetTextSize.fromName(prefs[WidgetKeys.textSize]),
                spacing = WidgetSpacing.fromName(prefs[WidgetKeys.spacing]),
                orangeRanks = prefs[WidgetKeys.orangeRanks] == true,
            )
            // Preview with the stories the widget is showing, or examples for a new one.
            val stories = decodeWidgetStories(prefs[WidgetKeys.stories]).ifEmpty { SampleStories }
            setContent {
                Hn2Theme {
                    ConfigScreen(
                        initial = current,
                        stories = stories,
                        onSave = { settings -> save(glanceId, settings, current.section, result) },
                        onCancel = ::finish,
                    )
                }
            }
        }
    }

    private fun save(glanceId: GlanceId, settings: WidgetSettings, previous: Section, result: Intent) {
        lifecycleScope.launch {
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { state ->
                state[WidgetKeys.section] = settings.section.name
                state[WidgetKeys.theme] = settings.theme.name
                state[WidgetKeys.textSize] = settings.textSize.name
                state[WidgetKeys.spacing] = settings.spacing.name
                state[WidgetKeys.orangeRanks] = settings.orangeRanks
                if (settings.section != previous) {
                    state.remove(WidgetKeys.stories)
                    state[WidgetKeys.loading] = true
                }
            }
            StoriesWidget().update(this@WidgetConfigActivity, glanceId)
            WidgetRefreshWorker.refreshNow(this@WidgetConfigActivity)
            setResult(RESULT_OK, result)
            finish()
        }
    }
}

private data class WidgetSettings(
    val section: Section,
    val theme: WidgetTheme,
    val textSize: WidgetTextSize,
    val spacing: WidgetSpacing,
    val orangeRanks: Boolean,
)

private val SampleStories = listOf(
    WidgetStory(1, "Show HN: A tiny Hacker News widget for your home screen", 412, 138, "github.com"),
    WidgetStory(2, "The unreasonable effectiveness of plain text", 96, 41, "example.com"),
    WidgetStory(3, "Ask HN: What's your favorite home-screen widget?", 251, 230),
)

/** A stand-in wallpaper behind the previews, so the transparent theme reads as transparent. */
private val Wallpaper = Brush.linearGradient(listOf(Color(0xFF2E3F63), Color(0xFF6B5A7A), Color(0xFFB38A6A)))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConfigScreen(
    initial: WidgetSettings,
    stories: List<WidgetStory>,
    onSave: (WidgetSettings) -> Unit,
    onCancel: () -> Unit,
) {
    // Section, theme and the rest are enums, so the settings survive rotation as their names.
    var section by rememberSaveable { mutableStateOf(initial.section) }
    var theme by rememberSaveable { mutableStateOf(initial.theme) }
    var textSize by rememberSaveable { mutableStateOf(initial.textSize) }
    var spacing by rememberSaveable { mutableStateOf(initial.spacing) }
    var orangeRanks by rememberSaveable { mutableStateOf(initial.orangeRanks) }
    val settings = WidgetSettings(section, theme, textSize, spacing, orangeRanks)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget settings") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel") } },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = { onSave(settings) },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(48.dp),
                ) { Text("Save") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // The preview stays put while the options scroll, so every change is visible as you make it.
            Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                WidgetPreview(settings, stories)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Setting("Stories") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Section.entries.forEach {
                            FilterChip(selected = it == section, onClick = { section = it }, label = { Text(it.title) })
                        }
                    }
                }
                Setting("Theme") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WidgetTheme.entries.forEach {
                            ThemeSwatch(it, selected = it == theme, onClick = { theme = it }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Setting("Rank numbers") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = orangeRanks, role = Role.Switch, onValueChange = { orangeRanks = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("In HN orange", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = orangeRanks, onCheckedChange = null)
                    }
                }
                Setting("Text size") {
                    Segmented(WidgetTextSize.entries, textSize, { it.label }) { textSize = it }
                }
                Setting("Spacing") {
                    Segmented(WidgetSpacing.entries, spacing, { it.label }) { spacing = it }
                }
            }
        }
    }
}

@Composable
private fun Setting(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option)) }
        }
    }
}

/** A small sample of one widget theme over the stand-in wallpaper. */
@Composable
private fun ThemeSwatch(theme: WidgetTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(shape)
                .background(Wallpaper)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
                .clickable(onClick = onClick)
                .padding(10.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.background),
                contentAlignment = Alignment.Center,
            ) {
                Text("Aa", color = theme.text, style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            theme.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
        )
    }
}

/** The widget as it will look, drawn with the same colors, sizes and spacing as the real one. */
@Composable
private fun WidgetPreview(settings: WidgetSettings, stories: List<WidgetStory>) {
    val theme = settings.theme
    val sizes = settings.textSize
    val rankColor = if (settings.orangeRanks) HnOrange else theme.text
    // JetBrains Mono for [mono] text, as the widget's images and meta line use; titles are the
    // phone's font in bold, like the widget's.
    // Matches the shadow in widget_story_title_shadow.xml (pixels, like the layout's).
    val storyShadow = if (theme.textShadow) Shadow(Color(0xB3000000), Offset(0f, 1f), 4f) else null

    fun style(size: Int, color: Color, mono: Boolean = false, bold: Boolean = false, shadow: Shadow? = null) = TextStyle(
        color = color,
        shadow = shadow,
        fontSize = size.sp,
        lineHeight = (size * 1.3f).sp,
        fontFamily = if (mono) JetBrainsMono else null,
        fontWeight = if (bold) FontWeight.Bold else null,
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Wallpaper)
            .padding(20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(theme.background)
                .padding(horizontal = 14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = settings.spacing.header.dp + 6.dp, bottom = settings.spacing.header.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f)) {
                    Text("HN", style = style(sizes.header, HnOrange, mono = true, bold = true))
                    Text(" / ", style = style(sizes.header, theme.secondary, mono = true))
                    Text(settings.section.name, style = style(sizes.header, theme.text, mono = true, bold = true))
                }
                Text("12:34", style = style(sizes.header - 3, theme.secondary, mono = true))
                Icon(
                    painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    tint = theme.text,
                    modifier = Modifier.size(36.dp).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            stories.take(3).forEachIndexed { index, story ->
                Row(Modifier.fillMaxWidth().padding(vertical = settings.spacing.story.dp)) {
                    Text(
                        rankLabel(index + 1),
                        style = style(sizes.rank, rankColor, mono = true),
                        // JetBrains Mono digits are 0.6em wide; the widget measures "00" the same way.
                        modifier = Modifier.width((sizes.rank * 1.2f + 10).dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(story.title, style = style(sizes.title, theme.text, bold = true, shadow = storyShadow), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            story.meta(),
                            style = style(sizes.meta, theme.secondary, mono = true, shadow = storyShadow),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
