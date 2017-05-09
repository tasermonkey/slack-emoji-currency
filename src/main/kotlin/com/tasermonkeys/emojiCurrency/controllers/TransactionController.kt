package com.tasermonkeys.emojiCurrency.controllers

import com.tasermonkeys.emojiCurrency.models.EmojiEntry
import com.tasermonkeys.emojiCurrency.models.EmojiLedger
import com.tasermonkeys.emojiCurrency.models.User
import com.tasermonkeys.emojiCurrency.models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*


@Suppress("UNUSED_PARAMETER")
@RestController
@RequestMapping("/transaction")
class TransactionController {
    @PostMapping(
            consumes = arrayOf(MediaType.MULTIPART_FORM_DATA_VALUE),
            produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun newTransaction(
            @RequestParam(value = "token") token: String,
            @RequestParam(value = "team_id") teamId: String,
            @RequestParam(value = "team_domain") teamDomain: String,
            @RequestParam(value = "channel_id") channelId: String,
            @RequestParam(value = "channel_name") channelName: String,
            @RequestParam(value = "user_id") userId: String,
            @RequestParam(value = "user_name") userName: String,
            @RequestParam(value = "command") command: String,
            @RequestParam(value = "text") text: String,
            @RequestParam(value = "response_url") response_url: String
    ): Map<String, Any> {
        val parsedText = parseTextForInput(text)
        return transaction {
            if (User.find({ Users.name eq userName }).empty()) {
                User.new {
                    name = userName
                }
            }
            val toUser = parsedText["to"]!!
            if (User.find({ Users.name eq toUser }).empty()) {
                User.new {
                    name = toUser
                }
            }
            val lp1 = if (userName > toUser) userName else toUser
            val lp2 = if (userName > toUser) toUser else userName
            val rawAmount = parsedText[EmojiEntry::amount.name]!!.toInt()
            val calcAmount = if (userName > toUser) -rawAmount else rawAmount
            val lEmoji = parsedText[EmojiEntry::emoji.name]!!

            val existing = EmojiLedger.slice(EmojiLedger.id)
                    .select((EmojiLedger.p1 eq lp1) and (EmojiLedger.p2 eq lp2) and (EmojiLedger.emoji eq lEmoji))
                    .firstOrNull()
            if (existing != null) {
                val currentId = existing[EmojiLedger.id]
                EmojiLedger.update({ EmojiLedger.id eq currentId }) {
                    it.update(EmojiLedger.amount, EmojiLedger.amount + intLiteral(calcAmount))
                }
            } else {
                EmojiEntry.new {
                    p1 = lp1
                    p2 = lp2
                    emoji = lEmoji
                    amount = rawAmount
                }
            }
            mapOf("text" to "You have given ${rawAmount} ${lEmoji} to ${toUser}")
        }
    }

    @GetMapping(value = "/all", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun listTransactions(): List<Map<String, Any>> {
        return transaction {
            EmojiEntry.all().limit(100).notForUpdate().toList().map(EmojiEntry::asMap)
        }
    }

    @PostMapping(
            value = "/summary",
            consumes = arrayOf(MediaType.MULTIPART_FORM_DATA_VALUE),
            produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun summary(@RequestParam(value = "token") token: String,
                @RequestParam(value = "team_id") teamId: String,
                @RequestParam(value = "team_domain") teamDomain: String,
                @RequestParam(value = "channel_id") channelId: String,
                @RequestParam(value = "channel_name") channelName: String,
                @RequestParam(value = "user_id") userId: String,
                @RequestParam(value = "user_name") userName: String,
                @RequestParam(value = "command") command: String,
                @RequestParam(value = "text") text: String,
                @RequestParam(value = "response_url") response_url: String): Map<String, Any> {
        return transaction {
            val summary = EmojiLedger
                    .slice(EmojiLedger.p1, EmojiLedger.p2, EmojiLedger.emoji, EmojiLedger.amount.sum())
                    .select({ (EmojiLedger.p1 eq userName) or (EmojiLedger.p2 eq userName) })
                    .groupBy(EmojiLedger.p1, EmojiLedger.p2, EmojiLedger.emoji)
                    .having { EmojiLedger.amount.sum() neq 0 }
                    .toList()
                    .map {
                        val currentIsFirst = it[EmojiLedger.p1] == userName
                        val amt = it[EmojiLedger.amount.sum()]!!
                        mapOf(
                                "other" to if (currentIsFirst) it[EmojiLedger.p2] else it[EmojiLedger.p1],
                                "emoji" to it[EmojiLedger.emoji],
                                "amount" to if (currentIsFirst) amt else -amt
                        )
                    }
                    .groupBy({ it["other"] })

            val summaryText = summary
                    .map {
                        val owes = it.value.filter { (it["amount"] as Int) > 0 }
                        val strBuilder = StringBuilder()
                        if (owes.isNotEmpty()) {
                            strBuilder.append("${it.key} owes you ${owes.joinToString(", ") { "${it["amount"]} ${it["emoji"]}" }
                            }.  ")
                        }
                        val owed = it.value.filter { (it["amount"] as Int) < 0 }
                        if (owed.isNotEmpty()) {
                            strBuilder.append("You owe ${it.key} ${owed.joinToString(", ") { "${-(it["amount"] as Int)} ${it["emoji"]}" }
                            }.")
                        }
                        strBuilder.trim().toString()
                    }
                    .joinToString("\n")
            mapOf("text" to summaryText)
        }
    }

    private fun parseTextForInput(inputText: String): Map<String, String> {
        val tokenized = inputText.split(delimiters = " ")
        if (tokenized.size != 2 && tokenized.size != 3) {
            throw IllegalArgumentException("syntax toUserName emoji [amount]")
        }
        if (tokenized.size == 3 && tokenized[2].toIntOrNull() == null) {
            throw IllegalArgumentException("syntax toUserName emoji [amount]")
        }
        return mapOf(
                "to" to tokenized[0],
                EmojiEntry::emoji.name to tokenized[1],
                EmojiEntry::amount.name to tokenized.getOrElse(2, { i -> "1" })
        )
    }
}

