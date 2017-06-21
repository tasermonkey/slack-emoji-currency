package com.tasermonkeys.emojiCurrency.controllers

import com.tasermonkeys.emojiCurrency.configuration.AppProps
import com.tasermonkeys.emojiCurrency.exceptions.BadToken
import com.tasermonkeys.emojiCurrency.models.EmojiEntry
import com.tasermonkeys.emojiCurrency.models.EmojiLedger
import com.tasermonkeys.emojiCurrency.models.User
import com.tasermonkeys.emojiCurrency.models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*


val userMentionRegex = Regex("""<@(\w+)\|\w+>""")

@Suppress("UNUSED_PARAMETER")
@RestController
@RequestMapping("/transaction")
class TransactionController(appProps: AppProps) {
    val expectedToken = appProps.expectedToken
    /**
     * Sample post
     * ```
     * http POST :8080/transaction token=blah team_id=foo team_domain=bar channel_id=baz channel_name=general user_id=stapljd1 user_name=stapljd1 command=emoji "text=josh :coffee: 1"
     * ```
     */
    @PostMapping(
            consumes = arrayOf(MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE),
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
        println(mapOf("token" to token,
                "team_id" to teamId,
                "team_domain" to teamDomain,
                "channel_id" to channelId,
                "channel_name" to channelName,
                "user_id" to userId,
                "user_name" to userName,
                "command" to command,
                "text" to text,
                "response_url" to response_url
        ))
        if (expectedToken != "none" && expectedToken != token) {
            throw BadToken()
        }
        val parsedText = parseTextForInput(text)
        return transaction {
            if (User.find({ Users.name eq userId }).empty()) {
                User.new {
                    name = userId
                }
            }
            val toUser = parsedText["to"]!!
            if (User.find({ Users.name eq toUser }).empty()) {
                User.new {
                    name = toUser
                }
            }
            val lp1 = if (userId >= toUser) userId else toUser
            val lp2 = if (userId > toUser) toUser else userId
            val rawAmount = parsedText[EmojiEntry::amount.name]!!.toInt()
            val calcAmount = if (userId >= toUser) -rawAmount else rawAmount
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
                    amount = calcAmount
                }
            }
            mapOf("response_type" to "in_channel",
                    "text" to "<@${userId}> have given ${rawAmount} ${lEmoji} to <@${toUser}>")
        }
    }

    @GetMapping(value = "/all", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun listTransactions(): List<Map<String, Any>> {
        return transaction {
            EmojiEntry
                    .find(EmojiLedger.amount neq 0)
                    .limit(100)
                    .notForUpdate()
                    .toList().map(EmojiEntry::asMap)
        }
    }

    @PostMapping(
            value = "/summary",
            consumes = arrayOf(MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE),
            produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun summary(@RequestParam(value = "token") token: String,
                @RequestParam(value = "team_id") teamId: String,
                @RequestParam(value = "team_domain") teamDomain: String,
                @RequestParam(value = "channel_id") channelId: String,
                @RequestParam(value = "channel_name") channelName: String,
                @RequestParam(value = "user_id") userId: String,
                @RequestParam(value = "user_name") userName: String,
                @RequestParam(value = "command") command: String,
                @RequestParam(value = "text", required = false, defaultValue = "") text: String,
                @RequestParam(value = "response_url") response_url: String): Map<String, Any> {
        if (expectedToken != "none" && expectedToken != token) {
            throw BadToken()
        }
        return transaction {
            val summary = EmojiLedger
                    .slice(EmojiLedger.p1, EmojiLedger.p2, EmojiLedger.emoji, EmojiLedger.amount.sum())
                    .select({ (EmojiLedger.p1 eq userId) or (EmojiLedger.p2 eq userId) })
                    .groupBy(EmojiLedger.p1, EmojiLedger.p2, EmojiLedger.emoji)
                    .having { EmojiLedger.amount.sum() neq 0 }
                    .toList()
                    .map {
                        val currentIsFirst = it[EmojiLedger.p1] == userId
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
                            strBuilder.append("<@${it.key}> owes <@${userId}> ${owes.joinToString(", ") { "${it["amount"]} ${it["emoji"]}" }
                            }.  ")
                        }
                        val owed = it.value.filter { (it["amount"] as Int) < 0 }
                        if (owed.isNotEmpty()) {
                            strBuilder.append("<@${userId}> owe <@${it.key}> ${owed.joinToString(", ") { "${-(it["amount"] as Int)} ${it["emoji"]}" }
                            }.")
                        }
                        strBuilder.trim().toString()
                    }
                    .joinToString("\n")
            mapOf("response_type" to "in_channel", "text" to summaryText)
        }
    }

    /**
     * parses `toUserName emoji [amount]` to a mapOf('to' to toUserName, 'emoji': emoji, 'amount': [amount|1])`
     */
    private fun parseTextForInput(inputText: String): Map<String, String> {
        val tokenized = inputText.split(delimiters = " ")
        if (tokenized.size != 2 && tokenized.size != 3) {
            throw IllegalArgumentException("syntax toUserName emoji [amount]")
        }
        if (tokenized.size == 3 && tokenized[2].toIntOrNull() == null) {
            throw IllegalArgumentException("syntax toUserName emoji [amount]")
        }
        // to_user is in the formt of: <@U02M08M30|jzig>

        return mapOf(
                "to" to userMentionRegex.find(tokenized[0])?.groups?.get(1)?.value!!,
                EmojiEntry::emoji.name to tokenized[1],
                EmojiEntry::amount.name to tokenized.getOrElse(2, { _ -> "1" })
        )
    }
}

