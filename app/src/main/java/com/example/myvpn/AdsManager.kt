package com.example.myvpn

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * يدير إعلانات AdMob:
 *  - نافذة الموافقة (UMP/GDPR) ثم تهيئة SDK
 *  - بانر (AdBanner) + ملء الشاشة عند قطع الاتصال + مكافأة "بدون إعلانات"
 */
object AdsManager {

    /** تصبح true بعد تهيئة SDK. */
    var ready by mutableStateOf(false)
        private set

    /** true أثناء فترة "بدون إعلانات" (بعد مشاهدة إعلان المكافأة). */
    var adFree by mutableStateOf(false)
        private set
    var adFreeUntil by mutableStateOf(0L)
        private set

    /** إعلان المكافأة محمّل وجاهز للعرض. */
    var rewardedReady by mutableStateOf(false)
        private set

    private var started = false
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val expire = Runnable { refresh() }

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var lastInterstitialAt = 0L
    private var disconnects = 0

    fun init(activity: Activity) {
        app = activity.applicationContext
        prefs = app.getSharedPreferences("ads", 0)
        adFreeUntil = prefs.getLong("adFreeUntil", 0L)
        refresh()
        if (!AppConfig.ADS_ENABLED) return

        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity, params,
            {
                // يعرض النموذج فقط عند الحاجة (مثلاً مستخدمو أوروبا)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { startAds(info) }
            },
            { startAds(info) }
        )
        if (info.canRequestAds()) startAds(info) // موافقة محفوظة من جلسة سابقة
    }

    private fun startAds(info: ConsentInformation) {
        if (started || !info.canRequestAds()) return
        started = true
        Thread {
            MobileAds.initialize(app) {
                main.post {
                    ready = true
                    ensureLoaded()
                }
            }
        }.start()
    }

    /** يُستدعى من onResume وعند انتهاء مدة المكافأة. */
    fun refresh() {
        if (!::prefs.isInitialized) return
        val left = adFreeUntil - System.currentTimeMillis()
        adFree = left > 0
        main.removeCallbacks(expire)
        if (left > 0) main.postDelayed(expire, left + 500) else ensureLoaded()
    }

    // ───────────── التحميل المسبق ─────────────
    private fun ensureLoaded() {
        if (!ready || adFree || !AppConfig.ADS_ENABLED) return
        loadInterstitial()
        loadRewarded()
    }

    private fun loadInterstitial() {
        if (interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(app, AppConfig.INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; loadingInterstitial = false }
                override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null; loadingInterstitial = false }
            })
    }

    private fun loadRewarded() {
        if (rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(app, AppConfig.REWARDED_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; rewardedReady = true; loadingRewarded = false }
                override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null; rewardedReady = false; loadingRewarded = false }
            })
    }

    // ───────────── ملء الشاشة: عند قطع الاتصال بنفسه ─────────────
    fun onUserDisconnected(activity: Activity) {
        if (!ready || adFree || !AppConfig.ADS_ENABLED) return
        disconnects++
        if (disconnects % AppConfig.INTERSTITIAL_EVERY_N_DISCONNECTS != 0) { ensureLoaded(); return }
        val now = System.currentTimeMillis()
        if (now - lastInterstitialAt < AppConfig.INTERSTITIAL_COOLDOWN_SEC * 1000L) return
        val ad = interstitial ?: run { ensureLoaded(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { interstitial = null; ensureLoaded() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { interstitial = null; ensureLoaded() }
        }
        lastInterstitialAt = now
        ad.show(activity)
    }

    // ───────────── المكافأة: بدون إعلانات لمدة محددة ─────────────
    fun showRewarded(activity: Activity) {
        val ad = rewarded ?: return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { rewarded = null; rewardedReady = false; ensureLoaded() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { rewarded = null; rewardedReady = false; ensureLoaded() }
        }
        ad.show(activity) { grantAdFree() }
    }

    private fun grantAdFree() {
        val until = System.currentTimeMillis() + AppConfig.REWARD_AD_FREE_MINUTES * 60_000L
        adFreeUntil = until
        prefs.edit().putLong("adFreeUntil", until).apply()
        refresh()
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** بانر إعلاني متجاوب. لا يظهر إن كانت الإعلانات معطلة أو غير جاهزة أو في فترة المكافأة. */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!AppConfig.ADS_ENABLED || !AdsManager.ready || AdsManager.adFree) return
    val width = LocalConfiguration.current.screenWidthDp - 48 // نفس هوامش الشاشة
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { c ->
            AdView(c).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(c, width))
                adUnitId = AppConfig.ADMOB_BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() }
    )
}

/** زر "شاهد إعلاناً = بدون إعلانات"، أو نص يوضح وقت انتهاء المكافأة. */
@Composable
fun RewardAdButton(modifier: Modifier = Modifier) {
    if (!AppConfig.ADS_ENABLED || !AdsManager.ready) return
    val ctx = LocalContext.current
    if (AdsManager.adFree) {
        val until = remember(AdsManager.adFreeUntil) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(AdsManager.adFreeUntil))
        }
        Text(
            "بدون إعلانات حتى $until",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier
        )
        return
    }
    if (!AdsManager.rewardedReady) return
    OutlinedButton(
        onClick = { ctx.findActivity()?.let { AdsManager.showRewarded(it) } },
        modifier = modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Default.CardGiftcard, null)
        Spacer(Modifier.width(8.dp))
        Text("شاهد إعلاناً = ${AppConfig.REWARD_AD_FREE_MINUTES} دقيقة بدون إعلانات", fontSize = 13.sp)
    }
}
