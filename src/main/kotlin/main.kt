package org.burgas

fun main(args: Array<String>) {
    val hello = "Hello Ktor"
    println(hello)
    io.ktor.server.netty.EngineMain.main(args)
}
