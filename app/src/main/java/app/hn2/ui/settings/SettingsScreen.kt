package app.hn2.ui.settings

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hn2.settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.hn2.account
import app.hn2.ui.account.LoginDialog
import app.hn2.ui.account.signedInUser

/** The Settings overview and its sub-pages. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.OVERVIEW) }
    BackHandler(enabled = page != SettingsPage.OVERVIEW) { page = SettingsPage.OVERVIEW }
    when (page) {
        SettingsPage.OVERVIEW -> SettingsOverview(onBack, onOpen = { page = it })
        SettingsPage.VOICE -> VoiceSettingsScreen(onBack = { page = SettingsPage.OVERVIEW })
    }
}

private enum class SettingsPage { OVERVIEW, VOICE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsOverview(onBack: () -> Unit, onOpen: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val account = context.account
    val voice by context.settings.voice.collectAsStateWithLifecycle()
    val voiceOptions = rememberVoiceOptions(voice.engine)
    val user = signedInUser()
    var showLogin by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountHeader(user, onLogIn = { showLogin = true })
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
                Column {
                    SettingsRow(
                        icon = Icons.Rounded.RecordVoiceOver,
                        title = "Voice",
                        summary = voiceSummary(voice, voiceOptions),
                        onClick = { onOpen(SettingsPage.VOICE) },
                    )
                }
            }
            if (user != null) {
                OutlinedButton(
                    onClick = account::logout,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                    Text("Log out", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (showLogin) LoginDialog(onDismiss = { showLogin = false })
}

/** Who's signed in. HN has no profile pictures or email addresses, so the avatar is the name's first letter. */
@Composable
private fun AccountHeader(user: String?, onLogIn: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (user != null) {
                    Text(
                        user.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onPrimaryContainer,
                    )
                } else {
                    Icon(Icons.Rounded.Person, null, tint = colors.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                if (user != null) {
                    Text("Signed in as", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Text(user, style = MaterialTheme.typography.titleLarge)
                } else {
                    Text("Not signed in", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Log in to vote and comment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (user == null) Button(onClick = onLogIn) { Text("Log in") }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
