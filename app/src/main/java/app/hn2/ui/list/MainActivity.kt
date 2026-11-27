package app.hn2.ui.list

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.hn2.data.Section
import app.hn2.ui.theme.Hn2Theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val section = Section.fromName(intent.getStringExtra(EXTRA_SECTION))
        setContent {
            Hn2Theme { StoryListScreen(initialSection = section) }
        }
    }

    companion object {
        const val EXTRA_SECTION = "section"

        fun intent(context: Context, section: Section): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_SECTION, section.name)
    }
}
