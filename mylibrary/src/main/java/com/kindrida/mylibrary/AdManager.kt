package com.kindrida.mylibrary


import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean


object AdManager {
    private lateinit var appOpenAdManager: AppOpenAdManager
    private lateinit var interstitialAdRepository: InterstitialAdRepository
    private lateinit var bannerAdRepository: BannerAdRepository
    private lateinit var nativeAdRepository: NativeAdRepository
    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    private lateinit var consentManager: ConsentManager
    private lateinit var interstitialId: String
    private lateinit var appOpenId: String
    private lateinit var bannerId: String


    private lateinit var sharedPreferences: SharedPreferences
    private val lock = Any()
    private var adCounter: Int = 0
    private var interstitialLoaded: Boolean = false
    private var adIsShowing: Boolean = false
    var readyForAd: Boolean = false
        private set

    private const val PREFS_NAME = "AdPrefs"
    private const val KEY_AD_COUNTER = "ad_counter"
    private const val KEY_READY_FOR_AD = "ready_for_ad"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activityWeakReference: WeakReference<Activity>? = null

    /**
     * Gathers user consent for personalized ads and initializes the Mobile Ads SDK.
     *
     * This function should be called before making any ad requests to ensure compliance with
     * user privacy regulations and to properly initialize the AdMob SDK.
     *
     * **Important:** The provided context **must** be an Activity context, as it's used for
     * displaying the consent form.
     *
     * @param context The Activity context used for gathering consent and initializing the SDK.
     */
    fun gatherConsentAndInitializeAdSdk(
        context: Activity,
        interstitialAdId: String,
        appOpenAdId: String,
        bannerAdId: String,
        onConsentGathered : (() -> Unit)? = null
    ) {
        interstitialId = interstitialAdId
        appOpenId = appOpenAdId
        bannerId = bannerAdId
        consentManager = ConsentManager.getInstance(context.applicationContext)
        consentManager.gatherConsent(context) { consentError ->
            if (consentError != null) {
                // Consent not obtained in current session.
                Log.w("ConsentInitialized", String.format("%s: %s", consentError.errorCode, consentError.message))
            }
            onConsentGathered?.invoke()

            if (consentManager.canRequestAds) {
                initializeMobileAdsSdk(context)
            }
        }

        if (consentManager.canRequestAds) {
            initializeMobileAdsSdk(context.applicationContext)
        }
    }

