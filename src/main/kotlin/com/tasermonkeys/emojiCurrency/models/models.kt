package com.tasermonkeys.emojiCurrency.models

import org.jetbrains.exposed.dao.*


object Users : IntIdTable() {
    val name = varchar("name", length = 255).uniqueIndex() // Column<String>
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    var name by Users.name
}


object EmojiLedger : IntIdTable() {
    val p1 = (varchar("p1", length = 255) references Users.name).index()
    val p2 = (varchar("p2", length = 255) references Users.name).index()
    val emoji = varchar("emoji", length = 64)
    val amount = integer("amount").default(1)

    init {
        index(true, p1, p2, emoji)
    }
}

class EmojiEntry(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EmojiEntry>(EmojiLedger)

    var p1 by EmojiLedger.p1
    var p2 by EmojiLedger.p2
    var emoji by EmojiLedger.emoji
    var amount by EmojiLedger.amount


    fun asMap() = mapOf(
            EmojiEntry::p1.name to p1,
            EmojiEntry::p2.name to p2,
            EmojiEntry::emoji.name to emoji,
            EmojiEntry::amount.name to amount
    )
}
