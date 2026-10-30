package com.example.myvpn

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import java.util.Locale

/** إعدادات نظام الوقت (كلها هنا باش ما تحتاجش تعدل AppConfig). */
object AdsTimeConfig {
    /** true = الـ VPN كيخدم غير ملي كاين وقت. الوقت كيتزاد بمشاهدة إعلان مكافأة. */
    const val TIME_GATE_ENABLED = true

    /** الوقت اللي كيتزاد عن كل إعلان مكافأة (بالدقائق). 60 = ساعة. */
    const val REWARD_MINUTES = 60

    /** السقف الأقصى للوقت المتراكم (بالدقائق). 1440 = 24 ساعة. */
    const val MAX_TOTAL_MINUTES = 24 * 60

    /** وقت هدية عند أول تشغيل (بالدقائق). 0 = بلا هدية. */
    const val WELCOME_MINUTES = 0
}

/**
 * يدير إعلانات AdMob + نظام الوقت:
 *  - نافذة الموافقة (UMP/GDPR) ثم تهيئة SDK
 *  - بانر + ملء الشاشة عند قطع الاتصال + إعلان مكافأة (+ساعة)
 *  - ملي كيوصل الوقت للصفر: كيتقطع الـ VPN ولا كيتعاود حتى يتزاد وقت
 */
object AdsManager {

    /** تصبح true بعد تهيئة SDK. */
    var ready by mutableStateOf(false)
        private set

    /** true ما دام كاين وقت متبقي (بدون إعلانات). */
    var adFree by mutableStateOf(false)
        private set

    /** لحظة انتهاء الوقت (ميلي ثانية). */
    var adFreeUntil by mutableStateOf(0L)
        private set

    /** إعلان المكافأة محمّل وجاهز للعرض. */
    var rewardedReady by mutableStateOf(false)
        private set

    /** يُستدعى عند انتهاء الوقت (مثلاً لقطع الـ VPN). */
    var onTimeExpired: (() -> Unit)? = null

    /** هل نظام "الـ VPN مرتبط بالوقت" مفعّل؟ */
    val gateOn: Boolean
        get() = AppConfig.ADS_ENABLED && AdsTimeConfig.TIME_GATE_ENABLED

    private var started = false
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val tick = Runnable { refresh() }

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

        if (gateOn && AdsTimeConfig.WELCOME_MINUTES > 0 && !prefs.getBoolean("welcome", false)) {
            prefs.edit().putBoolean("welcome", true).apply()
            addTime(AdsTimeConfig.WELCOME_MINUTES)
        }
        adFree = adFreeUntil > System.currentTimeMillis()
        refresh()
        if (!AppConfig.ADS_ENABLED) return

        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { startAds(info) }
            },
            { startAds(info) }
        )
        if (info.canRequestAds()) startAds(info)
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

    /** يُستدعى كل 30 ثانية ومن onResume وعند اقتراب انتهاء الوقت. */
    fun refresh() {
        if (!::prefs.isInitialized) return
        val left = adFreeUntil - System.currentTimeMillis()
        val was = adFree
        adFree = left > 0
        main.removeCallbacks(tick)
        if (left > 0) {
            main.postDelayed(tick, minOf(left + 200L, 30_000L))
        } else {
            if (was && gateOn) {
                Toast.makeText(
                    app, "انتهى الوقت — تم قطع الاتصال. شاهد إعلاناً لإضافة ساعة", Toast.LENGTH_LONG
                ).show()
                onTimeExpired?.invoke()
            }
            ensureLoaded()
        }
    }

    /** يُستدعى قبل تشغيل الـ VPN. false = ممنوع (انتهى الوقت). */
    fun canConnect(ctx: Context): Boolean {
        if (!gateOn) return true
        if (adFreeUntil > System.currentTimeMillis()) return true
        ensureLoaded()
        Toast.makeText(
            ctx,
            if (!ready) "جاري تحميل الإعلانات، حاول بعد لحظات"
            else "انتهى الوقت — شاهد إعلاناً للحصول على ساعة إضافية",
            Toast.LENGTH_LONG
        ).show()
        return false
    }

    // ───────────── التحميل المسبق ─────────────
    private fun ensureLoaded() {
        if (!ready || !AppConfig.ADS_ENABLED) return
        if (!adFree) loadInterstitial()
        loadRewarded() // نحتاجه دائماً: باش يقدر المستخدم يزيد وقت وهو عندو وقت
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

    // ───────────── المكافأة: + وقت ─────────────
    fun showRewarded(activity: Activity) {
        val ad = rewarded
        if (ad == null) {
            Toast.makeText(activity, "الإعلان غير جاهز، حاول بعد لحظات", Toast.LENGTH_SHORT).show()
            ensureLoaded()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { rewarded = null; rewardedReady = false; ensureLoaded() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { rewarded = null; rewardedReady = false; ensureLoaded() }
        }
        ad.show(activity) { addTime(AdsTimeConfig.REWARD_MINUTES) }
    }

    /** يزيد وقت فوق المتبقي (متراكم) مع سقف أقصى. */
    private fun addTime(minutes: Int) {
        val now = System.currentTimeMillis()
        val base = maxOf(now, adFreeUntil)
        val until = minOf(
            base + minutes * 60_000L,
            now + AdsTimeConfig.MAX_TOTAL_MINUTES * 60_000L
        )
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

/** بانر إعلاني متجاوب. مخفي ما دام كاين وقت متبقي. */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!AppConfig.ADS_ENABLED || !AdsManager.ready || AdsManager.adFree) return
    val width = LocalConfiguration.current.screenWidthDp - 48
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

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}

/** بطاقة الوقت المتبقي (عدّاد تنازلي) + زر "+ ساعة" بمشاهدة إعلان. */
@Composable
fun RewardAdButton(modifier: Modifier = Modifier) {
    if (!AppConfig.ADS_ENABLED) return
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val gate = AdsManager.gateOn
    val left = (AdsManager.adFreeUntil - now).coerceAtLeast(0L)
    val active = left > 0
    val accent = when {
        !active -> Color(0xFFEF4444)
        left < 10 * 60_000L -> Color(0xFFF59E0B)
        else -> Color(0xFF22D3EE)
    }
    val minutes = AdsTimeConfig.REWARD_MINUTES
    val addLabel = if (minutes % 60 == 0) "+ ${minutes / 60} ساعة" else "+ $minutes د"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F1A33),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Timer, null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (!gate) "بدون إعلانات" else if (active) "الوقت المتبقي" else "انتهى الوقت",
                        fontSize = 12.sp, color = Color(0xFF9FB0CC)
                    )
                    Text(
                        formatTime(left),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (active) Color.White else accent
                    )
                }
                Button(
                    onClick = { ctx.findActivity()?.let { AdsManager.showRewarded(it) } },
                    enabled = AdsManager.rewardedReady,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22D3EE),
                        contentColor = Color(0xFF0A0F1F),
                        disabledContainerColor = Color(0xFF22304F),
                        disabledContentColor = Color(0xFF7C8BA8)
                    )
                ) {
                    Icon(Icons.Default.CardGiftcard, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (AdsManager.rewardedReady) addLabel else "تحميل…",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (left.toFloat() / (minutes * 60_000f)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = accent,
                trackColor = Color(0xFF1B2A4A)
            )
            if (gate && !active) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "شاهد إعلاناً للحصول على ساعة وتشغيل VPN",
                    fontSize = 12.sp, color = Color(0xFFFCA5A5)
                )
            }
        }
    }
}
