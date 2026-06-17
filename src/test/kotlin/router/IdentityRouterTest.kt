package org.burgas.router

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.burgas.dao.IdentityEntity
import org.burgas.database.Authority
import org.burgas.database.DatabaseConnection
import org.burgas.database.IdentityTable
import org.burgas.dto.CsrfToken
import org.burgas.dto.IdentityRequest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityRouterTest {

    @Test
    fun `identity router tests`() = testApplication {
        configure()
        val httpClient = createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        explicitNulls = true
                        ignoreUnknownKeys = true
                        isLenient = true
                        prettyPrint = true
                        allowComments = true
                    }
                )
            }
            install(HttpCookies)
        }

        val csrfToken = httpClient.get("/api/v1/security/csrf-token") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }.body<CsrfToken>()

        httpClient.post("/api/v1/identities/create") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
            val identityRequest = IdentityRequest(
                authority = Authority.ADMIN,
                email = "admin@gmail.com",
                password = "admin",
                status = true,
                firstname = "Admin",
                lastname = "Admin",
                patronymic = "Admin",
                about = "About Admin"
            )
            setBody(identityRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, status)
            }

        httpClient.get("/api/v1/security/login") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Text.Plain)
            basicAuth("admin@gmail.com", "admin")
        }

        val identityEntity = suspendTransaction(db = DatabaseConnection.postgres, readOnly = true) {
            IdentityEntity.find { IdentityTable.email eq "admin@gmail.com" }.single()
        }

        httpClient.post("/api/v1/identities/update") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
            val identityRequest = IdentityRequest(
                id = identityEntity.id.value,
                about = "About Admin Account"
            )
            setBody(identityRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.Found, status)
            }

        httpClient.get("/api/v1/identities") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, status)
            }

        httpClient.get("/api/v1/identities/by-id") {
            parameter("identityId", identityEntity.id.value)
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, status)
            }

        httpClient.post("/api/v1/identities/delete") {
            parameter("identityId", identityEntity.id.value)
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
        }
            .apply {
                assertEquals(HttpStatusCode.Found, status)
            }
    }
}