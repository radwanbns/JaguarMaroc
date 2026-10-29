package com.example.myvpn

/** إعدادات تُدمج داخل التطبيق عند البناء (تصبح جزءاً من classes.dex). */
object AppConfig {
    /** رابط الاشتراك المدمج (raw من GitHub أو Gist). اتركه "" لتعطيل الميزة. */
    const val SUB_URL = "https://raw.githubusercontent.com/radwanbns/Redo-VPN/main/servers.txt"

    /** true = لا يستطيع المستخدم تغيير الرابط من الإعدادات. */
    const val LOCK_SUB = true



    /** تفعيل إعلانات جوجل (AdMob). false = لا إعلانات ولا نافذة موافقة. */
    const val ADS_ENABLED = true

    /**
     * معرّف وحدة البانر. القيمة الحالية **تجريبية** من جوجل (آمنة للاختبار).
     * قبل النشر استبدلها بمعرّفك الحقيقي: ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
     * (ومعرّف التطبيق الحقيقي في AndroidManifest.xml).
     */
    const val ADMOB_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"

    /** معرّف إعلان ملء الشاشة (Interstitial) — تجريبي، استبدله بمعرّفك الحقيقي. */
    const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    /** معرّف إعلان المكافأة (Rewarded) — تجريبي، استبدله بمعرّفك الحقيقي. */
    const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    /** الحد الأدنى بين إعلانين ملء الشاشة (بالثواني). */
    const val INTERSTITIAL_COOLDOWN_SEC = 180

    /** يظهر إعلان ملء الشاشة عند كل N عملية قطع اتصال (2 = كل مرة ثانية). */
    const val INTERSTITIAL_EVERY_N_DISCONNECTS = 2

    /** مكافأة مشاهدة الإعلان: عدد دقائق بدون أي إعلانات. */
    const val REWARD_AD_FREE_MINUTES = 2
}
