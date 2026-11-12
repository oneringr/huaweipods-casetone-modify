package oneringr.huaweipods.modify.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Sound(val id: String, val name: String, val fileName: String, val created: Long, val order: Int, val bytes: Int) {
    val durationMs get() = bytes * 1000L / 32000
}

class SoundLibrary(context: Context) {
    private val folder = File(context.filesDir, "library").apply { mkdirs() }
    private val index = AtomicFile(File(folder, "library.json"))
    fun load(): List<Sound> {
        if (!index.baseFile.exists()) return emptyList()
        val data = JSONArray(index.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        return (0 until data.length()).map { i ->
            val s = data.getJSONObject(i)
            Sound(s.getString("id"), s.getString("name"), s.getString("file"), s.getLong("created"), s.getInt("order"), s.getInt("bytes"))
        }.filter { file(it).isFile }.sortedBy { it.order }
    }
    fun save(sounds: List<Sound>) {
        val data = JSONArray()
        sounds.forEach { s -> data.put(JSONObject().put("id", s.id).put("name", s.name).put("file", s.fileName).put("created", s.created).put("order", s.order).put("bytes", s.bytes)) }
        val out = index.startWrite()
        try { out.write(data.toString().toByteArray()); index.finishWrite(out) } catch (e: Throwable) { index.failWrite(out); throw e }
    }
    fun add(name: String, pcm: ByteArray, sounds: List<Sound>): Sound {
        require(name.trim().isNotEmpty() && pcm.isNotEmpty() && pcm.size <= 192000)
        val id = UUID.randomUUID().toString()
        val sound = Sound(id, name.trim().take(80), "$id.pcm", System.currentTimeMillis(), sounds.size, pcm.size)
        file(sound).writeBytes(pcm)
        try { save(sounds + sound) } catch (e: Throwable) { file(sound).delete(); throw e }
        return sound
    }
    fun file(sound: Sound): File {
        require(sound.fileName.matches(Regex("[a-f0-9-]+\\.pcm")))
        return File(folder, sound.fileName)
    }
    fun delete(sound: Sound, sounds: List<Sound>) {
        save(sounds.filterNot { it.id == sound.id })
        file(sound).delete()
    }
}
