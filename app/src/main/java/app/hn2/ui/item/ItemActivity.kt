package app.hn2.ui.item

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.hn2.ui.theme.Hn2Theme

/**
 * One story: header, then Comments and Article tabs. Opened from the widget, the story list,
 * or a news.ycombinator.com/item?id= link.
 */
class ItemActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1).takeIf { it > 0 }
            ?: intent.data?.getQueryParameter("id")?.toLongOrNull()
        if (itemId == null) {
            finish()
            return
        }
        val startOnComments = intent.getBooleanExtra(EXTRA_START_ON_COMMENTS, false)
        enableEdgeToEdge()
        setContent {
            Hn2Theme { ItemScreen(itemId, startOnComments, onBack = ::finish) }
        }
    }

    companion object {
        /** Also the widget's action parameter name; keep them in sync. */
        const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_START_ON_COMMENTS = "start_on_comments"

        fun intent(context: Context, itemId: Long, startOnComments: Boolean = false): Intent =
            Intent(context, ItemActivity::class.java)
                .putExtra(EXTRA_ITEM_ID, itemId)
                .putExtra(EXTRA_START_ON_COMMENTS, startOnComments)
    }
}
