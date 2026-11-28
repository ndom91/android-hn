package app.hn2

import android.app.Application
import app.hn2.data.AppSettings
import app.hn2.data.HnAccount
import app.hn2.data.HnApi
import app.hn2.data.HnRepository
import app.hn2.listen.AudioCache
import app.hn2.listen.ListenSession
import app.hn2.listen.VoiceModel
import app.hn2.util.extraAdHosts
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class Hn2App : Application() {

    lateinit var repository: HnRepository
        private set
    lateinit var account: HnAccount
        private set
    lateinit var settings: AppSettings
        private set
    /** The shared HTTP client. Use `newBuilder().cache(null)` for big downloads, which would flush the cache. */
    lateinit var http: OkHttpClient
        private set
    val listen = ListenSession()

    // Loaded on first use, which is on the WebView's request thread, not the main thread.
    val adHosts: Set<String> by lazy {
        val easyList = assets.open("adhosts.txt").bufferedReader().useLines { it.toHashSet() }
        easyList + extraAdHosts
    }

    override fun onCreate() {
        super.onCreate()
        val client = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024))
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        http = client
        repository = HnRepository(HnApi(client))
        account = HnAccount(client, getSharedPreferences("account", MODE_PRIVATE))
        settings = AppSettings(getSharedPreferences("settings", MODE_PRIVATE))
        // Generated speech is kept a few days so a story can be replayed without regenerating it.
        Thread {
            AudioCache.deleteOld(AudioCache.dir(this))
            VoiceModel.RETIRED_ARCHIVES.forEach { File(VoiceModel.voicesDir(this), it).deleteRecursively() }
        }.start()
    }
}

val android.content.Context.repository: HnRepository
    get() = (applicationContext as Hn2App).repository

val android.content.Context.account: HnAccount
    get() = (applicationContext as Hn2App).account

val android.content.Context.adHosts: Set<String>
    get() = (applicationContext as Hn2App).adHosts

val android.content.Context.settings: AppSettings
    get() = (applicationContext as Hn2App).settings

val android.content.Context.listen: ListenSession
    get() = (applicationContext as Hn2App).listen

val android.content.Context.http: OkHttpClient
    get() = (applicationContext as Hn2App).http
