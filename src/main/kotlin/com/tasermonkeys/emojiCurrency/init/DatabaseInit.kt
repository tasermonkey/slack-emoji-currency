package com.tasermonkeys.emojiCurrency.init

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
        Class.forName("org.h2.Driver");
//        val db = Database.connect(
//                "jdbc:sqlite:application.db", driver = "org.sqlite.JDBC")
        val db = Database.connect("jdbc:h2:./application.db", driver = "org.h2.Driver")
        //println("Database name = ${db.name}, ${db.vendor}")

        transaction {
            SchemaUtils.create(Users, EmojiLedger)
        }
    }
}