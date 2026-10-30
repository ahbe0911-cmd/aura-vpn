package com.auravpn

import android.os.Bundle
import android.app.Activity
import android.net.VpnService
import com.auravpn.vpn.XrayConfigBuilder
import com.auravpn.vpn.EngineConfigResult
import com.auravpn.vpn.EngineConnectionGate
import com.auravpn.vpn.EngineStartResult
import com.auravpn.vpn.NativeXrayRuntime
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auravpn.ui.theme.AuraColors
import com.auravpn.ui.theme.AuraTheme
import com.auravpn.ui.ProfileImportDialog
import com.auravpn.ui.SubscriptionDialog
import com.auravpn.profile.SubscriptionRepository
import com.auravpn.profile.SavedSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.auravpn.profile.ProfileStore
import com.auravpn.profile.StoredProfile
import androidx.compose.ui.platform.LocalContext

private enum class AuraTab(val title: String, val icon: ImageVector) {
    HOME("خانه", Icons.Filled.Home),
    SERVERS("سرورها", Icons.Filled.Dns),
    ROUTING("مسیریابی", Icons.Filled.Route),
    SETTINGS("تنظیمات", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraTheme { AuraRoot() } }
    }
}

@Composable
private fun AuraRoot() {
    var selectedTab by remember { mutableStateOf(AuraTab.HOME) }
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val subscriptions = remember { SubscriptionRepository(context) }
    var sources by remember { mutableStateOf(subscriptions.list()) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(store.list()) }
    var selectedProfileId by remember { mutableStateOf(store.selectedId()) }
    var showImport by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var vpnPermissionGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vpnPermissionGranted = result.resultCode == Activity.RESULT_OK && VpnService.prepare(context) == null
        notice = if (vpnPermissionGranted) "مجوز VPN تأیید شد؛ موتور اتصال هنوز نصب نشده است." else "مجوز VPN تأیید نشد."
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            notice = null
        }
    }

    if (showSubscriptionDialog) {
        SubscriptionDialog(onDismiss = { showSubscriptionDialog = false }, onSave = { url, name ->
            val added = subscriptions.add(url, name)
            sources = subscriptions.list()
            showSubscriptionDialog = false
            notice = if (added) "اشتراک ذخیره شد" else "آدرس نامعتبر یا تکراری است"
        })
    }

    if (showImport) {
        ProfileImportDialog(onDismiss = { showImport = false }, onImport = { input ->
            val result = store.import(input)
            profiles = store.list()
            showImport = false
            notice = result.error ?: "افزوده شد: ${result.added} | تکراری: ${result.duplicates}"
        })
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = AuraColors.Background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AuraBottomBar(selected = selectedTab, onSelect = { selectedTab = it })
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(AuraColors.Background, AuraColors.BackgroundEnd)))
                    .padding(padding)
            ) {
                when (selectedTab) {
                    AuraTab.HOME -> HomeScreen(selectedProfileName = store.selected()?.name, onConnect = {
                        if (store.selected() == null) {
                            notice = "ابتدا از بخش سرورها یک پروفایل انتخاب کنید."
                            selectedTab = AuraTab.SERVERS
                        } else {
                            val profile = store.selected()
                            val validation = XrayConfigBuilder.fromShareLink(profile!!.source)
                            if (validation is EngineConfigResult.Unsupported) {
                                notice = "کانفیگ برای هسته فعلی آماده نیست: ${validation.reason}"
                            } else if (EngineConnectionGate.canStart(validation, NativeXrayRuntime(context).availability()) is EngineStartResult.Failed) {
                                notice = "هسته اجرایی Xray هنوز در APK موجود نیست؛ اتصال برقرار نشد."
                            } else {
                                val permissionIntent = VpnService.prepare(context)
                                if (permissionIntent != null) vpnPermissionLauncher.launch(permissionIntent)
                                else {
                                    vpnPermissionGranted = true
                                    notice = "کانفیگ اولیه معتبر و مجوز آماده است؛ موتور Xray هنوز متصل نشده است."
                                }
                            }
                        }
                    }, onServers = { selectedTab = AuraTab.SERVERS })
                    AuraTab.SERVERS -> ServersScreen(profiles = profiles, selectedId = selectedProfileId, onSelect = { id -> if (store.select(id)) { selectedProfileId = id; notice = "سرور انتخاب شد؛ اتصال VPN هنوز فعال نیست." } }, onImport = { showImport = true }, onDelete = { id -> store.delete(id); profiles = store.list(); selectedProfileId = store.selectedId() }, sources = sources, refreshing = refreshing, onAddSource = { showSubscriptionDialog = true }, onRemoveSource = { url -> subscriptions.remove(url); sources = subscriptions.list() }, onRefreshSource = { url -> if (!refreshing) { refreshing = true; scope.launch { try { val fetched = withContext(Dispatchers.IO) { subscriptions.fetchResult(url) }; val result = withContext(Dispatchers.IO) { store.import(fetched.content, fetched.headers) }; if (result.error != null) notice = result.error else { subscriptions.markUpdated(url); sources = subscriptions.list(); profiles = store.list(); notice = "افزوده شد: ${result.added} | تکراری: ${result.duplicates}" } } catch (e: Exception) { notice = "خطا در دریافت اشتراک: ${e.message}" } finally { refreshing = false } } } })
                    AuraTab.ROUTING -> RoutingScreen(onNotice = { notice = it })
                    AuraTab.SETTINGS -> SettingsScreen(onNotice = { notice = it })
                }
            }
        }
    }
}

