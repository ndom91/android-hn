package app.hn2.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.hn2.data.Section
import app.hn2.ui.theme.Hn2Theme
import kotlinx.coroutines.launch

/** Lets you pick the widget's section and theme, when placing it or later from its long-press menu. */
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
            val currentSection = Section.fromName(prefs[WidgetKeys.section])
            setContent {
                Hn2Theme {
                    ConfigScreen(
                        initialSection = currentSection,
                        initialTheme = WidgetTheme.fromName(prefs[WidgetKeys.theme]),
                        onSave = { section, theme -> save(glanceId, section, theme, currentSection, result) },
                    )
                }
            }
        }
    }

    private fun save(glanceId: GlanceId, section: Section, theme: WidgetTheme, previous: Section, result: Intent) {
        lifecycleScope.launch {
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { state ->
                state[WidgetKeys.section] = section.name
                state[WidgetKeys.theme] = theme.name
                if (section != previous) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(initialSection: Section, initialTheme: WidgetTheme, onSave: (Section, WidgetTheme) -> Unit) {
    var section by remember { mutableStateOf(initialSection) }
    var theme by remember { mutableStateOf(initialTheme) }
    Scaffold(topBar = { TopAppBar(title = { Text("Widget settings") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Heading("Section")
            Section.entries.forEach { RadioRow(it.title, it == section) { section = it } }
            Heading("Theme")
            WidgetTheme.entries.forEach { RadioRow(it.label, it == theme) { theme = it } }
            Button(onClick = { onSave(section, theme) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
