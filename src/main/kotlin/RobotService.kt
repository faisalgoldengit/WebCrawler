package org.example

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.example.data.RedisClient
import org.example.data.RobotPolicy
import org.example.data.RobotTXTparser

class RobotsTxtService(
    private val redis: RedisClient,
    private val httpClient: HttpClient, // your Ktor/OkHttp client
    private val userAgent: String = "MyCrawlerBot"
) {
    private val parser = RobotTXTparser()
    fun isPathAllowed(policy: RobotPolicy, path: String): Boolean {
        // Convert robots.txt pattern to regex: * = wildcard, $ = end anchor
        fun toRegex(pattern: String): Regex {
            val escaped = Regex.escape(pattern)
                .replace("\\*", ".*")
                .let { if (pattern.endsWith("$")) it.removeSuffix("\\\$") + "$" else it }
            return Regex("^$escaped")
        }

        val matches = policy.rules.filter { toRegex(it.path).containsMatchIn(path) }
        if (matches.isEmpty()) return true // no matching rule = allowed by default

        // Longest path match wins (per spec); Allow wins ties over Disallow
        val best = matches.maxByOrNull { it.path.length }!!
        return best.allow
    }
    suspend fun isAllowed(url: String): Boolean {
        val uri = java.net.URI(url)
        val domain = uri.host
        val path = uri.rawPath.ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")

        val content = redis.getCachedRobotsTxt(domain) ?: fetchAndCache(domain)
        val policy = parser.parse(content, userAgent)
        return isPathAllowed(policy, path)
    }

    suspend fun getCrawlDelayMs(domain: String): Long? {
        val content = redis.getCachedRobotsTxt(domain) ?: fetchAndCache(domain)
        return parser.parse(content, userAgent).crawlDelayMs
    }

    private suspend fun fetchAndCache(domain: String): String {
        val content = try {
            val resp = httpClient.get("https://$domain/robots.txt")
            when (resp.status.value) {
                in 200..299 -> resp.bodyAsText()
                in 400..499 -> ""          // 4xx -> no robots.txt -> allow everything
                else -> null                // 5xx / network error -> handled below
            }
        } catch (e: Exception) {
            null
        }

        // On server error / timeout, spec says: be conservative.
        // Common practice: treat as temporarily disallowed with a SHORT TTL, so you retry soon
        // rather than caching "allow all" for 24h based on a fluke.
        return if (content != null) {
            redis.cacheRobotsTxt(domain, content, ttl = 86400)
            content
        } else {
            redis.cacheRobotsTxt(domain, "", ttl = 300) // short TTL retry
            ""
        }
    }
}