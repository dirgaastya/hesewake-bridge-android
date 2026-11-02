package id.hesewake.mediabridge

import java.net.URI
import java.util.Locale

/** Only recognize image CDN URLs provided by a media session. */
internal object ArtworkUrl {
    fun higherResolution(value: String?): String? {
        if (value == null || value.length > 4096) return null
        val parsed = runCatching { URI(value) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase(Locale.ROOT) ?: return null
        if (parsed.scheme != "https" || parsed.rawUserInfo != null || (parsed.port != -1 && parsed.port != 443)) return null
        if (!(host == "ggpht.com" || host.endsWith(".ggpht.com") || host == "googleusercontent.com" || host.endsWith(".googleusercontent.com"))) return null
        val path = parsed.rawPath?.takeIf { it.isNotEmpty() } ?: return null
        val sized = path.replace(Regex("w[0-9]+-h[0-9]+"), "w1200-h1200").replace(Regex("=s[0-9]+"), "=s1200")
        val start = value.indexOf(path, value.indexOf("://") + 3)
        if (start < 0) return null
        return value.substring(0, start) + sized + value.substring(start + path.length)
    }
}