@Composable
private fun AuraHeader(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AuraColors.TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = AuraColors.TextSecondary, fontSize = 12.sp)
        }
        action?.invoke()
    }
}

@Composable
private fun HomeScreen(selectedProfileName: String?, onConnect: () -> Unit, onServers: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            AuraHeader(
                title = "AURA VPN",
                subtitle = "اتصال امن، ساده و هوشمند",
                action = {
                    Surface(color = AuraColors.Surface, shape = CircleShape, border = BorderStroke(1.dp, Color(0xFF293957))) {
                        IconButton(onClick = { }) {
                            Icon(Icons.Filled.Shield, contentDescription = "نشان آورا", tint = AuraColors.Primary)
                        }
                    }
                }
            )
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(224.dp)) {
                    Box(
                        Modifier.size(218.dp).shadow(24.dp, CircleShape)
                            .background(Color(0x2238C8FF), CircleShape)
                    )
                    Box(
                        Modifier.size(190.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFF1D3B62), Color(0xFF101A32))), CircleShape)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(AuraColors.Background, CircleShape)
                                .then(Modifier.padding(1.dp).background(Color(0xFF142541), CircleShape)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.PowerSettingsNew, contentDescription = "اتصال", tint = AuraColors.Primary, modifier = Modifier.size(52.dp))
                                Text("اتصال", color = AuraColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            }
                        }
                    }
                }
                Text("آماده اتصال", color = AuraColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                Text("هنوز تونلی فعال نیست", color = AuraColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Button(
                    onClick = onConnect,
                    modifier = Modifier.padding(top = 14.dp).height(48.dp).fillMaxWidth(0.72f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Primary, contentColor = AuraColors.Background)
                ) {
                    Text("شروع اتصال", fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            ServerCard(profileName = selectedProfileName, onClick = onServers)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("دانلود", "۰ KB/s", Icons.Filled.Download, Modifier.weight(1f))
                MetricCard("آپلود", "۰ KB/s", Icons.Filled.Upload, Modifier.weight(1f))
            }
        }
        item {
            Surface(
                color = Color(0xFF17243D),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF273957))
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AuraColors.Primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("مسیریابی هوشمند ایران", color = AuraColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("تنظیمات این قابلیت در بخش مسیریابی قرار دارد", color = AuraColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(profileName: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = AuraColors.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF293957))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFF213A5A), shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = AuraColors.Primary, modifier = Modifier.padding(11.dp).size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(profileName ?: "سرور انتخاب نشده", color = AuraColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(if (profileName == null) "برای افزودن سرور وارد فهرست سرورها شوید" else "پروفایل انتخاب شده؛ موتور اتصال هنوز آماده نیست", color = AuraColors.TextSecondary, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronLeft, contentDescription = "مشاهده سرورها", tint = AuraColors.TextSecondary)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, color = AuraColors.Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF293957))) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = AuraColors.Primary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(title, color = AuraColors.TextSecondary, fontSize = 12.sp)
            }
            Text(value, color = AuraColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ServersScreen(profiles: List<StoredProfile>, selectedId: String?, onSelect: (String) -> Unit, onImport: () -> Unit, onDelete: (String) -> Unit, sources: List<SavedSubscription>, refreshing: Boolean, onAddSource: () -> Unit, onRemoveSource: (String) -> Unit, onRefreshSource: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AuraHeader("سرورها", "مدیریت پروفایل‌ها و اشتراک‌ها")
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("جست‌وجوی سرورها", color = AuraColors.TextSecondary) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AuraColors.TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFF30415F),
                focusedBorderColor = AuraColors.Primary,
                unfocusedContainerColor = AuraColors.Surface
            )
        )
        Button(onClick = onAddSource) { Text("افزودن لینک اشتراک") }
        sources.forEach { source ->
            Surface(color = AuraColors.Surface, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, color = AuraColors.TextPrimary)
                        Text(if (source.lastUpdated > 0) "آخرین دریافت: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(source.lastUpdated)) else "هنوز دریافت نشده", color = AuraColors.TextSecondary, fontSize = 11.sp)
                    }
                    TextButton(onClick = { onRefreshSource(source.url) }, enabled = !refreshing) { Text("به‌روزرسانی") }
                    IconButton(onClick = { onRemoveSource(source.url) }) { Icon(Icons.Filled.Delete, contentDescription = "حذف اشتراک", tint = AuraColors.Error) }
                }
            }
        }
        if (profiles.isNotEmpty()) {
            profiles.forEach { profile ->
                Surface(color = AuraColors.Surface, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, color = AuraColors.TextPrimary)
                            Text(profile.protocol.uppercase(), color = AuraColors.TextSecondary, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onSelect(profile.id) }) {
                            Text(if (selectedId == profile.id) "انتخاب‌شده ✓" else "انتخاب")
                        }
                        IconButton(onClick = { onDelete(profile.id) }) { Icon(Icons.Filled.Delete, contentDescription = "حذف پروفایل", tint = AuraColors.Error) }
                    }
                }
            }
            Button(onClick = onImport) { Text("افزودن پروفایل") }
        } else Surface(color = AuraColors.Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFF293957))) {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Dns, contentDescription = null, tint = AuraColors.Primary, modifier = Modifier.size(42.dp))
                Text("هنوز سروری اضافه نشده", color = AuraColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("لینک اتصال یا اشتراک خود را وارد کنید تا اینجا نمایش داده شود.", color = AuraColors.TextSecondary, textAlign = TextAlign.Center, fontSize = 12.sp)
                Button(onClick = onImport, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Primary, contentColor = AuraColors.Background)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("افزودن کانفیگ")
                }
            }
        }
        Text("انتخاب خودکار سریع‌ترین سرور پس از اضافه‌شدن پروفایل‌ها فعال خواهد شد.", color = AuraColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun RoutingScreen(onNotice: (String) -> Unit) {
    var mode by remember { mutableStateOf("iran") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AuraHeader("مسیریابی هوشمند", "انتخاب مسیر ترافیک اینترنت")
        Text("حالت مسیریابی", color = AuraColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        RoutingOption("iran", "عبور هوشمند ایران", "سرویس‌های ایرانی مستقیم، سایر ترافیک از تونل", Icons.Filled.Tune, mode) { mode = it }
        RoutingOption("global", "همه از تونل", "ارسال تمام ترافیک از مسیر پروکسی", Icons.Filled.Public, mode) { mode = it }
        RoutingOption("custom", "قوانین سفارشی", "مدیریت دامنه، IP و اپلیکیشن‌ها", Icons.Filled.Rule, mode) { mode = it }
        Surface(color = Color(0xFF17243D), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF293957))) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = AuraColors.Primary)
                Spacer(Modifier.width(10.dp))
                Text("این انتخاب فعلاً نمایشی است؛ اعمال واقعی قوانین پس از اتصال موتور VPN در فازهای بعد انجام می‌شود.", color = AuraColors.TextSecondary, fontSize = 12.sp)
            }
        }
        OutlinedButton(onClick = { onNotice("مدیریت اپلیکیشن‌ها در فاز پیاده‌سازی مسیریابی اضافه می‌شود.") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Filled.Apps, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("انتخاب اپلیکیشن‌ها")
        }
    }
}

