package org.example.data

data class RobotsRule(val path:String,val allow:Boolean)

data class RobotPolicy(
    val rules:List<RobotsRule>,
    val crawlDelayMs:Long?,
    val sitemaps:List<String>
)


class RobotTXTparser{
    fun parse(content:String,userAgent:String): RobotPolicy{
        val lines = content.lines().map { it.trim() }
        val blocks = mutableListOf<Pair<List<String>, MutableList<RobotsRule>>>()
        var currentAgents = mutableListOf<String>()
        var currentRules = mutableListOf<RobotsRule>()
        var crawlDelay: Long? = null
        val sitemaps = mutableListOf<String>()

        fun flush(){
            if(currentAgents.isNotEmpty()){blocks.add(currentAgents to currentRules)}
            currentAgents = mutableListOf()
            currentRules = mutableListOf()
        }

        for(raw in lines) {
            val line = raw.substringBefore("#").trim()
            if (line.isEmpty()) continue

            val (key, value) = line.split(":", limit = 2).map { it.trim() }
                .let { if (it.size == 2) it[0] to it[1] else return@let null } ?: continue
            when (key.lowercase()) {
                "user-agent" -> {
                    // A new User-agent after rules started a new block
                    if (currentRules.isNotEmpty()) flush()
                    currentAgents.add(value.lowercase())
                }

                "disallow" -> if (value.isNotEmpty()) currentRules.add(RobotsRule(value, allow = false))
                else currentRules.add(RobotsRule("", allow = true)) // empty Disallow = allow all
                "allow" -> currentRules.add(RobotsRule(value, allow = true))
                "crawl-delay" -> crawlDelay = value.toDoubleOrNull()?.let { (it * 1000).toLong() }
                "sitemap" -> sitemaps.add(value)
            }
        }
        flush()
        val exact = blocks.firstOrNull { it.first.contains(userAgent.lowercase()) }
        val wildcard = blocks.firstOrNull { it.first.contains("*") }
        val chosen = exact ?: wildcard

        return RobotPolicy(
            rules = chosen?.second?:emptyList(),
            crawlDelayMs = crawlDelay,
            sitemaps=sitemaps
        )

    }
}