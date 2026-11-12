package oneringr.huaweipods.modify.update

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GithubRelease(val tag: String, val url: String)

object GithubUpdates {
    const val REPOSITORY = "oneringr/huaweipods-casetone-modify"
    const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"

    fun latest(): GithubRelease? {
        val connection = URL("https://api.github.com/repos/$REPOSITORY/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "CaseTone-Android")
            when (connection.responseCode) {
                404 -> return null
                403, 429 -> throw IOException("GitHub 请求受限，请稍后重试")
                200 -> Unit
                else -> throw IOException("GitHub 暂时无法连接，请稍后重试")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            versionParts(tag)
            val page = json.getString("html_url")
            val url = URL(page)
            require(url.protocol == "https" && url.host == "github.com" && url.path.startsWith("/$REPOSITORY/releases/"))
            return GithubRelease(tag, page)
        } catch (e: IllegalArgumentException) { throw IOException("GitHub 发布版本信息无法识别", e) }
        catch (e: org.json.JSONException) { throw IOException("GitHub 发布版本信息无法识别", e) }
        catch (e: java.net.SocketTimeoutException) { throw IOException("检查更新超时，请检查网络后重试", e) }
        catch (e: IOException) {
            if (e.message?.startsWith("GitHub") == true || e.message?.startsWith("检查更新") == true) throw e
            throw IOException("无法连接 GitHub，请检查网络后重试", e)
        } finally { connection.disconnect() }
    }

    fun isNewer(candidate: String, installed: String): Boolean {
        val remote = versionParts(candidate)
        val local = versionParts(installed)
        for (i in 0 until maxOf(remote.size, local.size)) {
            val comparison = (remote.getOrElse(i) { 0 }).compareTo(local.getOrElse(i) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    private fun versionParts(tag: String): List<Int> {
        val version = tag.trim().removePrefix("v").removePrefix("V")
        require(version.matches(Regex("[0-9]+(?:\\.[0-9]+){0,3}")))
        return version.split('.').map { it.toInt() }
    }
}
