package oneringr.huaweipods.modify.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import oneringr.huaweipods.modify.*
import oneringr.huaweipods.modify.audio.AudioSource
import oneringr.huaweipods.modify.data.Sound
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

private data class Palette(val background: Color, val surface: Color, val ink: Color, val muted: Color, val line: Color, val accent: Color, val soft: Color)
private val LocalPalette = staticCompositionLocalOf { Palette(Color(0xfff9f9f9), Color.White, Color(0xff202020), Color(0xff646464), Color(0xffe8e8e8), Color(0xff0090ff), Color(0xffe6f4fe)) }

@Composable
fun AppUi(model: AppModel, s: UiState, import: () -> Unit, requestBluetooth: () -> Unit, exportSound: (Sound) -> Unit, exportReady: (java.io.File, String) -> Unit) {
    val context = LocalContext.current
    val dark = when(s.theme) { "浅色" -> false; "深色" -> true; else -> isSystemInDarkTheme() }
    val p = if (dark) Palette(Color(0xff111111), Color(0xff191919), Color(0xffeeeeee), Color(0xffb4b4b4), Color(0xff333333), Color(0xff3b9eff), Color(0xff0d2847)) else LocalPalette.current
    val controller = remember(dark) {
        ThemeController(colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            lightColors = lightColorScheme(primary = p.accent, background = p.background, surface = p.background, surfaceContainer = p.surface, surfaceVariant = p.surface, onSurface = p.ink, onBackground = p.ink, onSurfaceVariantSummary = p.muted),
            darkColors = darkColorScheme(primary = p.accent, background = p.background, surface = p.background, surfaceContainer = p.surface, surfaceVariant = p.surface, onSurface = p.ink, onBackground = p.ink, onSurfaceVariantSummary = p.muted))
    }
    val styles = remember { defaultTextStyles(main = TextStyle(fontSize = 16.sp), paragraph = TextStyle(fontSize = 16.sp), body1 = TextStyle(fontSize = 16.sp), body2 = TextStyle(fontSize = 13.sp), button = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)) }
    CompositionLocalProvider(LocalPalette provides p) {
        MiuixTheme(controller = controller, textStyles = styles) {
            var devices by remember { mutableStateOf(false) }
            var rename by remember { mutableStateOf<Sound?>(null) }
            var nameNew by remember { mutableStateOf(false) }
            var menu by remember { mutableStateOf<Sound?>(null) }
            var remove by remember { mutableStateOf<Sound?>(null) }
            var sort by remember { mutableStateOf(false) }
            var theme by remember { mutableStateOf(false) }
            var format by remember { mutableStateOf(false) }
            var licensesExpanded by rememberSaveable { mutableStateOf(false) }
            var miuixLicense by remember { mutableStateOf(false) }
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(s.exported) {
                s.exported?.let { file ->
                    exportReady(file, ((s.source?.name?.substringBeforeLast('.') ?: "音频") + "_剪辑." + file.extension))
                    model.consumeExport()
                }
            }
            LaunchedEffect(s.message) {
                s.message?.let { message ->
                    snackbar.showSnackbar(message, duration = SnackbarDuration.Custom(4200))
                    if (model.state.value.message == message) model.dismissMessage()
                }
            }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                floatingToolbar = {
                    AnimatedVisibility(s.busy, modifier = Modifier.padding(bottom = NavigationBarDefaults.ItemHeight), enter = expandVertically(tween(250)) + fadeIn(tween(250)), exit = shrinkVertically(tween(200)) + fadeOut(tween(200))) {
                        FloatingToolbar(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = p.surface, cornerRadius = 20.dp, outSidePadding = PaddingValues(0.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Label(s.task, Modifier.weight(1f)); Caption("${(s.progress * 100).toInt()}%"); Spacer(Modifier.width(12.dp)); SmallAction("取消", onClick = model::cancelTask) }
                                LinearProgressIndicator(progress = s.progress, colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = p.accent, backgroundColor = Color.Transparent))
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) { data -> Snackbar(data, colors = SnackbarDefaults.snackbarColors(containerColor = p.soft, contentColor = p.accent)) } },
                bottomBar = {
                    NavigationBar(color = p.surface, showDivider = false) {
                        listOf("设置", "处理", "我的").forEachIndexed { index, title ->
                            NavigationBarItem(selected = s.page == index, onClick = { model.page(index) }, icon = listOf(Icons.Settings, Icons.Edit, Icons.Person)[index], label = title)
                        }
                    }
                }
            ) { insets ->
                Column(Modifier.fillMaxSize().background(p.background).padding(insets)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(listOf("设置", "处理", "我的")[s.page], fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f).semantics { heading() })
                        if (s.page == 1 && s.source != null) SmallAction("重新导入", enabled = !s.busy, onClick = import)
                        if (s.page == 2) SmallAction("外观", onClick = { theme = true })
                    }
                    Crossfade(s.page, animationSpec = tween(180), label = "pages", modifier = Modifier.weight(1f)) { page ->
                        when(page) {
                            0 -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                item {
                                    Surface {
                                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RoundIcon(Icons.Headphones)
                                            Column(Modifier.weight(1f).padding(horizontal = 14.dp).clickable(enabled = !s.busy) { requestBluetooth(); devices = true }) {
                                                Label(s.device?.name ?: "选择耳机", maxLines = 2)
                                                Caption(if (s.connected) "设备已就绪" else "从已配对设备中选择")
                                            }
                                            SmallAction("检测", enabled = !s.busy, onClick = { if (s.device == null) { requestBluetooth(); devices = true } else model.connect() })
                                        }
                                    }
                                }
                                item { SectionTitle("默认音效") }
                                item { Surface { SoundRow("默认", "舒展", selected = s.activeKey == "default", enabled = !s.busy, onApply = { model.apply("default") }) } }
                                item { SectionTitle("预设音效") }
                                item {
                                    Surface {
                                        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Label("增益", Modifier.weight(1f))
                                                SmallAction("还原", !s.busy) { model.presetGain(1f) }
                                                Text("${(s.presetGain * 100).roundToLong()}%", color = p.accent, fontSize = 13.sp, modifier = Modifier.widthIn(min = 42.dp))
                                            }
                                            GainSlider(s.presetGain, !s.busy, "预设音效增益", model::presetGain)
                                        }
                                        Divider()
                                        presets.forEachIndexed { i, preset ->
                                        val key = "preset:${preset.id}"
                                        SoundRow(preset.name, selected = s.activeKey == key, playing = s.playing == key, enabled = !s.busy, onPreview = { model.preview(key) }, onApply = { model.apply(key) })
                                        if (i < presets.lastIndex) Divider()
                                    } }
                                }
                                item { Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("我的音效库", Modifier.weight(1f)); if (s.library.isNotEmpty()) SmallAction("管理", onClick = { model.page(2) }) } }
                                item {
                                    Surface {
                                        if (s.library.isEmpty()) Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Caption("还没有添加音效", Modifier.weight(1f)); SmallAction("添加", enabled = !s.busy, onClick = import)
                                        } else model.sortedSounds(s).forEachIndexed { i, sound ->
                                            val key = "library:${sound.id}"
                                            SoundRow(sound.name, formatTime(sound.durationMs), selected = s.activeKey == key, playing = s.playing == key, enabled = !s.busy, onPreview = { model.preview(key) }, onApply = { model.apply(key) })
                                            if (i < s.library.lastIndex) Divider()
                                        }
                                    }
                                }
                                item { Surface { EntryRow("自定义音乐", Icons.Add, "导入音频或视频", enabled = !s.busy, onClick = import) } }
                            }
                            1 -> EditorPage(s, model, import, { format = true }, { nameNew = true })
                            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                item { Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("音效库", Modifier.weight(1f)); if (s.library.isNotEmpty()) SmallAction(s.sort, onClick = { sort = true }) } }
                                if (s.library.isEmpty()) item { Surface { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    RoundIcon(Icons.Music); Label("添加你的第一段音效"); PrimaryButton("导入音频或视频", enabled = !s.busy, onClick = import)
                                } } }
                                items(model.sortedSounds(s), key = { it.id }) { sound ->
                                    Surface {
                                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            IconAction(if (s.playing == "library:${sound.id}") Icons.Stop else Icons.Play, "试听 ${sound.name}", enabled = !s.busy) { model.preview("library:${sound.id}") }
                                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) { Label(sound.name, maxLines = 2); Caption(formatTime(sound.durationMs)) }
                                            IconAction(Icons.More, "管理 ${sound.name}", enabled = !s.busy) { menu = sound }
                                            var drag by remember(sound.id) { mutableFloatStateOf(0f) }
                                            val threshold = with(LocalDensity.current) { 56.dp.toPx() }
                                            Box(Modifier.size(44.dp).semantics { contentDescription = "长按拖动排序 ${sound.name}" }
                                                .pointerInput(sound.id, s.busy) {
                                                    if (!s.busy) detectDragGesturesAfterLongPress(onDragStart = { drag = 0f }, onDrag = { change, amount ->
                                                        change.consume(); drag += amount.y
                                                        if (abs(drag) > threshold) { model.move(sound.id, if (drag > 0) 1 else -1); drag = 0f }
                                                    })
                                                }, contentAlignment = Alignment.Center) { Icon(Icons.Drag, null, tint = p.muted) }
                                        }
                                    }
                                }
                                item { SectionTitle("作者") }
                                item {
                                    val context = LocalContext.current
                                    val avatar = remember { context.assets.open("author/avatar.png").use { BitmapFactory.decodeStream(it).asImageBitmap() } }
                                    Surface { Row(Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/oneringr"))) }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Image(avatar, "OneRingR 头像", Modifier.size(52.dp).clip(CircleShape))
                                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Label("OneRingR"); Caption("github.com/oneringr") }
                                        Icon(Icons.External, null, tint = p.muted)
                                    } }
                                }
                                item { Surface {
                                    Row(Modifier.fillMaxWidth().clickable { licensesExpanded = !licensesExpanded }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Layers, null, tint = p.accent, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(14.dp))
                                        Label("开放源代码许可", Modifier.weight(1f))
                                        Icon(if (licensesExpanded) Icons.Up else Icons.Down, if (licensesExpanded) "收起许可" else "展开许可", tint = p.muted, modifier = Modifier.size(18.dp))
                                    }
                                    AnimatedVisibility(licensesExpanded, enter = expandVertically(tween(250)) + fadeIn(tween(250)), exit = shrinkVertically(tween(200)) + fadeOut(tween(200))) {
                                        Column { Divider(); EntryRow("Miuix", Icons.Layers, "Apache License 2.0", onClick = { miuixLicense = true }) }
                                    }
                                    Divider()
                                    Row(Modifier.fillMaxWidth().clickable(enabled = !s.checkingUpdate, onClick = model::checkForUpdates).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Label("版本", Modifier.weight(1f))
                                        Caption(if (s.checkingUpdate) "正在检查" else BuildConfig.VERSION_NAME)
                                        Spacer(Modifier.width(8.dp)); Icon(Icons.Next, null, tint = p.muted, modifier = Modifier.size(18.dp))
                                    }
                                } }
                            }
                        }
                    }
                }
            }
            if (devices) DevicePicker(s.devices, { devices = false }, requestBluetooth) { device -> model.selectDevice(device); devices = false; model.connect() }
            if (miuixLicense) Sheet("Miuix", { miuixLicense = false }) {
                SmallAction("项目主页") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/compose-miuix-ui/miuix"))) }
                val license = remember { context.assets.open("licenses/Miuix-Apache-2.0.txt").bufferedReader().use { it.readText() } }
                Text(license, color = p.ink, fontSize = 13.sp)
            }
            s.error?.let { Sheet("操作未完成", model::dismissError) { Label(it); QuietButton("知道了", Modifier.fillMaxWidth(), onClick = model::dismissError) } }
            s.update?.let { release -> Sheet("发现新版本", model::dismissUpdate) {
                Label(release.tag)
                Caption("当前版本 ${BuildConfig.VERSION_NAME}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuietButton("稍后", Modifier.weight(1f), onClick = model::dismissUpdate)
                    PrimaryButton("去下载", Modifier.weight(1f)) { model.dismissUpdate(); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url))) }
                }
            } }
            if (nameNew || rename != null) NameDialog(if (nameNew) "添加到音效库" else "重命名", rename?.name ?: s.source?.name?.substringBeforeLast('.') ?: "新音效", onDismiss = { nameNew = false; rename = null }) { name ->
                if (nameNew) model.addToLibrary(name) else rename?.let { model.rename(it, name) }
                nameNew = false; rename = null
            }
            menu?.let { sound -> Sheet(sound.name, { menu = null }) {
                EntryRow("应用音效", Icons.Headphones, onClick = { menu = null; model.apply("library:${sound.id}") })
                EntryRow("保存到手机", Icons.Download, onClick = { menu = null; exportSound(sound) })
                EntryRow("重命名", Icons.Edit, onClick = { menu = null; rename = sound })
                EntryRow("上移", Icons.Up, onClick = { model.move(sound.id, -1); menu = null })
                EntryRow("下移", Icons.Down, onClick = { model.move(sound.id, 1); menu = null })
                EntryRow("删除", Icons.Delete, onClick = { menu = null; remove = sound })
            } }
            remove?.let { sound -> Sheet("删除音效", { remove = null }) {
                Label("删除“${sound.name}”？"); Caption("导入的原始文件会保留。")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuietButton("取消", Modifier.weight(1f), onClick = { remove = null })
                    PrimaryButton("删除", Modifier.weight(1f), onClick = { model.delete(sound); remove = null })
                }
            } }
            if (sort) Sheet("音效库排序", { sort = false }) {
                ChoiceButtons(listOf("手动", "名称", "最新")) { index -> model.setSort(listOf("手动排序", "名称排序", "最新添加")[index]); sort = false }
            }
            if (theme) Sheet("外观", { theme = false }) { ChoiceButtons(listOf("跟随系统", "浅色", "深色")) { index -> model.setTheme(listOf("跟随系统", "浅色", "深色")[index]); theme = false } }
            if (format) Sheet("保存音频", { format = false }) {
                ChoiceButtons(listOf("M4A", "WAV")) { index -> format = false; model.render(if (index == 0) "m4a" else "wav") }
            }
        }
    }
}

