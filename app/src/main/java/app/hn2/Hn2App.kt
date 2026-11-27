package app.hn2

import android.app.Application
import app.hn2.data.HnAccount
import app.hn2.data.HnApi
import app.hn2.data.HnRepository
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

    override fun onCreate() {
        super.onCreate()
        val client = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024))
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        repository = HnRepository(HnApi(client))
        account = HnAccount(client, getSharedPreferences("account", MODE_PRIVATE))
    }
}

val android.content.Context.repository: HnRepository
    get() = (applicationContext as Hn2App).repository

val android.content.Context.account: HnAccount
    get() = (applicationContext as Hn2App).account
