package com.noor.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.noor.tv.data.ClassicRepo
import com.noor.tv.data.DiskCache
import com.noor.tv.data.PrayerRepo
import com.noor.tv.data.Prefs
import com.noor.tv.data.QuranRepo
import com.noor.tv.data.RadioRepo
import com.noor.tv.net.Http

class NoorApp : Application(), ImageLoaderFactory {
    lateinit var prefs: Prefs
    lateinit var cache: DiskCache
    lateinit var quran: QuranRepo
    lateinit var prayer: PrayerRepo
    lateinit var radio: RadioRepo
    lateinit var classic: ClassicRepo

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        prefs = Prefs(this)
        cache = DiskCache(this)
        quran = QuranRepo(cache)
        prayer = PrayerRepo(cache)
        radio = RadioRepo(this, cache)
        classic = ClassicRepo(this, cache)
    }

    // 1GB RAM: keep the image memory cache small and reuse the ISRG-aware HTTP client.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Http.client }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.10).build() }
        .crossfade(false)
        .allowRgb565(true)
        .build()
}
