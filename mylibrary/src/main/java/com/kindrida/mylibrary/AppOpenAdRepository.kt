package com.kindrida.mylibrary

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd


class AppOpenAdRepository(private val context: Context) {

    private var appOpenAd: AppOpenAd? = null
    private var loadTime: Long = 0


    fun loadAd(adUnitId: String, onAdLoaded: (() -> Unit)? = null) {
        if (isAdAvailable()) {
            onAdLoaded?.invoke()
            return
        }
        try{

            val adRequest = AdRequest.Builder().build()
            AppOpenAd.load(context, adUnitId, adRequest, object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        loadTime = System.currentTimeMillis()
                        onAdLoaded?.invoke()
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        appOpenAd = null
                    }
                }
            )
        } catch (e:Exception){
            Log.e("AppOpenAdRepository", "Error loading ad: ${e.message}", e)
        }

    }

    fun getAppOpenAd(): AppOpenAd? {
        return if (isAdAvailable()) appOpenAd else null
    }

    fun clearAd() {
        appOpenAd = null
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanHoursAgo(4, loadTime)
    }

}

