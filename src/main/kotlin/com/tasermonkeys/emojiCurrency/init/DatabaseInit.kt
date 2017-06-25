package com.tasermonkeys.emojiCurrency.init

import com.heroku.sdk.jdbc.DatabaseUrl
import com.tasermonkeys.emojiCurrency.models.EmojiLedger
import com.tasermonkeys.emojiCurrency.models.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct
import java.sql.DriverManager



@Component
class PrepareTables {

    @PostConstruct
    fun init() {
        var DATABASE_URL = System.getenv("DATABASE_URL")
        DriverManager.registerDriver(org.postgresql.Driver())
        val local = "cedar-14" != System.getenv("STACK")
        val dbUrl: DatabaseUrl = DatabaseUrl.extract(local);
        val driverName = dbUrl.connection.metaData.driverName;
        println("**** ${DATABASE_URL} - ${dbUrl.jdbcUrl()} - ${dbUrl.username()} -- ${dbUrl.password()}}")
        Database.connect(DATABASE_URL, org.postgresql.Driver::class.java.canonicalName, dbUrl.username(), dbUrl.password())
        transaction {
            SchemaUtils.create(Users, EmojiLedger)
        }
    }
}