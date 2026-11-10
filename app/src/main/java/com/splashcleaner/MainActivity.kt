package com.splashcleaner

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.splashcleaner.data.AppConfigEntity
import com.splashcleaner.data.LocalDatabase
import com.splashcleaner.domain.AccessState
import com.splashcleaner.domain.InspectionCatalog
import com.splashcleaner.domain.InspectionScore
import com.splashcleaner.domain.CheckStatus
import com.splashcleaner.domain.Severity
import com.splashcleaner.service.AdAccessibilityService
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

private val Paper = Color(0xFFEDEEE9)
private val Panel = Color(0xFFF6F7F3)
private val Ink = Color(0xFF171C21)
private val Muted = Color(0xFF59636D)
private val Accent = Color(0xFF0F6A5F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Paper, surface = Panel)) { SplashCleanerApp() } }
    }
}

@Composable
private fun SplashCleanerApp() {
    val context = LocalContext.current
    val db = remember { LocalDatabase.get(context) }
    var page by remember { mutableStateOf(0) }
    var accessState by remember { mutableStateOf(readAccessState(context)) }
    var apps by remember { mutableStateOf(emptyList<ApplicationInfo>()) }
    var configs by remember { mutableStateOf(emptyMap<String, AppConfigEntity>()) }
    var selectedCheck by remember { mutableStateOf(InspectionCatalog.checks.first().id) }
    var statuses by remember { mutableStateOf(InspectionCatalog.checks.associate { it.id to CheckStatus.NOT_VERIFIED }) }

    LaunchedEffect(Unit) {
        apps = context.packageManager.getInstalledApplications(0)
            .filter { it.packageName != context.packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM == 0) }
            .sortedBy { context.packageManager.getApplicationLabel(it).toString().lowercase() }
        db.appConfigDao().observeAll().collect { list -> configs = list.associateBy { it.packageName } }
    }
    LaunchedEffect(page) { accessState = readAccessState(context) }

    Scaffold(containerColor = Paper, bottomBar = {
        NavigationBar(containerColor = Panel) {
            listOf("首页", "应用", "体检", "设置").forEachIndexed { index, label ->
                NavigationBarItem(selected = page == index, onClick = { page = index }, icon = {
                    Icon(when (index) { 0 -> Icons.Default.Home; 1 -> Icons.Default.Apps; 2 -> Icons.Default.FactCheck; else -> Icons.Default.Settings }, contentDescription = label)
                }, label = { Text(label) })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                0 -> HomeScreen(accessState, onEnable = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, onApps = { page = 1 }, onInspect = { page = 2 })
                1 -> AppsScreen(apps, configs, onToggle = { app, enabled ->
                    val pkg = app.packageName
                    context.getSharedPreferences("app_cfg", android.content.Context.MODE_PRIVATE).edit().putBoolean(pkg, enabled).apply()
                    lifecycleScopeLaunch(context) { db.appConfigDao().upsert(AppConfigEntity(pkg, enabled, System.currentTimeMillis(), if (enabled) System.currentTimeMillis() else configs[pkg]?.userApprovedAt)) }
                })
                2 -> InspectionScreen(selectedCheck, { selectedCheck = it }, statuses, { id, status -> statuses = statuses + (id to status) })
                else -> SettingsScreen(accessState, onEnable = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            }
        }
    }
}

@Composable private fun HomeScreen(state: AccessState, onEnable: () -> Unit, onApps: () -> Unit, onInspect: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("开屏净化助手", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink); Text("本地广告识别与误触防护", color = Muted) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Shield, null, tint = Accent); Spacer(Modifier.width(8.dp)); Text("保护服务状态", fontWeight = FontWeight.SemiBold) }
                    Text(state.label(), style = MaterialTheme.typography.titleLarge, color = if (state == AccessState.AVAILABLE) Accent else Ink)
                    Text(state.description(), color = Muted)
                    if (state != AccessState.AVAILABLE) Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) { Text("前往开启无障碍服务") }
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("自动动作", "未启用", Modifier.weight(1f))
            SummaryTile("规则包", "内置空规则", Modifier.weight(1f))
        } }
        item { ActionCard("逐应用授权", "新应用默认关闭；只处理你明确允许的应用。", Icons.Default.Apps, onApps) }
        item { ActionCard("广告体检", "23 项检测；无法自动验证的项目明确标记为未验证。", Icons.Default.FactCheck, onInspect) }
        item { Text("安全说明", fontWeight = FontWeight.SemiBold, color = Ink) }
        item { Text("当前版本为开发基座：尚未启用自动点击，也未内置第三方 App 规则。所有屏幕文字仅短暂存在于内存，不写入数据库或日志。", color = Muted) }
    }
}

@Composable private fun SummaryTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(14.dp)) { Text(title, color = Muted); Text(value, fontWeight = FontWeight.Bold, color = Ink) } }
}

@Composable private fun ActionCard(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, color = Ink); Text(desc, color = Muted, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Default.ChevronRight, null, tint = Muted) }
    }
}