    private fun initializeMobileAdsSdk(context: Context) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(context.applicationContext)
        }
        initializeRepositories(context.applicationContext as Application)
    }

    private fun initializeRepositories(context: Application) {
        try {
            bannerAdRepository = BannerAdRepository(context)
            appOpenAdManager = AppOpenAdManager(
                    AppOpenAdRepository(context.applicationContext)
            )
            interstitialAdRepository = InterstitialAdRepository()
            nativeAdRepository = NativeAdRepository(context)
            sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadAdCounter()
        } catch (e: Exception) {
            Log.e("AdManager", "Error initializing AdManager: ${e.message}")
        }
    }

    fun incrementAdCounter(activity: Activity, showCounter : Int = 10, onAdDecision: (() -> Unit)? = null) {
        require(value = showCounter >= 5){"showCounter must be greater than or equal to 5"}
        synchronized(lock) {
            try {
                if(adCounter >= showCounter && interstitialLoaded && readyForAd && !adIsShowing) {
                    showInterstitialAd(activity){
                        onAdDecision?.invoke()
                    }
                    return
                }
                onAdDecision?.invoke()
                adCounter++
                Log.d("AdManager", "incrementAdCounter: $adCounter")
                if (adCounter >= 4 && !interstitialLoaded) {
                    loadInterstitialAd(activity.applicationContext)
                } else if (adCounter > 4) {
                    readyForAd = true
                }
                saveAdCounter()
            } catch (e : Exception) {
                Log.e("AdManager", "Error incrementing ad counter: ${e.message}")
            }
        }
    }

    fun getBannerAdView(widthDp: Int? = null, onAdViewLoaded: (AdView) -> Unit ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (consentManager.canRequestAds){
                    val adView = bannerAdRepository.createAndLoadAdView(bannerId, widthDp)
                    onAdViewLoaded(adView)
                }
            } catch (e: Exception) {
                Log.e("AdManager", "Error getting banner ad view: ${e}")
            }
        }
    }

    fun showInterstitialAd(
        activity: Activity,
        onAdShowed: (() -> Unit)? = null,
        onAdDismissed: (() -> Unit)? = null
    ) {
        try {
            if (!readyForAd || !interstitialLoaded || adIsShowing) {
                onAdDismissed?.invoke()
//                Log.w("AdManager", "showInterstitialAd: not ready for Ad \n " +
//                        "readyForAd = $readyForAd \n " +
//                        "interstitialLoaded = $interstitialLoaded \n" +
//                        "\"adIsShowing = $adIsShowing \\n \""
//                )
                return
            }
            adIsShowing = true

            activityWeakReference = WeakReference(activity)

            val interstitialAd = interstitialAdRepository.getInterstitialAd()
            interstitialAdRepository.clearInterstitialAd() // Todo: need to find another reliable way
            interstitialAd?.fullScreenContentCallback =
                object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        super.onAdShowedFullScreenContent()
                        onAdShowed?.invoke()
                    }

                    override fun onAdDismissedFullScreenContent() {
                        super.onAdDismissedFullScreenContent()
                        adIsShowing = false
                        resetAdCounter()
                        activityWeakReference?.clear()
                        onAdDismissed?.invoke()
                    }

                    override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                        super.onAdFailedToShowFullScreenContent(p0)
                        Log.d("AdManager", "onAdFailedToShowFullScreenContent: $p0")
                        adIsShowing = false
                        resetAdCounter()
                        activityWeakReference?.clear()
                        onAdDismissed?.invoke()
                    }

                }
            activityWeakReference?.get()?.let { context ->

                interstitialAd?.show(context) ?: {
                    interstitialLoaded = false
                    loadInterstitialAd(context.applicationContext)
                    onAdDismissed?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e("AdManager", "Error showing interstitial ad: ${e.message}")
            adIsShowing = false
            onAdDismissed?.invoke()
        }
    }

    fun showAppOpenAd(activity: Activity, adUnitId: String) {
        try {
            if (consentManager.canRequestAds){
                Log.d("AdManager" , "showAppOpenAd: ")
                adIsShowing = true
                appOpenAdManager.showAdIfAvailable(
                    activity = activity,
                    adId = adUnitId,
                    onAdDismissed = {
                        resetAdCounter() },
                    adFinished = {
                        adIsShowing = false
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("AdManager", "Error showing app open ad: ${e.message}")
            adIsShowing = false
        }

    }

    fun loadNativeAds(
        adUnitId: Int ,
        count: Int ,
        onAllAdsLoaded: ((List<NativeAd>) -> Unit)? = null ,
        onAdFailedToLoad: ((String) -> Unit)? = null
    ) {
        if (consentManager.canRequestAds){

            nativeAdRepository.loadMultipleNativeAds(
                    adUnitId = adUnitId,
                    count = count,
                    onAllAdsLoaded = { ads ->
                        onAllAdsLoaded?.invoke(ads)
                    },
                    onAdFailedToLoad = { errorMessage ->
                        onAdFailedToLoad?.invoke(errorMessage)
                    }
            )
        }else{
            onAdFailedToLoad?.invoke("Consent not obtained")
        }
    }


    fun getNativeAds(): List<NativeAd> {
        return nativeAdRepository.getNativeAds()
    }

    fun clearNativeAds() {
        nativeAdRepository.clearNativeAds()
    }

    private fun loadInterstitialAd(context: Context) {
        try {
            if (consentManager.canRequestAds && !interstitialLoaded) {
                interstitialAdRepository.loadInterstitialAd(
                    context = context.applicationContext,
                    adUnitId = interstitialId,
                    onAdLoaded = {
                        Log.d("AdManager", "Interstitial ad loaded")
                        interstitialLoaded = true
                                 },
                    onAdFailedToLoad = {
                        Log.d("AdManager", "Interstitial ad failed to load")
                        interstitialLoaded = false
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("AdManager", "Error loading interstitial ad: ${e.message}")
        }

    }

    private fun loadAdCounter() {
        scope.launch {
            synchronized(lock) {
                try {
                    adCounter = sharedPreferences.getInt(KEY_AD_COUNTER, 0)
                    if (adCounter >= 4) {

                    } else {
                        sharedPreferences.getInt(KEY_AD_COUNTER, 0)
                    }

                    readyForAd = sharedPreferences.getBoolean(KEY_READY_FOR_AD, false)
                } catch (e : Exception) {
                    Log.e("AdManager", "Error loading ad counter: ${e.message}")
                }
            }
        }
    }

    private fun saveAdCounter() {
        scope.launch {
            synchronized(lock) {
                try {
                    sharedPreferences.edit()
                        .putInt(KEY_AD_COUNTER, adCounter)
                        .putBoolean(KEY_READY_FOR_AD, readyForAd)
                        .apply()
                } catch (e : Exception) {
                    Log.e("AdManager", "Error saving ad counter: ${e.message}")
                }
            }
        }
    }

    fun resetAdCounter() {
        scope.launch {
            synchronized(lock) {
                try {
                    adCounter = 0
                    readyForAd = false
                    interstitialLoaded = false
                    saveAdCounter()
                } catch (e : Exception) {
                    Log.e("AdManager", "Error resetting ad counter: ${e.message}")
                }
            }
        }
    }
}
