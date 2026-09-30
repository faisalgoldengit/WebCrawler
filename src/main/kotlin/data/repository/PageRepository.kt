package org.example.data.repository

import org.example.data.db.DatabaseFactory.dbQuery
import org.example.data.db.Pages
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.upsert

object PageRepository {
    suspend fun save(url:String,title:String,content:String)=dbQuery{
        Pages.upsert(Pages.url){
            it[this.url] = url
            it[this.title] = title
            it[this.content] = content
        }
    }


    suspend fun search(query:String,limit:Int=10):List<String> =dbQuery{
        val sql = """
            SELECT url
            FROM pages, websearch_to_tsquery('english', ?) AS q
            WHERE search_vector @@ q
            ORDER BY ts_rank(search_vector, q) DESC
            LIMIT ?
        """.trimIndent()
        TransactionManager.current().exec(
            sql,
            listOf(TextColumnType() to query, IntegerColumnType() to limit)
        ) { rs ->
            buildList { while (rs.next()) add(rs.getString("url")) }
        } ?: emptyList()
    }
}