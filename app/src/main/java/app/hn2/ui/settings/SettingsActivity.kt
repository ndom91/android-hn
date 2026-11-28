package app.hn2.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.hn2.ui.theme.Hn2Theme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Hn2Theme { SettingsScreen(onBack = ::finish) }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}
