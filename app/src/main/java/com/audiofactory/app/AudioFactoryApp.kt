package com.audiofactory.app

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebView

class AudioFactoryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ensure cookies are enabled and persistent
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }
        // Enable WebView safe debugging only in debug builds if needed
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        } else {
            WebView.setWebContentsDebuggingEnabled(false)
        }
    }
}
