package oneringr.huaweipods.modify

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import oneringr.huaweipods.modify.bluetooth.ToneClient
import java.io.File
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import oneringr.huaweipods.modify.data.Sound
import oneringr.huaweipods.modify.ui.AppUi

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()
    private var exportPath: String? = null
    private var exportSoundId: String? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::import) }
    private val bluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.refreshDevices() else model.showError("连接耳机需要允许附近设备权限。音频处理与音效库仍可使用。")
    }
    private val saveWav = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri -> saved(uri) }
    private val saveM4a = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { uri -> saved(uri) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        exportPath = savedInstanceState?.getString("exportPath")
        exportSoundId = savedInstanceState?.getString("exportSoundId")
        if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) model.refreshDevices()
        val visibleState = model.state.flowWithLifecycle(lifecycle)
        val initialState = model.state.value
        setContent {
            val state by visibleState.collectAsState(initial = initialState)
            AppUi(model, state,
                import = { model.stopPlayback(); picker.launch(arrayOf("audio/*", "video/*")) },
                requestBluetooth = {
                    if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) bluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    else model.refreshDevices()
                },
                exportSound = { sound -> exportSoundId = sound.id; exportPath = null; saveWav.launch(sound.name + ".wav") },
                exportReady = { file, name -> exportPath = file.absolutePath; exportSoundId = null; if (file.extension == "wav") saveWav.launch(name) else saveM4a.launch(name) })
        }
        if (BuildConfig.DEBUG && intent.hasExtra("probe")) probe(intent)
    }

    private fun saved(uri: Uri?) {
        if (uri != null) {
            exportSoundId?.let { id -> model.state.value.library.find { it.id == id }?.let { model.saveSound(uri, it) } }
                ?: exportPath?.let { path -> model.saveTo(uri, File(path)) }
        } else exportPath?.let { model.discardExport(File(it)) }
        exportSoundId = null; exportPath = null
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportPath", exportPath); outState.putString("exportSoundId", exportSoundId)
        super.onSaveInstanceState(outState)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun probe(intent: Intent) {
        lifecycleScope.launch(Dispatchers.IO) {
            val log = File(filesDir, "probe.log")
            log.writeText("START\n")
            fun trace(s: String) { log.appendText(s + "\n"); android.util.Log.i("ToneProbe", s) }
            try {
                val adapter = getSystemService(BluetoothManager::class.java).adapter
                val address = intent.getStringExtra("address")
                val device = if (address != null) adapter.getRemoteDevice(address) else adapter.bondedDevices.first()
                ToneClient(device, ::trace).use { client ->
                    client.connect()
                    trace("STATUS ${client.query()}")
                    when(intent.getStringExtra("probe")) {
                        "upload" -> trace("RESULT ${client.upload(assets.open("test-tone.pcm").use { it.readBytes() }) { n, s -> trace("PROGRESS $n $s") }}")
                        "default" -> trace("RESULT ${client.restoreDefault()}")
                    }
                }
                trace("SUCCESS")
            } catch (e: Throwable) { trace("FAIL ${e.stackTraceToString()}") }
        }
    }
}
