package com.example.myvpn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/**
 * لافتة "Update Success" الخضراء. تظهر ملي [trigger] كيولي true وتختفي وحدها
 * بعد [durationMs] (افتراضياً 1.2 ثانية) بغض النظر على مدة المتغير فـ ViewModel.
 *
 * كتتحط بسطر واحد داخل VpnScreen (فأي مكان):  UpdateSuccessBanner(vm.updateSuccess)
 */
@Composable
fun UpdateSuccessBanner(trigger: Boolean, durationMs: Long = 1200L) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger) {
            show = true
            delay(durationMs)
            show = false
        }
    }
    if (!show) return

    val density = LocalDensity.current
    // فوق البطاقة والبانر إلا كانو ظاهرين
    val lift = if (AppConfig.ADS_ENABLED && AdsManager.ready && !AdsManager.adFree) 230.dp else 130.dp
    val offsetY = with(density) { -lift.roundToPx() }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, offsetY),
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        AnimatedVisibility(visibleState = visible, enter = fadeIn()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF16A34A),
                shadowElevation = 8.dp
            ) {
                Text(
                    "Update Success",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