@Composable private fun EditorPage(s: UiState, model: AppModel, import: () -> Unit, export: () -> Unit, add: () -> Unit) {
    val source = s.source
    if (source == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            RoundIcon(Icons.Edit); Spacer(Modifier.height(24.dp)); Label("从一段声音开始")
            Spacer(Modifier.height(20.dp)); PrimaryButton("导入音频或视频", enabled = !s.busy, onClick = import)
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Label(source.name, maxLines = 2)
            WaveTimeline(source, s.start, s.end, s.cursor, !s.busy, model::selection, model::cursor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Caption("选区 ${formatTime(s.end - s.start)}")
                SmallAction("全部", !s.busy, model::all)
                SmallAction("前 6 秒", !s.busy, model::firstSix)
                IconAction(if (s.playing == "editor") Icons.Stop else Icons.Play, if (s.playing == "editor") "停止试听" else "试听选区", !s.busy, model::previewEditor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("开始", s.start, Modifier.weight(1f), !s.busy) { model.selection(it, s.end) }
                TimeField("结束", s.end, Modifier.weight(1f), !s.busy) { model.selection(s.start, it) }
            }
        } }
        Surface { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row { Label("增益", Modifier.weight(1f)); Label("${(s.gain * 100).roundToLong()}%") }
            GainSlider(s.gain, !s.busy, "音频增益", model::gain)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Caption("0%"); SmallAction("还原", !s.busy) { model.gain(1f) }; Caption("200%") }
        } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuietButton("保存到手机", Modifier.weight(1f), !s.busy, export)
            PrimaryButton("添加到音效库", Modifier.weight(1f), !s.busy && s.end - s.start <= 6000, add)
        }
        if (s.end - s.start > 6000) Caption("添加为开盒音效前，请将选区裁切到 6 秒以内。", Modifier.padding(horizontal = 8.dp))
    }
}

