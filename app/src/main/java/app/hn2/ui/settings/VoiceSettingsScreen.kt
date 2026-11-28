package app.hn2.ui.settings

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hn2.data.VoiceEngine
import app.hn2.data.VoiceSettings
import app.hn2.listen.VoiceOption
import app.hn2.listen.createSpeechEngine
import app.hn2.listen.voiceOptions
import app.hn2.settings
import app.hn2.util.runSuspendCatching
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = context.settings
    val voice by settings.voice.collectAsStateWithLifecycle()
    val options = rememberVoiceOptions(voice.engine)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Voice") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Engine")
            VoiceEngine.entries.forEach { engine ->
                ListItem(
                    headlineContent = { Text(engine.label) },
                    supportingContent = { Text(engine.description) },
                    leadingContent = { RadioButton(selected = engine == voice.engine, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.selectable(
                        selected = engine == voice.engine,
                        role = Role.RadioButton,
                        onClick = { settings.setVoiceEngine(engine) },
                    ),
                )
            }

            SectionLabel("Voice")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VoicePicker(
                    options = options,
                    selected = voice.voice,
                    onSelect = { settings.setVoice(voice.engine, it) },
                )
                PreviewButton(voice)
            }
        }
    }
}

private val VoiceEngine.description: String
    get() = when (this) {
        VoiceEngine.SYSTEM -> "Your phone's text-to-speech voices. Nothing to download."
    }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

/** The engine's voices, or null while they load. */
@Composable
fun rememberVoiceOptions(engine: VoiceEngine): List<VoiceOption>? {
    val context = LocalContext.current
    val options by produceState<List<VoiceOption>?>(null, engine) {
        value = runSuspendCatching { voiceOptions(context, engine) }.getOrDefault(emptyList())
    }
    return options
}

/** The label for the chosen voice, as the Settings screen's Voice row shows it. */
fun voiceSummary(voice: VoiceSettings, options: List<VoiceOption>?): String {
    val name = voice.voice?.let { id -> options?.firstOrNull { it.id == id }?.label } ?: "Default voice"
    return "${voice.engine.label} · $name"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(options: List<VoiceOption>?, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.let { id -> options?.firstOrNull { it.id == id }?.label } ?: "Default"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && options != null }) {
        OutlinedTextField(
            value = if (options == null) "Loading voices…" else label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Voice") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.exposedDropdownSize()) {
            DropdownMenuItem(text = { Text("Default") }, onClick = { onSelect(null); expanded = false })
            options.orEmpty().forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option.id); expanded = false })
            }
        }
    }
}

/** Speaks a sample sentence with the chosen engine and voice, the same way articles are read. */
@Composable
private fun PreviewButton(voice: VoiceSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player.value?.release() } }

    FilledTonalButton(
        enabled = !busy,
        onClick = {
            busy = true
            error = null
            scope.launch {
                runSuspendCatching { play(context, voice, player) }.onFailure { error = it.message ?: "Couldn't play a preview" }
                busy = false
            }
        },
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.PlayArrow, null)
        Text("Preview", Modifier.padding(start = 8.dp))
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

private suspend fun play(context: Context, voice: VoiceSettings, player: androidx.compose.runtime.MutableState<MediaPlayer?>) {
    val file = File(context.cacheDir, "voice-preview.wav")
    createSpeechEngine(context, voice).use { engine ->
        file.delete()
        engine.synthesize("This is how stories from Hacker News will sound.", file)
    }
    player.value?.release()
    player.value = MediaPlayer().apply {
        setDataSource(file.path)
        prepare()
        start()
    }
}
