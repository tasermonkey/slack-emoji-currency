package com.tasermonkeys.emojiCurrency.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "Bad Token") // 404
class BadToken : RuntimeException()

