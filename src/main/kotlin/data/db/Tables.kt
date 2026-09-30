package org.example.data.db

import org.jetbrains.exposed.dao.id.LongIdTable

object Pages: LongIdTable("pages"){
    val url = text("url")
    val title = text("title")
    val content = text("content")

}