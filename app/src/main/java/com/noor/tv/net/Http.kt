package com.noor.tv.net

import android.content.Context
import com.noor.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android 6 does not ship the ISRG Root X1 certificate that Let's Encrypt uses, so many HTTPS
 * servers fail with "trust anchor not found". This client trusts the system CAs plus the bundled
 * ISRG root, and is used for every request in the app (API calls, images and media streams).
 */
object Http {
    lateinit var client: OkHttpClient
        private set

    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun init(context: Context) {
        val tm = buildTrustManager(context)
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
        client = OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, tm)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "NoorTV/1.0 (Android TV)")
                        .build()
                )
            }
            .build()
    }

    private fun buildTrustManager(context: Context): X509TrustManager {
        val systemTm = defaultTrustManager(null)
        val cf = CertificateFactory.getInstance("X.509")
        val isrg = context.resources.openRawResource(R.raw.isrg_root_x1).use {
            cf.generateCertificate(it) as X509Certificate
        }
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("isrg_root_x1", isrg)
        }
        val extraTm = defaultTrustManager(ks)
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
                systemTm.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                try {
                    systemTm.checkServerTrusted(chain, authType)
                } catch (e: Exception) {
                    extraTm.checkServerTrusted(chain, authType)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> =
                systemTm.acceptedIssuers + extraTm.acceptedIssuers
        }
    }

    private fun defaultTrustManager(ks: KeyStore?): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $url")
            r.body?.string() ?: throw IOException("Empty body for $url")
        }
    }
}
