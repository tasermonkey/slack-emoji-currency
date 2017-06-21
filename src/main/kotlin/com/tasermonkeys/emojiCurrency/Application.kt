package com.tasermonkeys.emojiCurrency

import com.tasermonkeys.emojiCurrency.configuration.AppProps
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties


@EnableConfigurationProperties(AppProps::class)
@SpringBootApplication
open class Application

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}