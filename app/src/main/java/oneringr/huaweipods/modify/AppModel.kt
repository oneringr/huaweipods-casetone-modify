package oneringr.huaweipods.modify

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import oneringr.huaweipods.modify.audio.AudioEngine
import oneringr.huaweipods.modify.audio.AudioSource
import oneringr.huaweipods.modify.bluetooth.ToneClient
import oneringr.huaweipods.modify.bluetooth.ToneStatus
import oneringr.huaweipods.modify.data.Sound
import oneringr.huaweipods.modify.data.SoundLibrary
import oneringr.huaweipods.modify.update.GithubRelease
import oneringr.huaweipods.modify.update.GithubUpdates
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min
import kotlin.math.roundToInt

data class Preset(val id: String, val name: String)
val presets = listOf(
    Preset("ciallo", "Ciallo～(∠・ω )⌒☆"), Preset("man", "MAN!"), Preset("what_can_i_say", "WHAT CAN I SAY"),
    Preset("mambo", "曼波"), Preset("far_ahead", "遥遥领先"), Preset("steel", "钢管")
)
data class PairedDevice(val address: String, val name: String)
data class UiState(
    val page: Int = 0,
    val devices: List<PairedDevice> = emptyList(),
    val device: PairedDevice? = null,
    val connected: Boolean = false,
    val tone: ToneStatus? = null,
    val activeKey: String = "",
    val library: List<Sound> = emptyList(),
    val sort: String = "手动排序",
    val source: AudioSource? = null,
    val start: Long = 0,
    val end: Long = 0,
    val gain: Float = 1f,
    val presetGain: Float = 1f,
    val cursor: Long = 0,
    val playing: String? = null,
    val busy: Boolean = false,
    val task: String = "",
    val progress: Float = 0f,
    val error: String? = null,
    val message: String? = null,
    val theme: String = "跟随系统",
    val exported: File? = null,
    val checkingUpdate: Boolean = false,
    val update: GithubRelease? = null,
)

class AppModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", 0)
    private val library = SoundLibrary(app)
    private val libraryMutex = Mutex()
    private val engine = AudioEngine(app)
    private val mutable = MutableStateFlow(UiState(activeKey = prefs.getString("activeKey", "")!!, theme = prefs.getString("theme", "跟随系统")!!, presetGain = prefs.getFloat("presetGain", 1f).coerceIn(0f, 2f)))
    val state = mutable.asStateFlow()
    private var taskJob: Job? = null
    private var playJob: Job? = null
    private val playbackMutex = Mutex()
    @Volatile private var playGeneration = 0
    private var lastProgressAt = 0L
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var client: ToneClient? = null
    private val app get() = getApplication<Application>()
    init {
        editLibrary("音效库读取失败") {
            val sounds = library.load()
            mutable.update { it.copy(library = sounds) }
        }
    }

    private fun editLibrary(message: String, work: () -> Unit) {
        if (mutable.value.busy) return
        viewModelScope.launch {
            try { libraryMutex.withLock { runInterruptible(Dispatchers.IO) { work() } } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError("$message：${e.message ?: "请重试"}") }
        }
    }

    fun page(page: Int) { mutable.update { it.copy(page = page) } }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun dismissMessage() { mutable.update { it.copy(message = null) } }
    fun showError(message: String) { mutable.update { it.copy(error = message) } }
    fun dismissUpdate() { mutable.update { it.copy(update = null) } }
    fun checkForUpdates() {
        if (mutable.value.checkingUpdate) return
        mutable.update { it.copy(checkingUpdate = true) }
        viewModelScope.launch {
            try {
                val release = runInterruptible(Dispatchers.IO) { GithubUpdates.latest() }
                mutable.update {
                    when {
                        release == null -> it.copy(message = "GitHub 暂无发布版本")
                        GithubUpdates.isNewer(release.tag, BuildConfig.VERSION_NAME) -> it.copy(update = release)
                        else -> it.copy(message = "当前已是最新版本")
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(message = e.message ?: "检查更新失败，请稍后重试") } }
            finally { mutable.update { it.copy(checkingUpdate = false) } }
        }
    }
    fun setTheme(theme: String) { prefs.edit().putString("theme", theme).apply(); mutable.update { it.copy(theme = theme) } }
    fun setSort(sort: String) { mutable.update { it.copy(sort = sort) } }
    fun sortedSounds(s: UiState = mutable.value): List<Sound> = when(s.sort) {
        "名称排序" -> s.library.sortedBy { it.name.lowercase() }
        "最新添加" -> s.library.sortedByDescending { it.created }
        else -> s.library.sortedBy { it.order }
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        try {
            val adapter = app.getSystemService(BluetoothManager::class.java).adapter ?: error("此设备不支持蓝牙")
            require(adapter.isEnabled) { "请先开启手机蓝牙" }
            val devices = adapter.bondedDevices.map { PairedDevice(it.address, it.name ?: "已配对设备") }.sortedBy { it.name }
            val saved = prefs.getString("address", null)
            mutable.update { it.copy(devices = devices, device = devices.find { d -> d.address == (it.device?.address ?: saved) } ?: devices.singleOrNull()) }
        } catch (e: SecurityException) { showError("请允许附近设备权限后再连接") }
        catch (e: Throwable) { showError(e.message ?: "无法读取蓝牙设备") }
    }

    fun selectDevice(device: PairedDevice) {
        prefs.edit().putString("address", device.address).apply()
        mutable.update { it.copy(device = device, connected = false, tone = null) }
    }

    private fun runTask(label: String, connected: Boolean = false, libraryWrite: Boolean = false, work: () -> Unit) {
        if (mutable.value.busy) return
        if (connected && mutable.value.device == null) { showError("请先点击上方设备卡片，选择已配对的耳机"); return }
        if (connected && Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            showError("请先允许附近设备权限，并选择耳机"); return
        }
        stopPlayback()
        mutable.update { it.copy(busy = true, task = label, progress = 0f, error = null, exported = null) }
        taskJob = viewModelScope.launch {
            try {
                TaskService.start(app, connected) { cancelTask() }
                playbackMutex.withLock { }
                if (libraryWrite) libraryMutex.withLock { runInterruptible(Dispatchers.IO) { work() } }
                else runInterruptible(Dispatchers.IO) { work() }
            } catch (_: CancellationException) { mutable.update { it.copy(message = "操作已取消") } }
            catch (e: Throwable) { mutable.update { it.copy(error = e.message ?: "操作失败，请重试", connected = if (connected) false else it.connected) } }
            finally { client?.close(); client = null; TaskService.stop(app); mutable.update { it.copy(busy = false) } }
        }
    }

    fun cancelTask() { client?.close(); taskJob?.cancel() }
    fun progress(n: Float, task: String? = null) {
        val now = System.nanoTime()
        val current = mutable.value
        if (n > 0f && n < 1f && (task == null || task == current.task) && now - lastProgressAt < 50_000_000L) return
        lastProgressAt = now
        mutable.update { it.copy(progress = n.coerceIn(0f, 1f), task = task ?: it.task) }
    }

    @SuppressLint("MissingPermission")
    private fun withClient(action: (ToneClient) -> Unit) {
        val selected = mutable.value.device ?: error("请先选择已配对的耳机")
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        val device = adapter.getRemoteDevice(selected.address)
        val log = File(app.filesDir, "connection.log").apply { writeText("") }
        ToneClient(device) { line -> log.appendText(line + "\n") }.use { c ->
            client = c; c.connect(); mutable.update { it.copy(connected = true) }; action(c)
        }
    }

    fun connect() = runTask("连接耳机", true) {
        withClient { c ->
            val status = c.query()
            mutable.update { it.copy(tone = status, activeKey = if (!status.selected) "default" else it.activeKey.takeIf { key -> key.isNotBlank() && key != "default" } ?: "custom", message = "耳机状态已更新") }
        }
    }

    fun apply(key: String) = runTask(if (key == "default") "恢复默认音效" else "应用音效", true) {
        val bytes = if (key == "default") null else pcm(key)
        withClient { c ->
            val result = if (bytes == null) c.restoreDefault() else c.upload(bytes) { n, text -> progress(n, text) }
            prefs.edit().putString("activeKey", key).apply()
            mutable.update { it.copy(tone = result, activeKey = key, message = if (key == "default") "已恢复默认音效" else "音效已应用，合盖再开盖即可听到") }
        }
    }

    private fun pcm(key: String): ByteArray = if (key.startsWith("preset:")) {
        val id = key.removePrefix("preset:")
        require(presets.any { it.id == id })
        AudioEngine.applyPcmGain(app.assets.open("presets/$id.pcm").use { it.readBytes() }, mutable.value.presetGain)
    } else library.file(mutable.value.library.first { "library:${it.id}" == key }).readBytes()

    fun import(uri: Uri) = runTask("读取音频") {
        val source = engine.import(uri) { progress(it) }
        val old = mutable.value.source
        mutable.update { it.copy(source = source, start = 0, end = source.durationMs, cursor = 0, gain = 1f, page = 1) }
        old?.file?.delete()
    }

    fun selection(start: Long, end: Long) {
        if (mutable.value.busy) return
        val source = mutable.value.source ?: return
        val a = start.coerceIn(0, (source.durationMs - 1).coerceAtLeast(0))
        val b = end.coerceIn(a + 1, source.durationMs)
        stopPlayback()
        mutable.update { it.copy(start = a, end = b, cursor = it.cursor.coerceIn(a, b), exported = null) }
    }
    fun gain(gain: Float) { if (!mutable.value.busy) { stopPlayback(); mutable.update { it.copy(gain = gain.coerceIn(0f, 2f), exported = null) } } }
    fun presetGain(gain: Float) {
        if (mutable.value.busy) return
        val value = gain.coerceIn(0f, 2f)
        if (value == mutable.value.presetGain) return
        stopPlayback()
        val activeKey = mutable.value.activeKey.takeUnless { it.startsWith("preset:") } ?: ""
        prefs.edit().putFloat("presetGain", value).putString("activeKey", activeKey).apply()
        mutable.update { it.copy(presetGain = value, activeKey = activeKey) }
    }
    fun cursor(ms: Long) { stopPlayback(); mutable.update { it.copy(cursor = ms.coerceIn(it.start, it.end)) } }
    fun firstSix() { val s = mutable.value; selection(s.start, min(s.start + 6000, s.source?.durationMs ?: return)) }
    fun all() { selection(0, mutable.value.source?.durationMs ?: return) }

    fun addToLibrary(name: String) = runTask("添加到音效库", libraryWrite = true) {
        val s = mutable.value
        val source = s.source ?: error("请先导入音频或视频")
        val content = engine.tonePcm(source, s.start, s.end, s.gain)
        val sound = library.add(name, content, mutable.value.library)
        mutable.update { it.copy(library = it.library + sound, message = "已添加到我的音效库") }
    }

    fun rename(sound: Sound, name: String) {
        if (name.trim().isEmpty()) return
        editLibrary("音效重命名失败") {
            val sounds = mutable.value.library.map { if (it.id == sound.id) it.copy(name = name.trim().take(80)) else it }
            library.save(sounds); mutable.update { it.copy(library = sounds) }
        }
    }
    fun delete(sound: Sound) {
        if (mutable.value.busy) return
        stopPlayback()
        editLibrary("删除失败") {
            val sounds = mutable.value.library
            library.delete(sound, sounds)
            mutable.update { it.copy(library = sounds.filterNot { s -> s.id == sound.id }) }
        }
    }
    fun move(id: String, delta: Int) {
        editLibrary("排序保存失败") {
            val sounds = mutable.value.library.sortedBy { it.order }.toMutableList()
            val from = sounds.indexOfFirst { it.id == id }
            if (from < 0) return@editLibrary
            val to = (from + delta).coerceIn(0, sounds.lastIndex)
            if (from == to) return@editLibrary
            val item = sounds.removeAt(from); sounds.add(to, item)
            val reordered = sounds.mapIndexed { i, s -> s.copy(order = i) }
            library.save(reordered); mutable.update { it.copy(library = reordered, sort = "手动排序") }
        }
    }

    fun render(kind: String) = runTask("导出音频") {
        val s = mutable.value
        val source = s.source ?: error("请先导入音频或视频")
        val result = engine.export(source, s.start, s.end, s.gain, kind) { progress(it) }
        mutable.update { it.copy(exported = result) }
    }

    fun saveTo(uri: Uri, file: File) = runTask("保存音频") {
        try {
            app.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("无法写入所选位置")
        } finally { file.delete() }
        mutable.update { it.copy(message = "音频已保存") }
    }
    fun discardExport(file: File) { if (file.parentFile?.canonicalFile == File(app.cacheDir, "audio").canonicalFile) file.delete() }
    fun saveSound(uri: Uri, sound: Sound) = runTask("保存音效") {
        val data = library.file(sound).readBytes()
        app.contentResolver.openOutputStream(uri)?.use { out -> out.write(AudioEngine.wavHeader(data.size, 16000, 1)); out.write(data) }
            ?: error("无法写入所选位置")
        mutable.update { it.copy(message = "音效已保存") }
    }

    fun preview(key: String) {
        if (mutable.value.busy) return
        if (mutable.value.playing == key) { stopPlayback(); return }
        playRaw(key, 16000, 1) { pcm(key) }
    }

    private fun playRaw(key: String, rate: Int, channels: Int, load: () -> ByteArray) {
        stopPlayback()
        val generation = playGeneration
        mutable.update { it.copy(playing = key) }
        playJob = viewModelScope.launch {
            try {
                playbackMutex.withLock { runInterruptible(Dispatchers.IO) {
                val data = load()
                val track = createTrack(rate, channels)
                audioTrack = track
                try {
                    checkPlaybackCancelled()
                    track.play()
                    var offset = 0
                    while (offset < data.size) {
                        checkPlaybackCancelled()
                        val count = track.write(data, offset, data.size - offset)
                        check(count > 0) { "无法播放音频（$count）" }; offset += count
                    }
                    awaitPlayback(track, data.size / (channels * 2))
                }
                finally { runCatching { track.stop() }; track.release(); if (audioTrack === track) audioTrack = null }
            } } } catch (_: CancellationException) {} catch (e: Throwable) { if (playGeneration == generation) showError(e.message ?: "试听失败") }
            finally { if (playGeneration == generation) mutable.update { it.copy(playing = null) } }
        }
    }

    fun previewEditor() {
        if (mutable.value.busy) return
        if (mutable.value.playing == "editor") { stopPlayback(); return }
        stopPlayback()
        val generation = playGeneration
        val s = mutable.value
        val source = s.source ?: return
        val from = s.cursor.takeIf { it >= s.start && it < s.end - 20 } ?: s.start
        mutable.update { it.copy(playing = "editor", cursor = from) }
        playJob = viewModelScope.launch {
            try {
                playbackMutex.withLock { runInterruptible(Dispatchers.IO) {
                val channels = min(source.channels, 2)
                val track = createTrack(source.rate, channels)
                audioTrack = track
                val first = from * source.rate / 1000
                val last = s.end * source.rate / 1000
                try {
                    checkPlaybackCancelled()
                    track.play()
                    RandomAccessFile(source.file, "r").use { file ->
                        file.seek(first * source.channels * 2)
                        val raw = ByteArray(4096 * source.channels * 2)
                        val out = ShortArray(4096 * channels)
                        var remaining = last - first
                        while (remaining > 0) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val frames = min(4096L, remaining).toInt()
                            file.readFully(raw, 0, frames * source.channels * 2)
                            repeat(frames) { i -> repeat(channels) { ch ->
                                out[i * channels + ch] = (AudioEngine.shortAt(raw, (i * source.channels + ch) * 2) * s.gain).roundToInt().coerceIn(-32768, 32767).toShort()
                            } }
                            var offset = 0
                            val count = frames * channels
                            while (offset < count) {
                                checkPlaybackCancelled()
                                val written = track.write(out, offset, count - offset)
                                check(written > 0) { "无法播放音频（$written）" }; offset += written
                            }
                            remaining -= frames
                            if (playGeneration == generation) mutable.update { it.copy(cursor = (from + track.playbackHeadPosition.toLong() * 1000 / source.rate).coerceAtMost(s.end)) }
                        }
                        awaitPlayback(track, (last - first).toInt())
                        if (playGeneration == generation) mutable.update { it.copy(cursor = s.end) }
                    }
                } finally { runCatching { track.stop() }; track.release(); if (audioTrack === track) audioTrack = null }
            } } } catch (_: CancellationException) {} catch (e: Throwable) { if (playGeneration == generation) showError(e.message ?: "试听失败") }
            finally { if (playGeneration == generation) mutable.update { it.copy(playing = null) } }
        }
    }

    private fun createTrack(rate: Int, channels: Int): AudioTrack {
        val mask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        return AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(mask).build())
            .setBufferSizeInBytes(maxOf(16384, AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
    }
    private fun awaitPlayback(track: AudioTrack, frames: Int) {
        while (track.playbackHeadPosition < frames) { if (Thread.currentThread().isInterrupted) throw InterruptedException(); Thread.sleep(20) }
    }
    private fun checkPlaybackCancelled() { if (Thread.currentThread().isInterrupted) throw InterruptedException() }
    fun stopPlayback() {
        playGeneration++
        mutable.update { it.copy(playing = null) }
        playJob?.cancel(); playJob = null
        runCatching { audioTrack?.pause(); audioTrack?.stop(); audioTrack?.flush() }
    }
    fun consumeExport() { mutable.update { it.copy(exported = null) } }
    override fun onCleared() { stopPlayback(); client?.close(); TaskService.stop(app); mutable.value.source?.file?.delete(); super.onCleared() }
}
