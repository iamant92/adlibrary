package com.kindrida.mylibrary

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class BannerAdRepository(private val application: Application) {

    private fun getSize(specifiedWidthDp: Int? = null): AdSize {
        val windowManager = application.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = application.applicationContext.resources.displayMetrics
        val density = displayMetrics.density

        val adWidth = if (specifiedWidthDp != null) {
            // Use the specified width
            specifiedWidthDp
        } else {
            // Get full screen width
            val widthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                windowMetrics.bounds.width()
            } else {
                val outMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getMetrics(outMetrics)
                outMetrics.widthPixels
            }
            (widthPixels / density).toInt()
        }

//        val adWidth = (widthPixels / density).toInt()
        Log.d("BannerRepository", "getSize: $adWidth")
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(application.applicationContext, adWidth)
    }


    suspend fun createAndLoadAdView(
        adUnitId: String,
        widthDp: Int? = null
    ): AdView =
        withContext(Dispatchers.IO) {
        AdView(application.applicationContext).apply {
            this.adUnitId = adUnitId
            setAdSize(getSize(widthDp))
            val adRequest = AdRequest.Builder().build()
            withContext(Dispatchers.Main) {
                loadAd(adRequest)
            }
        }
    }

}