@Composable private fun WaveTimeline(source: AudioSource, start: Long, end: Long, cursor: Long, enabled: Boolean, select: (Long, Long) -> Unit, seek: (Long) -> Unit) {
    val p = LocalPalette.current
    var span by remember(source.file) { mutableLongStateOf(source.durationMs.coerceAtLeast(1)) }
    var viewStart by remember(source.file) { mutableLongStateOf(0) }
    val current by rememberUpdatedState(Triple(start, end, cursor))
    var target by remember { mutableIntStateOf(0) }
    val all = source.durationMs.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(16.dp)).background(p.background)
            .semantics { contentDescription = "音频波形，左右拖动边界裁切" }
            .pointerInput(source.file, enabled, span, viewStart) {
                if (enabled) detectDragGestures(onDragStart = { pos ->
                    val l = (current.first - viewStart).toFloat() / span * size.width
                    val r = (current.second - viewStart).toFloat() / span * size.width
                    target = if (abs(pos.x - l) <= abs(pos.x - r)) 0 else 1
                }, onDrag = { change, _ ->
                    change.consume()
                    val at = (viewStart + change.position.x.coerceIn(0f, size.width.toFloat()) / size.width * span).roundToLong().coerceIn(0, all)
                    if (target == 0) select(at.coerceAtMost(current.second - 1), current.second) else select(current.first, at.coerceAtLeast(current.first + 1))
                })
            }
            .pointerInput(source.file, enabled, span, viewStart) { if (enabled) detectTapGestures { pos -> seek((viewStart + pos.x / size.width * span).roundToLong()) } }) {
            val pad = 8.dp.toPx()
            val bars = (size.width / 5.dp.toPx()).toInt().coerceAtLeast(1)
            repeat(bars) { i ->
                val time = viewStart + (i + 0.5f) / bars * span
                val index = (time / all * source.waveform.size).toInt().coerceIn(0, source.waveform.lastIndex)
                val h = max(3.dp.toPx(), source.waveform[index] * (size.height - pad * 4))
                val x = i.toFloat() / bars * size.width
                drawLine(if (time >= start && time <= end) p.accent else p.muted.copy(alpha = 0.25f), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), 2.5.dp.toPx(), StrokeCap.Round)
            }
            val left = ((start - viewStart).toFloat() / span * size.width).coerceIn(0f, size.width)
            val right = ((end - viewStart).toFloat() / span * size.width).coerceIn(0f, size.width)
            drawRect(p.accent.copy(alpha = .08f), Offset(left, 0f), Size((right - left).coerceAtLeast(0f), size.height))
            listOf(start, end).forEach { time ->
                if (time in viewStart..(viewStart + span)) {
                    val x = ((time - viewStart).toFloat() / span * size.width).coerceIn(4.dp.toPx(), size.width - 4.dp.toPx())
                    drawLine(p.accent, Offset(x, 8.dp.toPx()), Offset(x, size.height - 8.dp.toPx()), 6.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White, Offset(x, size.height / 2 - 8.dp.toPx()), Offset(x, size.height / 2 + 8.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            if (cursor in viewStart..(viewStart + span)) {
                val x = (cursor - viewStart).toFloat() / span * size.width
                drawLine(p.ink, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption(formatTime(viewStart), Modifier.weight(1f))
            SmallAction("缩小", enabled && span < all) { span = min(all, span * 2); viewStart = viewStart.coerceAtMost(all - span) }
            SmallAction("放大", enabled && span > 500) {
                span = max(500, span / 2).coerceAtMost(all)
                viewStart = ((start + min(end, start + span)) / 2 - span / 2).coerceIn(0, all - span)
            }
            Caption(formatTime(min(all, viewStart + span)))
        }
        if (span < all) Slider(value = viewStart.toFloat(), onValueChange = { viewStart = it.toLong() }, valueRange = 0f..max(1, all - span).toFloat(), enabled = enabled, modifier = Modifier.height(32.dp))
    }
}

@Composable private fun TimeField(title: String, millis: Long, modifier: Modifier, enabled: Boolean, change: (Long) -> Unit) {
    var text by remember { mutableStateOf(String.format(Locale.US, "%.3f", millis / 1000.0)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(millis, focused) { if (!focused) text = String.format(Locale.US, "%.3f", millis / 1000.0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption("$title（秒）")
        TextField(value = text, onValueChange = { v -> text = v; v.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { change((it * 1000).roundToLong()) } }, modifier = Modifier.onFocusChanged { focused = it.isFocused }, enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
}

@Composable private fun Surface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LocalPalette.current.surface), content = content)
}
@Composable private fun Label(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) = Text(text, modifier, color = LocalPalette.current.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
@Composable private fun Caption(text: String, modifier: Modifier = Modifier) = Text(text, modifier, color = LocalPalette.current.muted, fontSize = 13.sp)
@Composable private fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(text, modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp), color = LocalPalette.current.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
@Composable private fun Divider() = Spacer(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(0.5.dp).background(LocalPalette.current.line))
@Composable private fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) = Button(onClick = onClick, modifier = modifier, enabled = enabled, colors = ButtonDefaults.buttonColorsPrimary()) { Text(text, fontSize = 16.sp) }
@Composable private fun QuietButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) = Button(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text, fontSize = 16.sp) }
@Composable private fun SmallAction(text: String, enabled: Boolean = true, onClick: () -> Unit) = Text(text, Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp), color = LocalPalette.current.accent.copy(alpha = if (enabled) 1f else .35f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
@Composable private fun IconAction(icon: ImageVector, description: String, enabled: Boolean = true, action: () -> Unit) = Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = action), contentAlignment = Alignment.Center) { Icon(icon, description, tint = LocalPalette.current.accent.copy(alpha = if (enabled) 1f else .35f), modifier = Modifier.size(22.dp)) }
@Composable private fun RoundIcon(icon: ImageVector) = Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(LocalPalette.current.soft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = LocalPalette.current.accent, modifier = Modifier.size(25.dp)) }

@Composable private fun SoundRow(name: String, detail: String? = null, selected: Boolean, playing: Boolean = false, enabled: Boolean = true, onPreview: (() -> Unit)? = null, onApply: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(enabled, onClick = onApply).padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Label(name, maxLines = 2); if (detail != null) Caption(detail) }
        if (onPreview != null) IconAction(if (playing) Icons.Stop else Icons.Play, "试听 $name", enabled, onPreview)
        Canvas(Modifier.size(44.dp).semantics { contentDescription = if (selected) "$name 已应用" else "应用 $name" }) {
            drawCircle(if (selected) p.accent else p.muted.copy(alpha = .4f), 10.dp.toPx(), style = Stroke(if (selected) 6.dp.toPx() else 1.5.dp.toPx()))
        }
    }
}

