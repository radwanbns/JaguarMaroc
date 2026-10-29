package com.example.myvpn

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Obfuscated UI strings. The key is packaged in the APK, so this is obfuscation, not absolute secrecy. */
internal object PromoSecrets {
    private const val KEY = "wDZk2kyUea2CThv0eTizW/hxCjovXw88m0bURAen88E="
    private fun open(value: String): String = try {
        val raw = Base64.decode(value, Base64.DEFAULT)
        val nonce = raw.copyOfRange(0, 12)
        val ciphertext = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(Base64.decode(KEY, Base64.DEFAULT), "AES"), GCMParameterSpec(128, nonce))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (_: Exception) { "" }

    val title get() = open("8kjRckCkUUqf1gyTSO9muyKhUp2h5I1sT6p8iOGaraI8d8tu2w==")
    val description get() = open("IZOK2a7wtyTfLLdCWb2V6DvaNOTSssfAeZdA412knBV+66N+rVmIHXMyyWcs8CEtwQgS8jlqYJWFMQ76XNv1dRSx24GsJBoO77wxGzaUV9nbWJ0RWoFj5ZOoMg4H1IYHpj1I+sXPhh6FOwgcg2wouCU=")
    val telegramUrl get() = open("adfkH+6Cj3sQEHd61qVcrzKX/N4mL+UWdu1EtOKq5tHIrHgjpX4/Cy6wxG4NaMN6T+MNgbAj")
    val joinLabel get() = open("PanmSCZMLmXady2o49O9lKV+72ImcyN/CizOLUVbJrrgEuwqjw0ZI2JapvPez8ZZXZMdZSqBYPw=")
    val laterLabel get() = open("usZYZITI0twx22TQ+lagn5Y7HqaMMgtFO/opPEjNsaQIWKDzKP/Qjg==")
}
