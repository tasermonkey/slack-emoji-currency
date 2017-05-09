package com.tasermonkeys.emojiCurrency

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import javax.sql.DataSource


@SpringBootApplication
open class Application

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}