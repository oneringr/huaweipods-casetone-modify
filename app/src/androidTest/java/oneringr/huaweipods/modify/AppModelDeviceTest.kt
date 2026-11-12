package oneringr.huaweipods.modify

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.StrictMode
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import oneringr.huaweipods.modify.bluetooth.ToneClient
import oneringr.huaweipods.modify.data.SoundLibrary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class AppModelDeviceTest {
    @Test fun libraryEditsStayOffMainThreadAndRapidPreviewsFinish() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val id = UUID.randomUUID().toString()
        val folder = File(context.cacheDir, "model-test-$id").apply { mkdirs() }
        val app = object : Application() {
            init { attachBaseContext(context) }
            override fun getFilesDir(): File = folder
            override fun getCacheDir(): File = folder
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = context.getSharedPreferences("test-$id-$name", mode)
        }
        val library = SoundLibrary(app)
        val first = library.add("第一段", ByteArray(32000), emptyList())
        val second = library.add("第二段", ByteArray(32000), listOf(first))
        val store = ViewModelStore()
        lateinit var model: AppModel
        instrumentation.runOnMainSync { model = AppModel(app); store.put("test", model) }
        fun await(check: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 5000
            while (!check() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
            assertTrue("state=${model.state.value}", check())
        }
        try {
            await { model.state.value.library.size == 2 }
            repeat(8) { i ->
                instrumentation.runOnMainSync { model.preview("library:${if (i % 2 == 0) first.id else second.id}") }
                SystemClock.sleep(25)
            }
            instrumentation.runOnMainSync { model.stopPlayback(); model.preview("library:${second.id}") }
            await { model.state.value.playing == null }
            assertNull(model.state.value.error)

            val violations = CopyOnWriteArrayList<String>()
            instrumentation.runOnMainSync {
                val old = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder(old).detectDiskWrites()
                    .penaltyListener(Executor { it.run() }) { violations.add(it.toString()) }.build())
                try {
                    model.rename(second, "已重命名")
                    model.move(second.id, -1)
                    model.delete(first)
                } finally { StrictMode.setThreadPolicy(old) }
            }
            await { model.state.value.library.size == 1 && model.state.value.library.single().name == "已重命名" }
            assertTrue(violations.toString(), violations.isEmpty())
            assertEquals(model.state.value.library, library.load())
            instrumentation.runOnMainSync { model.delete(second); model.move("missing", 1) }
            await { model.state.value.library.isEmpty() }
            assertNull(model.state.value.error)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            library.file(first).delete(); library.file(second).delete()
            File(folder, "library/library.json").delete()
            context.deleteSharedPreferences("test-$id-settings")
        }
    }

    @Test fun closingClientPreventsAnotherConnectionAttempt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
        val client = ToneClient(adapter.getRemoteDevice("00:11:22:33:44:55"))
        client.close()
        assertThrows(InterruptedException::class.java) { client.connect() }
    }
}
