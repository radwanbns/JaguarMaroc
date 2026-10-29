package com.example.myvpn

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wireguard.android.backend.GoBackend

private val OnColors = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF00212B),
    secondary = Color(0xFF8B5CF6),
    background = Color(0xFF0A0F1F),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF0F1730),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF16203D),
    onSurfaceVariant = Color(0xFF9FB0CC),
    surfaceContainer = Color(0xFF101830),
    surfaceContainerHigh = Color(0xFF141D38),
    surfaceContainerHighest = Color(0xFF1A2547),
    outline = Color(0xFF2B3A63),
    error = Color(0xFFFF6B6B)
)

const val WS_TEMPLATE =
    "GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]"

class MainActivity : ComponentActivity() {

    private val vm: VpnViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) vm.connect() else vm.onPermissionDenied()
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        window.statusBarColor = 0xFF0A0F1F.toInt()
        window.navigationBarColor = 0xFF0A0F1F.toInt()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        setContent {
            MaterialTheme(colorScheme = OnColors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VpnScreen(vm, onToggle = ::toggle)
                }
            }
        }
    }

    private fun toggle() {
        if (vm.status == VpnStatus.CONNECTED) {
            vm.disconnect()
        } else {
            val intent = android.net.VpnService.prepare(this)
            if (intent != null) permissionLauncher.launch(intent) else vm.connect()
        }
    }
}

@Composable
fun VpnScreen(vm: VpnViewModel, onToggle: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showTelegramDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activityPrefs = remember { context.getSharedPreferences("promo_dialog_prefs", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        if (!activityPrefs.getBoolean("telegram_dialog_seen", false)) showTelegramDialog = true
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("ON") }
                        withStyle(SpanStyle(color = Color.White)) { append(" Tunnel") }
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Settings, "الإعدادات", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(tab == 0, { tab = 0 }, text = { Text("الرئيسية") })
                Tab(tab == 1, { tab = 1 }, text = { Text("السجل") })
            }
            if (tab == 0) HomeTab(vm, onToggle) else LogScreen()
        }

        // Telegram invitation is a compact dialog; the old floating side panel is removed.
        if (vm.updateSuccess) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 18.dp),
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
    if (showSettings) SettingsDialog(vm, onDismiss = { showSettings = false })
    if (showTelegramDialog) {
        AlertDialog(
            onDismissRequest = {
                showTelegramDialog = false
                activityPrefs.edit().putBoolean("telegram_dialog_seen", true).apply()
            },
            title = { Text("RADOUANE TECH", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
            text = { Text("انضم إلى قناة Telegram لمتابعة الأخبار والتحديثات.") },
            confirmButton = {
                Button(onClick = {
                    activityPrefs.edit().putBoolean("telegram_dialog_seen", true).apply()
                    showTelegramDialog = false
                    runCatching {
                        context.startActivity(android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://t.me/radouane_tech")
                        ))
                    }
                }) { Icon(Icons.Default.Send, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("انضم إلى تلغرام") }
            },
            dismissButton = {
                TextButton(onClick = {
                    activityPrefs.edit().putBoolean("telegram_dialog_seen", true).apply()
                    showTelegramDialog = false
                }) { Text("لاحقاً") }
            }
        )
    }
}

@Composable
fun LogScreen() {
    val clipboard = LocalClipboardManager.current
    val state = rememberLazyListState()
    LaunchedEffect(AppLog.lines.size) {
        if (AppLog.lines.isNotEmpty()) state.scrollToItem(AppLog.lines.size - 1)
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(AppLog.lines.joinToString("\n")))
            }) { Text("نسخ") }
            OutlinedButton(onClick = { AppLog.clear() }) { Text("مسح") }
        }
        Spacer(Modifier.height(8.dp))
        if (AppLog.lines.isEmpty()) Text("لا توجد سجلات بعد", fontSize = 13.sp)
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(AppLog.lines) {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
fun HomeTab(vm: VpnViewModel, onToggle: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }

    val color = when (vm.status) {
        VpnStatus.CONNECTED -> Color(0xFF22C55E)
        VpnStatus.CONNECTING -> Color(0xFFFBBF24)
        VpnStatus.DISCONNECTED -> Color(0xFF475569)
    }
    val label = when (vm.status) {
        VpnStatus.CONNECTED -> "متصل"
        VpnStatus.CONNECTING -> "جارٍ الاتصال..."
        VpnStatus.DISCONNECTED -> "غير متصل"
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.size(176.dp).border(3.dp, color.copy(alpha = 0.35f), CircleShape).padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onToggle,
                enabled = vm.status != VpnStatus.CONNECTING,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = color,
                    disabledContainerColor = color
                ),
                modifier = Modifier.fillMaxSize()
            ) { Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(64.dp), tint = Color.White) }
        }

        Spacer(Modifier.height(12.dp))
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)

        if (vm.activeSsh) {
            Spacer(Modifier.height(8.dp))
            Text(
                "SSH يعمل كـ VPN كامل: كل حركة الهاتف تمر عبر السيرفر.",
                fontSize = 13.sp
            )
        }
        if (vm.activeV2ray) {
            Spacer(Modifier.height(8.dp))
            Text("V2Ray يعمل كـ VPN كامل: كل حركة الهاتف تمر عبر السيرفر.", fontSize = 13.sp)
        }
        vm.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("السيرفرات", fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.updateSubscription() }, enabled = !vm.subBusy) {
                Icon(Icons.Default.Refresh, "تحديث السيرفرات")
            }
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "إضافة") }
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            itemsIndexed(vm.servers) { i, server ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = if (vm.selected == i) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = vm.selected == i,
                            onClick = { if (vm.status == VpnStatus.DISCONNECTED) vm.selected = i }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(server.name)
                            val sub = when (server.type) {
                                "WG" -> "WireGuard"
                                "V2RAY" -> "V2Ray • ${server.config.substringBefore("://").uppercase()}"
                                else -> "SSH • ${server.host}" +
                                    (if (server.payload.isNotBlank()) " • Payload" else "") +
                                    (if (server.tls) " • TLS" else "")
                            }
                            Text(sub, fontSize = 12.sp)
                        }
                        IconButton(onClick = { vm.removeServer(i) }) {
                            Icon(Icons.Default.Delete, "حذف")
                        }
                    }
                }
            }
        }
    }

    if (showAdd) AddServerDialog(onDismiss = { showAdd = false }, onSave = { vm.addServer(it); showAdd = false })
}

