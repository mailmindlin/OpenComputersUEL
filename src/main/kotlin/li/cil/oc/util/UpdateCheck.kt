package li.cil.oc.util

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.versioning.ComparableVersion
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool

object UpdateCheck {
    private val releasesUrl = URL("https://api.github.com/repos/MightyPirates/OpenComputers/releases")

    @JvmField
    var info: CompletableFuture<Release?> = CompletableFuture.supplyAsync(
        { initialize() },
        ForkJoinPool.commonPool()
    )

    private fun initialize(): Release? {
        // Keep the version template split up so it's not replaced with the actual version...
        if (Settings.get.updateCheck && OpenComputers.Version != ("@" + "VERSION" + "@")) {
            try {
                OpenComputers.log.info("Starting OpenComputers version check.")
                val reader = JsonReader(InputStreamReader(releasesUrl.openStream()))
                reader.beginArray()
                val candidates = mutableListOf<Release>()
                while (reader.hasNext()) {
                    val release: Release = Gson().fromJson(reader, Release::class.java)
                    if (!release.prerelease) {
                        // Handle the newer version format: mcVersion/release
                        var versionMatch = true
                        if (release.tag_name.contains("/")) {
                            val tagNameParts = release.tag_name.split("/", limit = 2)
                            if (tagNameParts.size >= 2) {
                                release.tag_name = tagNameParts[1]
                                versionMatch = OpenComputers.McVersion == tagNameParts[0]
                            }
                        }
                        if (versionMatch) {
                            candidates.add(release)
                        }
                    }
                }
                reader.endArray()
                if (candidates.isNotEmpty()) {
                    val latest = candidates.maxByOrNull { release -> ComparableVersion(release.tag_name.removePrefix("v")) }!!
                    val remoteVersion = ComparableVersion(latest.tag_name.removePrefix("v"))
                    val localVersion = ComparableVersion(Loader.instance().indexedModList[OpenComputers.ID]!!.version)
                    if (remoteVersion.compareTo(localVersion) > 0) {
                        OpenComputers.log.info("A newer version of OpenComputers is available: ${latest.tag_name}.")
                        return latest
                    }
                }
                OpenComputers.log.info("Running the latest OpenComputers version.")
            } catch (t: Throwable) {
                OpenComputers.log.warn("Update check for OpenComputers failed.", t)
            }
        }
        return null
    }

    class Release {
        @JvmField
        var tag_name: String = ""
        @JvmField
        var body: String = ""
        @JvmField
        var prerelease: Boolean = false
    }
}