@Composable
private fun RoutingOption(key: String, title: String, subtitle: String, icon: ImageVector, selected: String, onSelect: (String) -> Unit) {
    val borderColor by animateColorAsState(if (selected == key) AuraColors.Primary else Color(0xFF293957), label = "routingBorder")
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(key) },
        color = if (selected == key) Color(0xFF172F4B) else AuraColors.Surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected == key) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AuraColors.Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = AuraColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AuraColors.TextSecondary, fontSize = 11.sp)
            }
            RadioButton(selected == key, onClick = { onSelect(key) }, colors = RadioButtonDefaults.colors(selectedColor = AuraColors.Primary))
        }
    }
}

@Composable
private fun SettingsScreen(onNotice: (String) -> Unit) {
    var persianDigits by remember { mutableStateOf(true) }
    var darkTheme by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AuraHeader("تنظیمات", "شخصی‌سازی تجربه آورا")
        SettingSwitch("تم تاریک", "ظاهر تیره و کم‌نور", darkTheme, { darkTheme = it })
        SettingSwitch("اعداد فارسی", "نمایش اعداد فارسی در زبان فارسی", persianDigits, { persianDigits = it })
        SettingRow(Icons.Filled.Language, "زبان برنامه", "فارسی") { onNotice("انتخاب زبان انگلیسی در به‌روزرسانی رابط اضافه می‌شود.") }
        SettingRow(Icons.Filled.Security, "امنیت و حریم خصوصی", "گزارش‌ها و مجوزها") { onNotice("صفحه امنیت در فاز سخت‌سازی تکمیل می‌شود.") }
        SettingRow(Icons.Filled.Description, "گزارش اتصال", "هنوز گزارشی ثبت نشده") { onNotice("هنوز هسته VPN برای تولید گزارش اتصال نصب نشده است.") }
        Spacer(Modifier.weight(1f))
        Text("AURA VPN  •  نسخه ۰.۱.۰", color = AuraColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(color = AuraColors.Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF293957))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = AuraColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AuraColors.TextSecondary, fontSize = 11.sp)
            }
            Switch(checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = AuraColors.Background, checkedTrackColor = AuraColors.Primary))
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = AuraColors.Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF293957))) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AuraColors.Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = AuraColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AuraColors.TextSecondary, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = AuraColors.TextSecondary)
        }
    }
}

@Composable
private fun AuraBottomBar(selected: AuraTab, onSelect: (AuraTab) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0E172B), contentColor = AuraColors.TextPrimary, tonalElevation = 0.dp) {
        AuraTab.values().forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.title) },
                label = { Text(tab.title, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AuraColors.Primary,
                    selectedTextColor = AuraColors.Primary,
                    indicatorColor = Color(0xFF1B3552),
                    unselectedIconColor = AuraColors.TextSecondary,
                    unselectedTextColor = AuraColors.TextSecondary
                )
            )
        }
    }
}
