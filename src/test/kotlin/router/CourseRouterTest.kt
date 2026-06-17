package org.burgas.router

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.basicAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.burgas.dao.CourseEntity
import org.burgas.dao.IdentityEntity
import org.burgas.database.Authority
import org.burgas.database.CourseTable
import org.burgas.database.DatabaseConnection
import org.burgas.database.IdentityTable
import org.burgas.dto.CourseIdentityRequest
import org.burgas.dto.CourseRequest
import org.burgas.dto.CsrfToken
import org.burgas.dto.IdentityRequest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

class CourseRouterTest {

    @Test
    fun `course router test`() = testApplication {
        configure()
        val httpClient = createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        allowComments = true
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                        explicitNulls = true
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

        httpClient.get("/api/v1/security/login") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Text.Plain)
            basicAuth("admin@gmail.com", "admin")
        }

        val identityEntity = suspendTransaction(db = DatabaseConnection.postgres, readOnly = true) {
            IdentityEntity.find { IdentityTable.email eq "admin@gmail.com" }.single()
        }

        httpClient.post("/api/v1/courses/create") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val courseRequest = CourseRequest(name = "Test Course", description = "About Test Course")
            setBody(courseRequest)
        }
            .apply { assertEquals(HttpStatusCode.OK, status) }

        val courseEntity = suspendTransaction(db = DatabaseConnection.postgres, readOnly = true) {
            CourseEntity.find { CourseTable.name eq "Test Course" }.single()
        }

        httpClient.put("/api/v1/courses/update") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val courseRequest = CourseRequest(id = courseEntity.id.value , name = "New Test Course")
            setBody(courseRequest)
        }
            .apply { assertEquals(HttpStatusCode.OK, status) }

        httpClient.get("/api/v1/courses") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
            .apply { assertEquals(HttpStatusCode.OK, status) }

        httpClient.get("/api/v1/courses/by-id") {
            parameter("courseId", courseEntity.id.value)
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
            .apply { assertEquals(HttpStatusCode.OK, status) }

        val courseIdentityRequest = CourseIdentityRequest(courseId = courseEntity.id.value, identityId = identityEntity.id.value)

        httpClient.put("/api/v1/courses/add-identity") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
            setBody(courseIdentityRequest)
        }
            .apply { assertEquals(HttpStatusCode.OK, status) }

        httpClient.put("/api/v1/courses/remove-identity") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
            setBody(courseIdentityRequest)
        }
            .apply { assertEquals(HttpStatusCode.OK, status) }

        httpClient.delete("/api/v1/courses/delete") {
            parameter("courseId", courseEntity.id.value)
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply { assertEquals(HttpStatusCode.OK, status) }

        httpClient.post("/api/v1/identities/delete") {
            parameter("identityId", identityEntity.id.value)
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
        }
    }
}