@Composable private fun EntryRow(text: String, icon: ImageVector, detail: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled, onClick = onClick).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalPalette.current.accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Label(text); detail?.let { Caption(it) } }
        Icon(Icons.Next, null, tint = LocalPalette.current.muted, modifier = Modifier.size(18.dp))
    }
}
@Composable private fun ChoiceButtons(labels: List<String>, choose: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        labels.forEachIndexed { index, title ->
            Button(onClick = { choose(index) }, modifier = Modifier.weight(1f), minWidth = 0.dp, insideMargin = PaddingValues(horizontal = 8.dp, vertical = 14.dp)) { Text(title, fontSize = 16.sp, maxLines = 1) }
        }
    }
}

@Composable private fun GainSlider(value: Float, enabled: Boolean, description: String, change: (Float) -> Unit) {
    val p = LocalPalette.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val alpha = if (enabled) 1f else .35f
    Slider(value, change, enabled = enabled, valueRange = 0f..2f, height = 44.dp,
        keyPoints = listOf(1f), magnetThreshold = .015f,
        colors = SliderDefaults.sliderColors(backgroundColor = Color.Transparent, foregroundColor = Color.Transparent, thumbColor = Color.Transparent, disabledBackgroundColor = Color.Transparent, disabledForegroundColor = Color.Transparent, disabledThumbColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = description }.drawBehind {
            val pad = size.height / 2
            val center = Offset(size.width / 2, size.height / 2)
            val fraction = value.coerceIn(0f, 2f) / 2
            val x = pad + (if (rtl) 1f - fraction else fraction) * (size.width - 2 * pad)
            drawLine(p.line, Offset(pad, center.y), Offset(size.width - pad, center.y), 6.dp.toPx(), StrokeCap.Round)
            drawLine(p.muted.copy(alpha = .45f), Offset(center.x, center.y - 7.dp.toPx()), Offset(center.x, center.y + 7.dp.toPx()), 1.dp.toPx())
            if (abs(value - 1f) > .001f) drawLine(p.accent.copy(alpha = alpha), center, Offset(x, center.y), 6.dp.toPx(), StrokeCap.Round)
            drawCircle(p.accent.copy(alpha = alpha), 11.dp.toPx(), Offset(x, center.y))
            drawCircle(p.surface, 7.dp.toPx(), Offset(x, center.y))
        })
}

