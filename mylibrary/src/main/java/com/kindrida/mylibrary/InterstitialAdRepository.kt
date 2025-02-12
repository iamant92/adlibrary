package com.kindrida.mylibrary

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class InterstitialAdRepository {

    private var interstitialAd: InterstitialAd? = null
    private var loadedTime: Long = 0

    fun loadInterstitialAd(context: Context,
                           adUnitId :String,
                           onAdLoaded: (() -> Unit)? = null,
                           onAdFailedToLoad: (() -> Unit)? = null) {
        if (isAdAvailable()) {
            onAdLoaded?.invoke()
            return
        }
        val adRequest = AdRequest.Builder().build()
        Log.d("InterstitialAdManager", "function to load is calling")
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    loadedTime = System.currentTimeMillis()
                    onAdLoaded?.invoke()

                }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.d("InterstitialAdManager", loadAdError.toString())
                    onAdFailedToLoad?.invoke()
                }
            }
        )
    }

    fun clearInterstitialAd() {
        interstitialAd = null
    }

    fun getInterstitialAd(): InterstitialAd? {
        return if (isAdAvailable()) interstitialAd else null
    }

    private fun isAdAvailable() : Boolean{
        return interstitialAd != null && wasLoadTimeLessThanHoursAgo (1, loadedTime)
    }
}