@Composable private fun AppsScreen(apps: List<ApplicationInfo>, configs: Map<String, AppConfigEntity>, onToggle: (ApplicationInfo, Boolean) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ApplicationInfo?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("应用管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("搜索应用") }, singleLine = true)
        Text("默认关闭。打开开关即表示你允许在该应用内进行广告识别。", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(apps.filter { context.packageManager.getApplicationLabel(it).toString().contains(query, true) || it.packageName.contains(query, true) }, key = { it.packageName }) { app ->
                val enabled = configs[app.packageName]?.enabled == true
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(context.packageManager.getApplicationLabel(app).toString(), color = Ink, fontWeight = FontWeight.Medium); Text(app.packageName, color = Muted, style = MaterialTheme.typography.labelSmall) }
                    Switch(checked = enabled, onCheckedChange = { if (it) pending = app else onToggle(app, false) })
                }
            }
        }
    }
    pending?.let { app ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("允许处理此应用？") },
            text = { Text("开屏净化助手将仅在你授权的应用内读取可访问性窗口节点，以识别广告关闭控件。屏幕文本不会上传或保存。") },
            confirmButton = { TextButton(onClick = { onToggle(app, true); pending = null }) { Text("允许") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } })
    }
}

@Composable private fun InspectionScreen(selected: String, onSelect: (String) -> Unit, statuses: Map<String, CheckStatus>, onStatus: (String, CheckStatus) -> Unit) {
    val checks = InspectionCatalog.checks.map { it.copy(status = statuses[it.id] ?: CheckStatus.NOT_VERIFIED) }
    val score = InspectionScore.calculate(checks, checks.count { it.status != CheckStatus.NOT_VERIFIED }.toDouble() / 23.0)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("广告体检", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink) }
        item { Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(16.dp)) { Text("当前手动记录评分（演示数据）", color = Muted); Text("${score.score} 分 · ${score.grade}", style = MaterialTheme.typography.headlineMedium, color = Accent, fontWeight = FontWeight.Bold); Text("有效覆盖 ${checks.count { it.status != CheckStatus.NOT_VERIFIED }}/23；未验证项不计分。", color = Muted) } } }
        items(checks, key = { it.id }) { check ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().clickable { onSelect(check.id) }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(check.id, color = Accent, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Text(check.title, color = Ink, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text(statusLabel(check.status), color = Muted, style = MaterialTheme.typography.labelSmall) }
                    Text(check.criterion, color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (selected == check.id) {
                        Text("检测方式：${check.method} · 证据：${check.evidence}", color = Ink, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { onStatus(check.id, CheckStatus.PASS) }) { Text("记录通过") }
                            TextButton(onClick = { onStatus(check.id, CheckStatus.ISSUE) }) { Text("记录问题") }
                            TextButton(onClick = { onStatus(check.id, CheckStatus.NOT_VERIFIED) }) { Text("未验证") }
                        }
                    }
                }
            }
        }
        item { Text("注意：本版本只提供检测项记录界面，未自动执行 OCR、像素对比或点击验证。D-03、D-04、D-05 等人工项目必须实测，不能默认达标。", color = Muted) }
    }
}

@Composable private fun SettingsScreen(state: AccessState, onEnable: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink) }
        item { ActionCard("无障碍服务", state.label(), Icons.Default.Accessibility, onEnable) }
        item { SettingRow("规则通道", "stable（尚未连接规则 CDN）") }
        item { SettingRow("规则包版本", "内置开发基线 · schema 3") }
        item { SettingRow("遥测", "未实现；当前版本不发送数据") }
        item { SettingRow("隐私", "屏幕文本不写入日志、数据库或网络请求") }
        item { SettingRow("版本", "0.1.0 · Android 9–16") }
        item { Text("Shizuku、Ed25519 订阅验签、自动回滚、PDF 报告导出及遥测上传尚未接入。", color = Muted) }
    }
}

@Composable private fun SettingRow(title: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(15.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = Ink); Text(value, color = Muted, style = MaterialTheme.typography.bodySmall) } }
}

private fun AccessState.label() = when (this) { AccessState.NOT_ENABLED -> "尚未授权"; AccessState.RESTRICTED -> "可能受到系统限制"; AccessState.AVAILABLE -> "无障碍服务已开启"; AccessState.RECYCLED -> "服务已断开" }
private fun AccessState.description() = when (this) { AccessState.NOT_ENABLED -> "开启后才能读取已授权应用的窗口节点。你可以稍后再授权。"; AccessState.RESTRICTED -> "请在系统设置中允许此应用使用受限制的无障碍设置。"; AccessState.AVAILABLE -> "服务已连接。当前开发版本仅采集基线，不执行自动点击。"; AccessState.RECYCLED -> "系统可能回收了服务，请检查无障碍设置。" }
private fun statusLabel(s: CheckStatus) = when (s) { CheckStatus.PASS -> "已记录通过"; CheckStatus.ISSUE -> "发现问题"; CheckStatus.NOT_VERIFIED -> "未验证" }
private fun readAccessState(context: android.content.Context): AccessState {
    val expected = ComponentName(context, AdAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val isEnabled = enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    return when { !isEnabled -> AccessState.NOT_ENABLED; AdAccessibilityService.connected -> AccessState.AVAILABLE; else -> AccessState.RECYCLED }
}
private fun lifecycleScopeLaunch(context: android.content.Context, block: suspend () -> Unit) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { block() }
}
