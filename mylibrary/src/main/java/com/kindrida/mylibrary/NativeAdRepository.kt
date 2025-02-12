package com.kindrida.mylibrary

import android.app.Application
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd

class NativeAdRepository(private val context: Application) {

    private val nativeAds: MutableList<NativeAd> = mutableListOf()

    /**
     * Load a single native ad.
     */
    private fun loadNativeAd(
        adUnitId: Int,
        onAdLoaded: (NativeAd) -> Unit,
        onAdFailedToLoad: ((String) -> Unit)?
    ) {
        val adLoader = AdLoader.Builder(context, context.getString(adUnitId))
            .forNativeAd { nativeAd ->
                nativeAds.add(nativeAd)
                onAdLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    onAdFailedToLoad?.invoke(adError.message)
                }

            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Load multiple native ads.
     */
    fun loadMultipleNativeAds(
        adUnitId: Int,
        count: Int,
        onAllAdsLoaded: ((List<NativeAd>) -> Unit)? = null, // Optional callback
        onAdFailedToLoad: ((String) -> Unit)? = null // Optional failure callback
    ) {
        val nativeAds = mutableListOf<NativeAd>() // To hold successfully loaded ads
        var adsLoaded = 0

        // Define a helper function to check if all ads are loaded
        fun checkIfAllAdsLoaded() {
            if (adsLoaded == count) {
                // Call the onAllAdsLoaded callback with the successfully loaded ads
                onAllAdsLoaded?.invoke(nativeAds)
            }
        }

        repeat(count) {
            loadNativeAd(
                    adUnitId = adUnitId,
                    onAdLoaded = { ad ->
                        nativeAds.add(ad) // Add successfully loaded ad to the list
                        adsLoaded++ // Increment ads loaded count
                        checkIfAllAdsLoaded() },
                    onAdFailedToLoad = { error ->
                        adsLoaded++ // Even if an ad fails, increment the count
                        onAdFailedToLoad?.invoke(error) // Call the failure callback if provided
                        checkIfAllAdsLoaded()
            })
        }
    }


    /**
     * Get the list of loaded native ads.
     */
    fun getNativeAds(): List<NativeAd> {
        return nativeAds
    }

    /**
     * Clear all loaded native ads from memory.
     */
    fun clearNativeAds() {
        nativeAds.forEach { it.destroy() }
        nativeAds.clear()
    }
}
