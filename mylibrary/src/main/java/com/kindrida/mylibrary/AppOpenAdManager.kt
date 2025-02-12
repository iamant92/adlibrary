package com.kindrida.mylibrary

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class AppOpenAdManager(private val repository: AppOpenAdRepository) {

    private var isShowingAd: Boolean = false
    private var activityRef: WeakReference<Activity>? = null


    fun showAdIfAvailable(
        activity: Activity,
        adId: String,
        onAdDismissed: (() -> Unit)? = null,
        adFinished : (() -> Unit)? = null
    ) {
        if (isShowingAd) {
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val appOpenAd = repository.getAppOpenAd()

            if (appOpenAd != null) {
                activityRef = WeakReference(activity)
                withContext(Dispatchers.Main) {
                    appOpenAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            isShowingAd = false
                            repository.clearAd()
                            onAdDismissed?.invoke()
                            adFinished?.invoke()
                            loadNewAd(adId)
                            activityRef?.clear()
                        }

                        override fun onAdFailedToShowFullScreenContent(p0 : AdError) {
                            super.onAdFailedToShowFullScreenContent(p0)
                            isShowingAd = false
                            repository.clearAd()
                            adFinished?.invoke()
                            loadNewAd(adId)
                            activityRef?.clear()
                        }

                        override fun onAdShowedFullScreenContent() {
                            super.onAdShowedFullScreenContent()
                            isShowingAd = true
                        }
                    }

                    activityRef?.get()?.let { context ->
                        appOpenAd.show(context)
                    }
                }
            } else {
                withContext(Dispatchers.Main){
                    loadNewAd(adId)
                }
            }
        }
    }

    private fun loadNewAd(string: String) {
            repository.loadAd(string)


    }
}