@Composable
fun SettingsDialog(vm: VpnViewModel, onDismiss: () -> Unit) {
    var port by remember { mutableStateOf(vm.socksPort.toString()) }
    var ka by remember { mutableStateOf(vm.keepAlive.toString()) }
    var subUrl by remember { mutableStateOf(vm.subUrl) }
    var subAuto by remember { mutableStateOf(vm.subAuto) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الإعدادات") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    port, { port = it.filter(Char::isDigit) },
                    label = { Text("منفذ SOCKS5 المحلي (1024-65535)") }, singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    ka, { ka = it.filter(Char::isDigit) },
                    label = { Text("فاصل Keep-Alive بالثواني (5-120)") }, singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Text("اشتراك السيرفرات (رابط GitHub)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (AppConfig.LOCK_SUB && AppConfig.SUB_URL.isNotBlank()) {
                    Text("رابط الاشتراك مدمج في التطبيق.", fontSize = 12.sp)
                } else {
                    OutlinedTextField(
                        subUrl, { subUrl = it.trim() },
                        label = { Text("رابط الملف (raw) الذي يبدأ بـ https://") }, singleLine = true
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(subAuto, { subAuto = it })
                    Spacer(Modifier.width(8.dp))
                    Text("تحديث تلقائي عند فتح التطبيق", fontSize = 13.sp)
                }
                TextButton(
                    onClick = { vm.saveSubscription(subUrl, subAuto); vm.updateSubscription() },
                    enabled = !vm.subBusy
                ) { Text("حفظ وتحديث الآن") }
                    }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveSettings(port.toIntOrNull() ?: 1080, ka.toIntOrNull() ?: 15)
                vm.saveSubscription(subUrl, subAuto)
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun AddServerDialog(onDismiss: () -> Unit, onSave: (Server) -> Unit) {
    var type by remember { mutableStateOf("WG") }
    var name by remember { mutableStateOf("") }
    var cfg by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var cHost by remember { mutableStateOf("") }
    var cPort by remember { mutableStateOf("") }
    var tls by remember { mutableStateOf(false) }
    var sni by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة سيرفر") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == "WG", { type = "WG" }, label = { Text("WireGuard") })
                    FilterChip(type == "SSH", { type = "SSH" }, label = { Text("SSH") })
                    FilterChip(type == "V2RAY", { type = "V2RAY" }, label = { Text("V2Ray") })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                if (type == "WG") {
                    OutlinedTextField(
                        cfg, { cfg = it },
                        label = { Text("إعداد WireGuard (.conf)") },
                        modifier = Modifier.height(200.dp)
                    )
                } else if (type == "V2RAY") {
                    OutlinedTextField(
                        cfg, { cfg = it.trim() },
                        label = { Text("رابط VLESS / VMess / Trojan") },
                        placeholder = { Text("vless://… أو vmess://… أو trojan://…") },
                        modifier = Modifier.height(150.dp)
                    )
                    Text("يدعم روابط السيرفرات الفردية، ويعمل كـ VPN كامل لكل الهاتف.", fontSize = 12.sp)
                } else {
                    OutlinedTextField(host, { host = it }, label = { Text("عنوان سيرفر SSH") }, singleLine = true)
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("منفذ SSH") }, singleLine = true)
                    OutlinedTextField(user, { user = it }, label = { Text("اسم المستخدم") }, singleLine = true)
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("كلمة المرور") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(advanced, { advanced = it })
                        Spacer(Modifier.width(8.dp))
                        Text("WebSocket / Payload")
                    }
                    if (advanced) {
                        OutlinedTextField(cHost, { cHost = it }, label = { Text("عنوان الوسيط/CDN (اختياري)") }, singleLine = true)
                        OutlinedTextField(cPort, { cPort = it.filter(Char::isDigit) }, label = { Text("منفذ الوسيط (80 أو 443)") }, singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(tls, { tls = it })
                            Spacer(Modifier.width(8.dp))
                            Text("TLS")
                        }
                        if (tls) OutlinedTextField(sni, { sni = it }, label = { Text("SNI") }, singleLine = true)
                        OutlinedTextField(
                            payload, { payload = it },
                            label = { Text("Payload") },
                            modifier = Modifier.height(130.dp)
                        )
                        TextButton(onClick = { payload = WS_TEMPLATE }) { Text("قالب WebSocket") }
                        Text("المتغيرات: [host] [port] [crlf] [cr] [lf]", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Server(
                    name = name, type = type, config = cfg,
                    host = host.trim(), port = port.toIntOrNull() ?: 22,
                    user = user.trim(), password = pass,
                    connectHost = if (advanced) cHost.trim() else "",
                    connectPort = if (advanced) cPort.toIntOrNull() ?: 0 else 0,
                    tls = advanced && tls,
                    sni = if (advanced) sni.trim() else "",
                    payload = if (advanced) payload else ""
                ))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
