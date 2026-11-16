package com.lvngx.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import rikka.shizuku.Shizuku

private val Bg = Color(0xFF080A11)
private val Surface = Color(0xFF141A29)
private val Surface2 = Color(0xFF1B2335)
private val Red = Color(0xFFF20D50)
private val TextMain = Color(0xFFF4F5F8)
private val Muted = Color(0xFF9AA4B8)

data class GameApp(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LVNGApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

@Composable
private fun LVNGApp() {
    val context = LocalContext.current
    var page by remember { mutableStateOf((context as? android.app.Activity)?.intent?.getStringExtra("lvngx_page") ?: "home") }
    var selectedGames by remember { mutableStateOf(loadGames(context)) }
    var allApps by remember { mutableStateOf(emptyList<GameApp>()) }
    var showPicker by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var shizukuBinder by remember { mutableStateOf(false) }
    var shizukuPermission by remember { mutableStateOf(false) }
    var shizukuInstalled by remember { mutableStateOf(isPackageInstalled(context, "moe.shizuku.privileged.api")) }

    fun refreshShizuku() {
        shizukuBinder = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        shizukuPermission = shizukuBinder && try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
        shizukuInstalled = isPackageInstalled(context, "moe.shizuku.privileged.api")
    }

    DisposableEffect(Unit) {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 7102) refreshShizuku()
        }
        val received = rikka.shizuku.Shizuku.OnBinderReceivedListener { refreshShizuku() }
        val dead = rikka.shizuku.Shizuku.OnBinderDeadListener { refreshShizuku() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshShizuku()
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }

    fun refreshOverlay() { overlayGranted = Settings.canDrawOverlays(context) }

    DisposableEffect(context) {
        val owner = context as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshOverlay()
                refreshShizuku()
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) runCatching {
            val bytes = createBackup(context).toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("File backup tidak dapat dibaca")
            restoreBackup(context, raw)
            selectedGames = loadGames(context)
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val pkg = result.data?.getStringExtra("package")
            if (pkg != null) {
                val app = allApps.firstOrNull { it.packageName == pkg }
                if (app != null && selectedGames.none { it.packageName == pkg }) {
                    selectedGames = selectedGames + app
                    saveGames(context, selectedGames)
                }
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Red, background = Bg, surface = Surface,
        onPrimary = Color.White, onBackground = TextMain, onSurface = TextMain
    )) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101522), Bg, Color(0xFF07090F))))) {
            Column(Modifier.fillMaxSize()) {
                when (page) {
                    "home" -> HomePage(
                        games = selectedGames,
                        onAdd = {
                            allApps = installedApps(context)
                            showPicker = true
                        },
                        onLaunch = { game -> launchGame(context, game.packageName) },
                        onSpace = {
                            if (overlayGranted) {
                                startOverlayService(context)
                            } else {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                            }
                        },
                        onDiagnostics = { page = "diagnostics" }
                    )
                    "library" -> LibraryPage(selectedGames, onAdd = {
                        allApps = installedApps(context); showPicker = true
                    }, onLaunch = { launchGame(context, it.packageName) }, onRemove = {
                        selectedGames = selectedGames.filterNot { g -> g.packageName == it.packageName }
                        saveGames(context, selectedGames)
                    })
                    "diagnostics" -> DiagnosticsPage(
                        overlayGranted = overlayGranted,
                        shizukuInstalled = shizukuInstalled,
                        shizukuBinder = shizukuBinder,
                        shizukuPermission = shizukuPermission,
                        onPermission = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        },
                        onShizuku = {
                            if (!shizukuInstalled) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                                } catch (_: Exception) {}
                            } else if (!shizukuBinder) {
                                try { context.startActivity(context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")) } catch (_: Exception) {}
                            } else if (!shizukuPermission) {
                                try { Shizuku.requestPermission(7102) } catch (_: Throwable) { refreshShizuku() }
                            } else {
                                refreshShizuku()
                            }
                        }
                    )
                    else -> ModulePage(
                        module = page,
                        onBack = { page = "home" },
                        onDiagnostics = { page = "diagnostics" },
                        onExportBackup = { backupExport.launch("LVNG-X-backup.lvngx") },
                        onImportBackup = { backupImport.launch(arrayOf("application/octet-stream", "application/json", "*/*")) },
                        onOpenSystemSettings = {
                            val action = when (page) {
                                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                                "performance" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                                "input", "macro" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                                "settings" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                else -> Settings.ACTION_SETTINGS
                            }
                            try {
                                val intent = if (page == "settings") Intent(action, Uri.parse("package:${context.packageName}")) else Intent(action)
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                BottomBar(page, onPage = { page = it })
            }
            if (showPicker) {
                GamePickerDialog(
                    apps = allApps,
                    selected = selectedGames.map { it.packageName }.toSet(),
                    onDismiss = { showPicker = false },
                    onSelect = { app ->
                        if (selectedGames.none { it.packageName == app.packageName }) {
                            selectedGames = selectedGames + app
                            saveGames(context, selectedGames)
                        }
                        showPicker = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HomePage(
    games: List<GameApp>, onAdd: () -> Unit, onLaunch: (GameApp) -> Unit,
    onSpace: () -> Unit, onDiagnostics: () -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LVNG -X", color = TextMain, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
                Text("GAME SPACE", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
            }
            Image(androidx.compose.ui.res.painterResource(R.drawable.lvngx_logo), null, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF291522), Color(0xFF171B2B), Surface)))
                .clickable { onSpace() }.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(androidx.compose.ui.res.painterResource(R.drawable.lvngx_logo), null,
                Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Surface2))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Masuk ke", color = Muted, fontSize = 12.sp)
                Text("Game Space", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Text("›", color = TextMain, fontSize = 34.sp, modifier = Modifier.padding(end = 4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Perpustakaan Game", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${games.size}", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            Text("Tambah  +", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(Surface2).clickable { onAdd() }.padding(horizontal = 15.dp, vertical = 9.dp))
        }
        if (games.isEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surface).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Belum ada game", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Tambahkan game dari aplikasi yang terpasang.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(14.dp))
                Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Tambah Game") }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(games, key = { it.packageName }) { game ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surface).clickable { onLaunch(game) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(game, Modifier.size(54.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                            Text(game.label, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Game tersimpan", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Button(onClick = { onLaunch(game) }, colors = ButtonDefaults.buttonColors(containerColor = Red), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                            Text("Main", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Pengaturan akses  ›", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onDiagnostics() }.padding(vertical = 10.dp))
    }
}

@Composable
private fun LibraryPage(
    games: List<GameApp>, onAdd: () -> Unit, onLaunch: (GameApp) -> Unit, onRemove: (GameApp) -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Text("Game Library", color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 22.dp, bottom = 18.dp))
        Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Tambah Game") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            items(games, key = { it.packageName }) { game ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(game, Modifier.size(44.dp))
                    Text(game.label, color = TextMain, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Main", color = Red, modifier = Modifier.clickable { onLaunch(game) }.padding(8.dp))
                    Text("Hapus", color = Muted, modifier = Modifier.clickable { onRemove(game) }.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsPage(
    overlayGranted: Boolean,
    shizukuInstalled: Boolean,
    shizukuBinder: Boolean,
    shizukuPermission: Boolean,
    onPermission: () -> Unit,
    onShizuku: () -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Text("Pengaturan", color = TextMain, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 22.dp, bottom = 5.dp))
        Text("Akses dan kompatibilitas perangkat", color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 20.dp))
        val activityManager = LocalContext.current.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memory = android.app.ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val totalRamGb = memory.totalMem / (1024.0 * 1024.0 * 1024.0)
        val freeRamGb = memory.availMem / (1024.0 * 1024.0 * 1024.0)
        Text("DEVICE", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        DiagnosticRow("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", true)
        DiagnosticRow("RAM tersedia", String.format(java.util.Locale.US, "%.1f / %.1f GB", freeRamGb, totalRamGb), freeRamGb > 0.5)
        DiagnosticRow("Perangkat", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", true)
        Spacer(Modifier.height(16.dp))
        Text("ACCESS CENTER", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(10.dp))
        val shizukuStatus = when {
            !shizukuInstalled -> "Shizuku belum terpasang"
            !shizukuBinder -> "Server tidak berjalan"
            !shizukuPermission -> "Izin LVNG -X belum diberikan"
            else -> "Terhubung dan diizinkan"
        }
        SettingCard("Shizuku Access", shizukuStatus, shizukuBinder && shizukuPermission, onShizuku)
        Spacer(Modifier.height(10.dp))
        SettingCard("Floating Window", if (overlayGranted) "Izin diberikan" else "Izin diperlukan",
            overlayGranted, onPermission)
        Spacer(Modifier.height(22.dp))
        Text("SYSTEM STATUS", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(10.dp))
        DiagnosticRow("Shizuku Binder", if (shizukuBinder) "Aktif" else "Tidak aktif", shizukuBinder)
        DiagnosticRow("Shizuku Permission", if (shizukuPermission) "Granted" else "Belum granted", shizukuPermission)
        DiagnosticRow("Overlay Permission", if (overlayGranted) "Granted" else "Belum granted", overlayGranted)
        val enabledA11y = android.provider.Settings.Secure.getString(LocalContext.current.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val macroEnabled = enabledA11y.split(':').any { it.equals("${LocalContext.current.packageName}/${MacroAccessibilityService::class.java.name}", true) }
        DiagnosticRow("Macro Accessibility", if (macroEnabled) "Enabled" else "Permission required", macroEnabled)
        DiagnosticRow("Macro Dispatcher", if (MacroAccessibilityService.instance != null) "Connected" else "Inactive", MacroAccessibilityService.instance != null)
        Spacer(Modifier.height(18.dp))
        Text("Fitur yang memerlukan akses khusus hanya akan aktif setelah izin dan kompatibilitasnya terverifikasi.",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun ModulePage(
    module: String,
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    if (module == "macro") {
        MacroDashboard(onBack = onBack, onDiagnostics = onDiagnostics)
        return
    }
    if (module == "input") {
        TouchTestDashboard(onBack = onBack, onDiagnostics = onDiagnostics)
        return
    }
    val title = when (module) {
        "macro" -> "Macro Engine"
        "display" -> "Display & Visual"
        "input" -> "Input & Control"
        "performance" -> "Performance Monitor"
        "tools" -> "Game Tools"
        "profiles" -> "Game Profiles"
        "backup" -> "Backup & Restore"
        "settings" -> "Settings"
        else -> "Game Space"
    }
    val description = when (module) {
        "macro" -> "Kontrol sentuh otomatis membutuhkan Accessibility Service dan pengujian kompatibilitas game. Engine macro belum diaktifkan pada build ini."
        "display" -> "Pengaturan layar Android dapat dibuka di sini. Overlay visual lintas aplikasi membutuhkan izin dan pengujian tersendiri."
        "input" -> "Dukungan input lanjutan bergantung pada layanan aksesibilitas dan kompatibilitas perangkat."
        "performance" -> "Android membatasi kontrol CPU dan RAM aplikasi lain. Monitor harus menampilkan metrik perangkat yang benar, bukan klaim boost palsu."
        "tools" -> "Pilih alat yang tersedia setelah implementasi dan izin terkait selesai diverifikasi."
        "profiles" -> "Library game tersimpan di perangkat. Pilih game dari tab Library untuk menjalankannya."
        "backup" -> "Backup profil akan menyertakan konfigurasi LVNG -X, bukan izin Android atau sesi Shizuku."
        "settings" -> "Kelola izin Android dan status integrasi LVNG -X."
        else -> "Kelola game yang ingin ditampilkan di Game Space."
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Red, fontSize = 32.sp, modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
            Column {
                Text(title, color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("LVNG -X MODULE", color = Red, fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(description, color = Muted, fontSize = 13.sp, lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(16.dp))
        Spacer(Modifier.height(16.dp))
        if (module in setOf("macro", "input", "display", "performance", "settings")) {
            Button(onClick = onOpenSystemSettings, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                Text(if (module == "settings") "Buka Info Aplikasi" else "Buka Pengaturan Android")
            }
        }
        if (module == "backup") {
            Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                Text("Ekspor Backup .lvngx")
            }
            OutlinedButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) { Text("Pulihkan dari Backup") }
            Text("Backup ini menyimpan daftar game dan preferensi lokal LVNG -X. Izin Android, data game, dan sesi Shizuku tidak ikut disalin.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
        }
        OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("Buka Diagnostics Center") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke Game Space") }
    }
}

@Composable
private fun MacroDashboard(onBack: () -> Unit, onDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(MacroAccessibilityService.PREFS, Context.MODE_PRIVATE) }
    var interval by remember { mutableFloatStateOf(prefs.getInt(MacroAccessibilityService.KEY_INTERVAL, 250).toFloat()) }
    var targetX by remember { mutableFloatStateOf(prefs.getFloat(MacroAccessibilityService.KEY_TARGET_X, 0.5f)) }
    var targetY by remember { mutableFloatStateOf(prefs.getFloat(MacroAccessibilityService.KEY_TARGET_Y, 0.5f)) }
    var triggerSize by remember { mutableFloatStateOf(prefs.getInt(MacroAccessibilityService.KEY_TRIGGER_SIZE, 64).toFloat()) }
    var opacity by remember { mutableFloatStateOf(prefs.getInt(MacroAccessibilityService.KEY_TRIGGER_ALPHA, 85).toFloat()) }
    var profile by remember { mutableStateOf(prefs.getString(MacroAccessibilityService.KEY_PROFILE, "Default") ?: "Default") }
    var profiles by remember { mutableStateOf(runCatching { val a = org.json.JSONArray(prefs.getString("profiles", "[]")); (0 until a.length()).map { a.getString(it) } }.getOrDefault(listOf("Default"))) }
    var profileMenu by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var editTrigger by remember { mutableStateOf(prefs.getBoolean("edit_trigger", false)) }
    val enabledServices = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val serviceEnabled = enabledServices.split(':').any { it.equals("${context.packageName}/${MacroAccessibilityService::class.java.name}", true) }
    val active = MacroAccessibilityService.instance != null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Red, fontSize = 32.sp, modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
            Column(Modifier.weight(1f)) {
                Text("Macro Engine", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("HOLD-TO-REPEAT CONTROL", color = Red, fontSize = 9.sp, letterSpacing = 1.6.sp)
            }
            Text("LVNG -X", color = Muted, fontSize = 10.sp)
        }
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ENGINE STATUS", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(when { !serviceEnabled -> "PERMISSION REQUIRED"; !active -> "READY · HOLD TRIGGER"; else -> "SERVICE ACTIVE" }, color = if (serviceEnabled) Color(0xFF70D6A1) else Red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (serviceEnabled) Color(0xFF70D6A1) else Red))
            }
            Spacer(Modifier.height(12.dp))
            if (!serviceEnabled) {
                SettingCard("Aktifkan Accessibility Service", "Diperlukan untuk dispatchGesture. Macro tidak berjalan sebelum layanan diaktifkan.", false) {
                    try { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("EDIT TRIGGER POSITION", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("Aktifkan sementara untuk menggeser tombol MACRO. Matikan untuk mengunci posisi.", color = Muted, fontSize = 10.sp) }
                Switch(checked = editTrigger, onCheckedChange = { editTrigger = it; prefs.edit().putBoolean("edit_trigger", it).apply(); OverlayService.instance?.refreshMacroTrigger() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Red))
            }
            Text("MACRO PROFILE", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            OutlinedTextField(value = profile, onValueChange = { profile = it.take(28); saved = false }, label = { Text("Nama profil") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Surface2, focusedLabelColor = Red))
            Box {
                OutlinedButton(onClick = { profileMenu = true }) { Text("Pilih profil: $profile") }
                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                    profiles.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = {
                        profile = name; profileMenu = false
                        val data = runCatching { org.json.JSONObject(prefs.getString("profile_$name", "{}")) }.getOrDefault(org.json.JSONObject())
                        interval = data.optInt("interval", 250).toFloat(); targetX = data.optDouble("x", 0.5).toFloat(); targetY = data.optDouble("y", 0.5).toFloat()
                        triggerSize = data.optInt("size", 64).toFloat(); opacity = data.optInt("opacity", 85).toFloat(); saved = false
                    }) }
                }
            }
            Text("TARGET POSITION · X ${"%.2f".format(targetX)} / Y ${"%.2f".format(targetY)}", color = TextMain, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Slider(value = targetX, onValueChange = { targetX = it; saved = false }, valueRange = 0.02f..0.98f, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red))
            Slider(value = targetY, onValueChange = { targetY = it; saved = false }, valueRange = 0.03f..0.97f, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("INTERVAL", color = TextMain, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("${interval.toInt()} ms", color = Red, fontWeight = FontWeight.Bold) }
            Slider(value = interval, onValueChange = { interval = it; saved = false }, valueRange = 120f..1200f, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("TRIGGER SIZE", color = TextMain, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("${triggerSize.toInt()} dp", color = Red) }
            Slider(value = triggerSize, onValueChange = { triggerSize = it; saved = false }, valueRange = 48f..100f, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("TRIGGER OPACITY", color = TextMain, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("${opacity.toInt()}%", color = Red) }
            Slider(value = opacity, onValueChange = { opacity = it; saved = false }, valueRange = 35f..100f, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red))
            Text("Cara pakai: tahan tombol MACRO yang mengambang untuk mengulang gesture ke target. Lepaskan untuk berhenti. Game tertentu dapat membatasi gesture sintetis.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val safeName = profile.ifBlank { "Default" }
                val data = org.json.JSONObject().put("interval", interval.toInt()).put("x", targetX).put("y", targetY).put("size", triggerSize.toInt()).put("opacity", opacity.toInt()).put("updatedAt", System.currentTimeMillis())
                profiles = (profiles + safeName).distinct()
                val names = org.json.JSONArray().also { a -> profiles.forEach { a.put(it) } }
                prefs.edit().putInt(MacroAccessibilityService.KEY_INTERVAL, interval.toInt())
                    .putFloat(MacroAccessibilityService.KEY_TARGET_X, targetX).putFloat(MacroAccessibilityService.KEY_TARGET_Y, targetY)
                    .putInt(MacroAccessibilityService.KEY_TRIGGER_SIZE, triggerSize.toInt()).putInt(MacroAccessibilityService.KEY_TRIGGER_ALPHA, opacity.toInt())
                    .putString(MacroAccessibilityService.KEY_PROFILE, safeName).putString("profiles", names.toString())
                    .putString("profile_$safeName", data.toString()).apply()
                OverlayService.instance?.refreshMacroTrigger()
                profile = safeName; saved = true
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text(if (saved) "Profil Tersimpan" else "Simpan Profil") }
            OutlinedButton(onClick = {
                if (profiles.size > 1) profiles = profiles.filterNot { it == profile }
                val names = org.json.JSONArray().also { a -> profiles.forEach { a.put(it) } }
                prefs.edit().putString("profiles", names.toString()).remove("profile_$profile").putString(MacroAccessibilityService.KEY_PROFILE, profiles.first()).apply()
                profile = profiles.first(); saved = false
            }, enabled = profiles.size > 1, modifier = Modifier.fillMaxWidth()) { Text("Hapus Profil Ini") }
            Button(onClick = { MacroAccessibilityService.instance?.stopMacro() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6F122A))) { Text("EMERGENCY STOP") }
            OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("Buka Diagnostics") }
        }
    }
}

@Composable
private fun TouchTestDashboard(onBack: () -> Unit, onDiagnostics: () -> Unit) {
    var pointers by remember { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var started by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    LaunchedEffect(started) {
        while (started) { kotlinx.coroutines.delay(1000); elapsed += 1000 }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Red, fontSize = 32.sp, modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
            Column { Text("Input & Control", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("TOUCH DIAGNOSTICS", color = Red, fontSize = 9.sp, letterSpacing = 1.5.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tap Count" to taps.toString(), "Pointers" to pointers.toString()).forEach { (label, value) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Surface).padding(14.dp)) {
                    Text(label, color = Muted, fontSize = 10.sp); Text(value, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Koordinat: ${x.toInt()}, ${y.toInt()} px", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surface)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { if (!started) { started = true; elapsed = 0 }; taps++; pointers = 1; x = event.x; y = event.y }
                    MotionEvent.ACTION_POINTER_DOWN -> { pointers = event.pointerCount; x = event.getX(event.actionIndex); y = event.getY(event.actionIndex) }
                    MotionEvent.ACTION_MOVE -> { pointers = event.pointerCount; if (event.pointerCount > 0) { x = event.getX(0); y = event.getY(0) } }
                    MotionEvent.ACTION_POINTER_UP -> pointers = event.pointerCount - 1
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pointers = 0
                }
                true
            }, contentAlignment = Alignment.Center) {
            Text("SENTUH AREA INI\n\nMulti-touch test", color = Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Text("Sesi: ${elapsed / 1000}s   ·   CPS rata-rata: ${if (elapsed > 0) String.format(java.util.Locale.US, "%.2f", taps * 1000f / elapsed) else "0.00"}", color = TextMain, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { taps = 0; elapsed = 0; started = false; pointers = 0 }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Reset Test") }
            OutlinedButton(onClick = onDiagnostics, modifier = Modifier.weight(1f)) { Text("Diagnostics") }
        }
        Text("Data hanya berasal dari sentuhan pada area pengujian LVNG -X; aplikasi tidak membaca sentuhan di game lain.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(vertical = 10.dp))
    }
}

@Composable
private fun SettingCard(title: String, detail: String, good: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface)
        .clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = if (good) Color(0xFF70D6A1) else Red, fontSize = 11.sp,
                modifier = Modifier.padding(top = 5.dp))
        }
        Text("›", color = Red, fontSize = 26.sp)
    }
}

@Composable
private fun DiagnosticRow(name: String, status: String, good: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(status, color = if (good) Color(0xFF70D6A1) else Red, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (good) Color(0xFF70D6A1) else Red))
    }
    HorizontalDivider(color = Surface2)
}

@Composable
private fun BottomBar(page: String, onPage: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 10.dp)
        .clip(CircleShape).background(Color(0xFF171922)).padding(7.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        BottomItem("Home", page == "home") { onPage("home") }
        BottomItem("Library", page == "library") { onPage("library") }
        BottomItem("Access", page == "diagnostics") { onPage("diagnostics") }
    }
}

@Composable
private fun BottomItem(label: String, active: Boolean, onClick: () -> Unit) {
    Text(label, color = if (active) TextMain else Muted, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clip(CircleShape).background(if (active) Red.copy(alpha = 0.20f) else Color.Transparent).clickable { onClick() }.padding(horizontal = 17.dp, vertical = 12.dp))
}

@Composable
private fun AppIcon(game: GameApp, modifier: Modifier = Modifier) {
    val bitmap = remember(game.packageName) {
        val drawable = game.icon
        if (drawable is BitmapDrawable && drawable.bitmap != null) drawable.bitmap
        else {
            val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, 96, 96)
            drawable.draw(canvas)
            bmp
        }
    }
    Image(bitmap.asImageBitmap(), contentDescription = game.label, modifier = modifier.clip(RoundedCornerShape(14.dp)))
}

@Composable
private fun GamePickerDialog(apps: List<GameApp>, selected: Set<String>, onDismiss: () -> Unit, onSelect: (GameApp) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Tambah Game", color = TextMain, fontWeight = FontWeight.Bold) },
        text = {
            if (apps.isEmpty()) Text("Tidak ada aplikasi yang dapat ditampilkan.", color = Muted)
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = app.packageName !in selected) { onSelect(app) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app, Modifier.size(38.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(app.label, color = TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (app.packageName in selected) "Ditambahkan" else "Tambah", color = if (app.packageName in selected) Muted else Red, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Selesai", color = Red) } }
    )
}

private fun installedApps(context: Context): List<GameApp> {
    val pm = context.packageManager
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { it.packageName != context.packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
        .map { GameApp(pm.getApplicationLabel(it).toString(), it.packageName, pm.getApplicationIcon(it)) }
        .sortedBy { it.label.lowercase() }
}

private fun launchGame(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Settings.canDrawOverlays(context)) {
            startOverlayService(context)
        }
        context.startActivity(intent)
    }
}

private fun saveGames(context: Context, games: List<GameApp>) {
    val array = JSONArray()
    games.forEach { array.put(it.packageName) }
    context.getSharedPreferences("lvngx", Context.MODE_PRIVATE).edit().putString("games", array.toString()).apply()
}

private fun loadGames(context: Context): List<GameApp> {
    val pm = context.packageManager
    val raw = context.getSharedPreferences("lvngx", Context.MODE_PRIVATE).getString("games", "[]") ?: "[]"
    val array = JSONArray(raw)
    val result = mutableListOf<GameApp>()
    for (i in 0 until array.length()) {
        val pkg = array.optString(i)
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            if (pm.getLaunchIntentForPackage(pkg) != null) result += GameApp(pm.getApplicationLabel(info).toString(), pkg, pm.getApplicationIcon(info))
        } catch (_: Exception) { }
    }
    return result
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private fun createBackup(context: Context): String {
    val prefs = context.getSharedPreferences("lvngx", Context.MODE_PRIVATE)
    val macroPrefs = context.getSharedPreferences(MacroAccessibilityService.PREFS, Context.MODE_PRIVATE)
    val payload = JSONObject().apply {
        put("games", JSONArray(prefs.getString("games", "[]") ?: "[]"))
        put("macro", JSONObject().apply {
            put("interval", macroPrefs.getInt(MacroAccessibilityService.KEY_INTERVAL, 250))
            put("targetX", macroPrefs.getFloat(MacroAccessibilityService.KEY_TARGET_X, 0.5f).toDouble())
            put("targetY", macroPrefs.getFloat(MacroAccessibilityService.KEY_TARGET_Y, 0.5f).toDouble())
            put("triggerSize", macroPrefs.getInt(MacroAccessibilityService.KEY_TRIGGER_SIZE, 64))
            put("triggerOpacity", macroPrefs.getInt(MacroAccessibilityService.KEY_TRIGGER_ALPHA, 85))
            put("profile", macroPrefs.getString(MacroAccessibilityService.KEY_PROFILE, "Default"))
            put("profiles", macroPrefs.getString("profiles", "[]"))
        })
        put("createdAt", System.currentTimeMillis())
    }.toString()
    return JSONObject().apply {
        put("format", "LVNG-X")
        put("formatVersion", 1)
        put("payload", payload)
        put("sha256", sha256(payload))
    }.toString(2)
}

private fun restoreBackup(context: Context, raw: String) {
    val envelope = JSONObject(raw)
    require(envelope.optString("format") == "LVNG-X") { "Format backup tidak dikenal" }
    require(envelope.optInt("formatVersion") == 1) { "Versi backup tidak didukung" }
    val payload = envelope.getString("payload")
    require(sha256(payload) == envelope.getString("sha256")) { "Integritas backup gagal: checksum tidak cocok" }
    val data = JSONObject(payload)
    val games = data.optJSONArray("games") ?: JSONArray()
    val clean = JSONArray()
    for (i in 0 until games.length()) {
        val pkg = games.optString(i)
        if (pkg.isNotBlank() && pkg != context.packageName) clean.put(pkg)
    }
    context.getSharedPreferences("lvngx", Context.MODE_PRIVATE).edit()
        .putString("games", clean.toString()).commit()
    val macro = data.optJSONObject("macro")
    if (macro != null) context.getSharedPreferences(MacroAccessibilityService.PREFS, Context.MODE_PRIVATE).edit()
        .putInt(MacroAccessibilityService.KEY_INTERVAL, macro.optInt("interval", 250).coerceIn(120, 2000))
        .putFloat(MacroAccessibilityService.KEY_TARGET_X, macro.optDouble("targetX", 0.5).toFloat().coerceIn(0f, 1f))
        .putFloat(MacroAccessibilityService.KEY_TARGET_Y, macro.optDouble("targetY", 0.5).toFloat().coerceIn(0f, 1f))
        .putInt(MacroAccessibilityService.KEY_TRIGGER_SIZE, macro.optInt("triggerSize", 64).coerceIn(48, 100))
        .putInt(MacroAccessibilityService.KEY_TRIGGER_ALPHA, macro.optInt("triggerOpacity", 85).coerceIn(35, 100))
        .putString(MacroAccessibilityService.KEY_PROFILE, macro.optString("profile", "Default"))
        .putString("profiles", macro.optString("profiles", "[\"Default\"]"))
        .commit()
}

private fun startOverlayService(context: Context) {
    if (!Settings.canDrawOverlays(context)) return
    try { context.startService(Intent(context, OverlayService::class.java)) } catch (_: Exception) { }
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }
}
