package industries.hannah.pixelblaze

import com.google.gson.Gson
import com.google.gson.JsonArray
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.time.Instant


data class Discovered(
    val id: String,
    val name: String,
    val lastSeen: Instant,
    val version: String,
    val remoteIp: String,
    val localIp: String,
    val boardType: String,
    val arch: String,
)

class Discovery(private val httpClient: HttpClient = HttpClient()) {
    private val gson = Gson()

    suspend fun discoverLocalPixelblazes(): List<Discovered>? {
        val response = httpClient.get("http://discover.electromage.com/discover")
        return if (response.status == HttpStatusCode.OK) {
            val body = response.bodyAsText()
            try {
                val arr = gson.fromJson(body, JsonArray::class.java)
                arr.map { ele ->
                    val json = ele.asJsonObject
                    Discovered(
                        id = json["id"].asString,
                        name = json["name"].asString,
                        lastSeen = Instant.parse(json["createdAt"].asString),
                        version = json["version"].asString,
                        remoteIp = json["ip"].asString,
                        localIp = json["localIp"].asString,
                        boardType = json["boardType"].asString,
                        arch = json["arch"].asString,
                    )
                }.toList()
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }
    }
}