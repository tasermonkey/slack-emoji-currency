package com.tasermonkeys.emojiCurrency.init

import com.heroku.sdk.jdbc.DatabaseUrl
import com.tasermonkeys.emojiCurrency.models.EmojiLedger
import com.tasermonkeys.emojiCurrency.models.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class PrepareTables {

    @PostConstruct
    fun init() {
        val dev = System.getenv("DATABASE_URL") == null
        if (dev) {
            println("************* Running with dev's H2 database *************")
            Class.forName("org.h2.Driver")
            Database.connect("jdbc:h2:./application.db", driver = "org.h2.Driver")
        } else {
            val local = "cedar-14" != System.getenv("STACK")
            val dbUrl: DatabaseUrl = DatabaseUrl.extract(local);
            val driverName = dbUrl.connection.metaData.driverName;
            Database.connect(dbUrl.jdbcUrl(), driverName)
        }
        transaction {
            SchemaUtils.create(Users, EmojiLedger)
        }
    }
}