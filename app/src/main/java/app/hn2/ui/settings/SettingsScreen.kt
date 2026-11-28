package app.hn2.ui.settings

import androidx.compose.foundation.background
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val account = LocalContext.current.account
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
            // Rows for settings sub-pages (Voice, ...) go here.
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
