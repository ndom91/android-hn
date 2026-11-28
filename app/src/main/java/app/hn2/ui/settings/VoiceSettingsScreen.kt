package app.hn2.ui.settings

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.hn2.listen.VoiceDownloadWorker
import app.hn2.listen.VoiceModel
import app.hn2.listen.fallbackAfterDeleting
import app.hn2.listen.isUsing
import app.hn2.listen.models
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
    val scope = rememberCoroutineScope()
    val voice by settings.voice.collectAsStateWithLifecycle()
    var deletions by remember { mutableIntStateOf(0) }
    val downloads = VoiceModel.entries.associateWith { rememberDownloadState(it, deletions) }
    // Reload the voice list whenever a download finishes or is deleted.
    val installed = downloads.filterValues { it == DownloadState.Installed }.keys
    val options = rememberVoiceOptions(voice.engine, installed)
    var confirmDelete by remember { mutableStateOf<VoiceModel?>(null) }

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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Engine")
            SettingsCard {
                VoiceEngine.entries.forEach { engine ->
                    val ready = engine.models.isEmpty() || engine.models.any { it in installed }
                    ListItem(
                        headlineContent = { Text(engine.label) },
                        supportingContent = { Text(engine.description) },
                        leadingContent = { RadioButton(selected = engine == voice.engine, onClick = null) },
                        trailingContent = if (ready) null else {
                            { Text("Not downloaded", style = MaterialTheme.typography.labelMedium) }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.selectable(
                            selected = engine == voice.engine,
                            role = Role.RadioButton,
                            onClick = { settings.setVoiceEngine(engine) },
                        ),
                    )
                }
            }

            SectionLabel("Voice")
            val missing = voice.engine.models.isNotEmpty() && voice.engine.models.none { it in installed }
            if (missing) {
                MissingVoiceCard(voice.engine, downloads)
            } else {
                VoicePicker(
                    options = options,
                    selected = voice.voice,
                    onSelect = { settings.setVoice(voice.engine, it) },
                )
                PreviewButton(voice)
            }

            SectionLabel("Downloads")
            SettingsCard {
                listOf(VoiceEngine.PIPER, VoiceEngine.KOKORO).forEach { engine ->
                    Text(
                        engine.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    )
                    engine.models.forEach { model ->
                        DownloadRow(
                            model = model,
                            state = downloads.getValue(model),
                            inUse = voice.isUsing(model, installed),
                            onDelete = { confirmDelete = model },
                        )
                    }
                }
            }
            StorageUsed(installed)
        }
    }

    confirmDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${model.label.substringBefore(" ·")}?") },
            text = {
                Text(
                    if (voice.isUsing(model, installed)) {
                        "Articles are read with this voice now. Without it, hn2 switches to " +
                            "${fallbackAfterDeleting(model, installed).label}. You can download it again later (${model.sizeMb} MB)."
                    } else {
                        "This frees ${model.sizeMb} MB. You can download it again later."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    if (voice.voices[model.engine] == model.name) settings.setVoice(model.engine, null)
                    if (voice.engine == model.engine) settings.setVoiceEngine(fallbackAfterDeleting(model, installed))
                    scope.launch {
                        withContext(Dispatchers.IO) { model.delete(context) }
                        deletions++
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

private val VoiceEngine.description: String
    get() = when (this) {
        VoiceEngine.SYSTEM -> "Your phone's built-in voices. Nothing to download."
        VoiceEngine.PIPER -> "Natural voices that run on your phone, about 21 MB each. Quick on any phone."
        VoiceEngine.KOKORO -> "The most natural voices. One 333 MB download, best on recent phones."
    }

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

/** Shown instead of the voice picker when the chosen engine has nothing downloaded yet. */
@Composable
private fun MissingVoiceCard(engine: VoiceEngine, downloads: Map<VoiceModel, DownloadState>) {
    val context = LocalContext.current
    val first = engine.models.first()
    val downloading = engine.models.any { downloads[it] is DownloadState.Downloading }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                when {
                    downloading -> "${engine.label} is downloading. You can leave this screen; it carries on."
                    engine.models.size > 1 -> "${engine.label} needs a voice. Download one below, or start with ${first.label.substringBefore(" ·")}."
                    else -> "${engine.label} needs a one-time download of ${first.sizeMb} MB."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!downloading) {
                Button(onClick = { VoiceDownloadWorker.start(context, first) }) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Text(
                        if (engine.models.size > 1) "Download ${first.label.substringBefore(" ·")} (${first.sizeMb} MB)" else "Download (${first.sizeMb} MB)",
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** How much space downloaded voices take, measured from the files. */
@Composable
private fun StorageUsed(installed: Set<VoiceModel>) {
    val context = LocalContext.current
    val bytes by produceState(0L, installed) {
        value = withContext(Dispatchers.IO) {
            installed.sumOf { model -> model.dir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        }
    }
    if (installed.isNotEmpty() && bytes > 0) {
        Text(
            "Downloaded voices use ${bytes / 1_000_000} MB on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

private sealed interface DownloadState {
    data object NotDownloaded : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Failed(val message: String) : DownloadState
    data object Installed : DownloadState
}

/** Follows [model]'s download in WorkManager. [deletions] changes after a delete, to re-check the files. */
@Composable
private fun rememberDownloadState(model: VoiceModel, deletions: Int): DownloadState {
    val context = LocalContext.current
    val work = remember(model) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(VoiceDownloadWorker.workName(model))
    }
    val infos by work.collectAsStateWithLifecycle(initialValue = emptyList())
    val info = infos.lastOrNull()
    return remember(info, deletions) {
        when {
            info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED ->
                DownloadState.Downloading(info.progress.getFloat(VoiceDownloadWorker.KEY_PROGRESS, 0f))
            model.isInstalled(context) -> DownloadState.Installed
            info?.state == WorkInfo.State.FAILED ->
                DownloadState.Failed(info.outputData.getString(VoiceDownloadWorker.KEY_ERROR) ?: "Download failed")
            else -> DownloadState.NotDownloaded
        }
    }
}

@Composable
private fun DownloadRow(model: VoiceModel, state: DownloadState, inUse: Boolean, onDelete: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(model.label) },
        supportingContent = {
            when (state) {
                DownloadState.NotDownloaded -> Text("${model.sizeMb} MB")
                is DownloadState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Downloading… ${(state.progress * 100).toInt()}%")
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                is DownloadState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                DownloadState.Installed -> Text(
                    if (inUse) "In use · ${model.sizeMb} MB" else "Downloaded · ${model.sizeMb} MB",
                    color = if (inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            when (state) {
                DownloadState.NotDownloaded ->
                    IconButton(onClick = { VoiceDownloadWorker.start(context, model) }) {
                        Icon(Icons.Rounded.Download, "Download ${model.label}")
                    }
                is DownloadState.Failed ->
                    IconButton(onClick = { VoiceDownloadWorker.start(context, model) }) {
                        Icon(Icons.Rounded.Refresh, "Retry downloading ${model.label}")
                    }
                is DownloadState.Downloading ->
                    IconButton(onClick = { VoiceDownloadWorker.cancel(context, model) }) {
                        Icon(Icons.Rounded.Close, "Cancel download")
                    }
                DownloadState.Installed ->
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete ${model.label}") }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** The engine's voices, or null while they load. They're reloaded when [key] changes. */
@Composable
fun rememberVoiceOptions(engine: VoiceEngine, key: Any? = null): List<VoiceOption>? {
    val context = LocalContext.current
    val options by produceState<List<VoiceOption>?>(null, engine, key) {
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