@Composable private fun DevicePicker(devices: List<PairedDevice>, dismiss: () -> Unit, refresh: () -> Unit, select: (PairedDevice) -> Unit) {
    Dialog(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).clip(RoundedCornerShape(28.dp)).background(LocalPalette.current.surface).padding(24.dp)) {
            Text("选择耳机", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = LocalPalette.current.ink)
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(vertical = 14.dp)) {
                if (devices.isEmpty()) item { Caption("请先在系统蓝牙设置中配对耳机", Modifier.padding(vertical = 16.dp)) }
                items(devices, key = { it.address }) { device -> EntryRow(device.name, Icons.Headphones, onClick = { select(device) }) }
            }
            QuietButton("刷新设备", Modifier.fillMaxWidth(), onClick = refresh)
        }
    }
}

@Composable private fun Sheet(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).clip(RoundedCornerShape(28.dp)).background(LocalPalette.current.surface).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = LocalPalette.current.ink)
            content()
        }
    }
}
@Composable private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, confirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    Sheet(title, onDismiss) {
        TextField(value, { value = it.take(80) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { QuietButton("取消", Modifier.weight(1f), onClick = onDismiss); PrimaryButton("保存", Modifier.weight(1f), value.isNotBlank()) { confirm(value) } }
    }
}
private fun formatTime(ms: Long): String = String.format(Locale.US, "%d:%02d.%03d", ms / 60000, ms / 1000 % 60, ms % 1000)

private object Icons {
    private fun icon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, pathBuilder = draw)
    }.build()
    val Settings = icon("设置") { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f); moveTo(8f, 3f); lineTo(8f, 9f); moveTo(16f, 9f); lineTo(16f, 15f); moveTo(10f, 15f); lineTo(10f, 21f) }
    val Edit = icon("处理") { moveTo(4f, 17f); lineTo(4f, 20f); lineTo(7f, 20f); lineTo(20f, 7f); lineTo(17f, 4f); close(); moveTo(14f, 7f); lineTo(17f, 10f) }
    val Person = icon("我的") { moveTo(12f, 12f); curveTo(6f, 12f, 6f, 3f, 12f, 3f); curveTo(18f, 3f, 18f, 12f, 12f, 12f); moveTo(4f, 21f); curveTo(4f, 11f, 20f, 11f, 20f, 21f) }
    val Headphones = icon("耳机") { moveTo(4f, 14f); lineTo(4f, 11f); curveTo(4f, 0f, 20f, 0f, 20f, 11f); lineTo(20f, 14f); moveTo(4f, 12f); lineTo(8f, 12f); lineTo(8f, 20f); lineTo(4f, 20f); close(); moveTo(20f, 12f); lineTo(16f, 12f); lineTo(16f, 20f); lineTo(20f, 20f); close() }
    val Play = icon("试听") { moveTo(8f, 4f); lineTo(20f, 12f); lineTo(8f, 20f); close() }
    val Stop = icon("停止") { moveTo(6f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close() }
    val More = icon("更多") { moveTo(5f, 11f); lineTo(5f, 13f); moveTo(12f, 11f); lineTo(12f, 13f); moveTo(19f, 11f); lineTo(19f, 13f) }
    val Drag = icon("排序") { moveTo(7f, 7f); lineTo(17f, 7f); moveTo(7f, 12f); lineTo(17f, 12f); moveTo(7f, 17f); lineTo(17f, 17f) }
    val Add = icon("添加") { moveTo(12f, 4f); lineTo(12f, 20f); moveTo(4f, 12f); lineTo(20f, 12f) }
    val Music = icon("音效库") { moveTo(8f, 17f); lineTo(8f, 5f); lineTo(19f, 3f); lineTo(19f, 15f); moveTo(8f, 17f); curveTo(0f, 12f, 1f, 24f, 8f, 17f); moveTo(19f, 15f); curveTo(11f, 10f, 12f, 22f, 19f, 15f) }
    val External = icon("GitHub") { moveTo(14f, 3f); lineTo(21f, 3f); lineTo(21f, 10f); moveTo(21f, 3f); lineTo(11f, 13f); moveTo(10f, 5f); lineTo(4f, 5f); lineTo(4f, 21f); lineTo(19f, 21f); lineTo(19f, 15f) }
    val Next = icon("打开") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    val Layers = icon("组件") { moveTo(3f, 8f); lineTo(12f, 3f); lineTo(21f, 8f); lineTo(12f, 13f); close(); moveTo(3f, 13f); lineTo(12f, 18f); lineTo(21f, 13f); moveTo(3f, 18f); lineTo(12f, 23f); lineTo(21f, 18f) }
    val Download = icon("保存") { moveTo(12f, 3f); lineTo(12f, 16f); moveTo(7f, 11f); lineTo(12f, 16f); lineTo(17f, 11f); moveTo(4f, 17f); lineTo(4f, 21f); lineTo(20f, 21f); lineTo(20f, 17f) }
    val Delete = icon("删除") { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(9f, 3f); lineTo(15f, 3f); moveTo(6f, 6f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 6f); moveTo(10f, 10f); lineTo(10f, 17f); moveTo(14f, 10f); lineTo(14f, 17f) }
    val Up = icon("上移") { moveTo(5f, 15f); lineTo(12f, 8f); lineTo(19f, 15f) }
    val Down = icon("下移") { moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f) }
}
