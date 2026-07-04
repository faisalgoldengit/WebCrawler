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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

fun main() {

    val client = HttpClient(CIO)
    runBlocking {
        LocalCrawler(client)
    }
    client.close()

}


suspend fun LocalCrawler(client: HttpClient)= coroutineScope{

        val urlQueue = Channel<String>(500)
        val start = "https://quotes.toscrape.com/"
        val visited = ConcurrentHashMap.newKeySet<String>()
        urlQueue.send(start)
        visited.add(start)
        val TotalWorkers = 10
        repeat(TotalWorkers) {workerid->
            this.launch(Dispatchers.IO){
                for(url in urlQueue){
                    val parsedUrls = Parser(url,client)
                    parsedUrls.forEach {
                        if(visited.add(it)){
                            urlQueue.send(it)
                            println(it)
                        }

                    }
                }
            }
        }

}

suspend fun Parser(url:String,client: HttpClient):List<String>{
    return try {
        client.prepareGet(url) {
            header(HttpHeaders.Accept, "text/html")
        }.execute() { response ->

            if (response.status != HttpStatusCode.OK){ return@execute emptyList<String>()}
            val contentType = response.contentType()
            val finalUrl = response.request.url.toString()
            if (contentType == null || !contentType.match(ContentType.Text.Html)) {
                return@execute emptyList<String>()
            }

            val htmlstring = response.bodyAsText()
            withContext(Dispatchers.Default){
                val doc = Jsoup.parse(htmlstring,finalUrl)
                doc.select("a[href]").map { it.attr("abs:href") }.filter{it.isNotEmpty()}
            }


        }
    }
    catch (e: Exception){
        emptyList()
    }

}