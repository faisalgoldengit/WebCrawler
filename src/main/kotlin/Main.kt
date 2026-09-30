package org.example
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.example.data.RedisClient
import org.example.data.db.DatabaseFactory
import org.example.data.repository.PageRepository
import org.jsoup.Jsoup
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

fun main() {


    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))

    DatabaseFactory.init("jdbc:postgresql://localhost:5432/crawler", "postgres", "postgres")
    val client = HttpClient(CIO)
    val redisClient = RedisClient()
    val robotService = RobotsTxtService(redisClient,client)
    runBlocking {

        LocalCrawler(client, redisClient,robotService)
        PageRepository.search("Albert Einstein").forEach {
            println("Result:$it")
        }
    }
    client.close()
    redisClient.close()

}

data class Parsed_pages(val title:String,val content:String,val links:List<String>)
suspend fun LocalCrawler(client: HttpClient,redisClient: RedisClient,robotService: RobotsTxtService)= coroutineScope{

        val queueKey = "crawler:queue"
        val start = "https://quotes.toscrape.com/"
        val startDomain = java.net.URI(start).host
        val TotalWorkers = 10
        // Clear state from previous runs FIRST, then seed the start URL.
        redisClient.delete(queueKey)
        redisClient.delete("visited:urls")
        if(redisClient.markVisitedIfNew(start)){
            redisClient.pushUrl(queueKey,start)
        }

        repeat(TotalWorkers) {workerid->
            this.launch(Dispatchers.IO){
                while(redisClient.VisitedSize()<1000){
                    val url = redisClient.popUrlBlocking(queueKey, timeout = 10)
                    if(url == null){break}   // queue empty for 10s -> crawl is done, let the worker exit
                    val domain = java.net.URI(url).host
                    if(domain !=startDomain ){continue}
                    val crawlDelay = robotService.getCrawlDelayMs(domain)?:0L
                    if(!redisClient.isDomainAvailable(domain,crawlDelay)){
                        redisClient.pushUrl(queueKey,url)
                        continue
                    }
                    val parsedpage = Parser(url,client)?:continue

                    try {
                        PageRepository.save(url,parsedpage.title,parsedpage.content)
                    }
                    catch (e:Exception){
                        println("Database error")
                    }

                    parsedpage.links.forEach {
                        // Skip mailto:, javascript:, malformed hrefs etc. instead of crashing the whole crawler
                        val host = try { java.net.URI(it).takeIf { u -> u.scheme == "http" || u.scheme == "https" }?.host } catch (e: Exception) { null }
                        if(host == startDomain && robotService.isAllowed(it)) {
                            if (redisClient.markVisitedIfNew(it)) {

                                redisClient.pushUrl(queueKey, it)
                                println(it)
                            }
                        }
                    }
                }
            }
        }
}

suspend fun Parser(url:String,client: HttpClient): Parsed_pages?{
    return try {
        client.prepareGet(url) {
            header(HttpHeaders.Accept, "text/html")
        }.execute() { response ->

            if (response.status != HttpStatusCode.OK){ return@execute null}
            val contentType = response.contentType()
            val finalUrl = response.request.url.toString()
            if (contentType == null || !contentType.match(ContentType.Text.Html)) {
                return@execute null
            }

            val htmlstring = response.bodyAsText()
            withContext(Dispatchers.Default){
                val doc = Jsoup.parse(htmlstring,finalUrl)
                Parsed_pages(
                    title = doc.title(),
                    content = doc.body().text().take(100_000),
                    links=doc.select("a[href]").map { it.attr("abs:href") }.filter{it.isNotEmpty()}
                )

            }


        }
    }
    catch (e: Exception){
        null
    }

}