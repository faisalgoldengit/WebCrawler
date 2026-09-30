package org.example.data.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object DatabaseFactory {
    lateinit var db: Database
        private set

    fun init(jdbcUrl: String, user: String, password: String, maxPool: Int = 20) {
        val ds = HikariDataSource(HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            this.driverClassName = "org.postgresql.Driver"
            this.maximumPoolSize = maxPool
            this.isAutoCommit = false
        })

        // Run pending migrations before anyone else touches the DB.
        Flyway.configure().dataSource(ds).load().migrate()

        db = Database.connect(ds)
    }

    // Wrap suspend functions with this. It runs the block in a real DB transaction
    // on the IO dispatcher, so it doesn't block the coroutine's event thread.
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db) { block() }
}