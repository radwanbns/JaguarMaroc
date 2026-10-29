package com.example.myvpn

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Promo copy and URLs are encrypted at rest in source. This is obfuscation, not
 * a secret boundary: the decryption key is packaged in the APK and can be recovered.
 */
internal object PromoSecrets {
    private const val KEY_B64 = "mJrxdH4mlLwyt+RevXzHzsWBNKKPXObDDXvyIlVuNWA="
    private const val NONCE_B64 = "hwXZokund+Iix/fb"
    private const val DATA_B64 = "bHEMc5Sahy5pxiVSudeeHoaFVTXbxuWzjbVrgpOBBB7JLSD2/vblW3wzYC/Tf5zCae0JURAc1NPyxw1fMLzTlz/8tKCBuFbBCrhzRmSbPuYcKYsLNWSR8j9BsTTX8KSoxcWxFkVj2/evHAsa++zkmEDRWeHtfe0D6wAXBRi+kjptuNA5ZHWixN7Wy1XWtDQuDVKkU7RWIFpRPr3bukyYIt6aRQYruR8XxJsZxu98liM9grL7Eu84jbmI4YfZNDoX75dv+DfrRgWRjVZOp4wXIiYROD7CzWQ5mYZPyjg1qexWlHMvxicckiOv/mhIva1UgCIFYMpSVZZow2OW6ldrY8iGQgXYyqIGubMcTajfFcuNaHN9ImbANJwELycSh1iJ1jYORYJzMhue0DlhW0/WOKg="

    private val values: List<String> by lazy {
        val key = Base64.decode(KEY_B64, Base64.DEFAULT)
        val nonce = Base64.decode(NONCE_B64, Base64.DEFAULT)
        val encrypted = Base64.decode(DATA_B64, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        String(cipher.doFinal(encrypted), Charsets.UTF_8).split("|")
    }

    val title: String get() = values[0]
    val description: String get() = values[1]
    val telegramUrl: String get() = values[2]
    val youtubeUrl: String get() = values[3]
    val telegramLabel: String get() = values[4]
    val youtubeLabel: String get() = values[5]
    val hideLabel: String get() = values[6]
}
