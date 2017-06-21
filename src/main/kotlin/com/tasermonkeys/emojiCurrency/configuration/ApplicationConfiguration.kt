package com.tasermonkeys.emojiCurrency.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app")
data class AppProps(var expectedToken: String = "none")