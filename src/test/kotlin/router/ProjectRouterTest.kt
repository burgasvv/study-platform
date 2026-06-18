package org.burgas.router

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.burgas.dao.CourseEntity
import org.burgas.dao.IdentityEntity
import org.burgas.dao.ProjectEntity
import org.burgas.database.Authority
import org.burgas.database.CourseTable
import org.burgas.database.DatabaseConnection
import org.burgas.database.IdentityTable
import org.burgas.database.ProjectTable
import org.burgas.dto.CourseRequest
import org.burgas.dto.CsrfToken
import org.burgas.dto.IdentityRequest
import org.burgas.dto.ProjectRequest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.Test
import kotlin.test.assertEquals

class ProjectRouterTest {

    @Test
    fun `project router tests`() = testApplication {
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

        val courseEntity = suspendTransaction(db = DatabaseConnection.postgres, readOnly = true) {
            CourseEntity.find { CourseTable.name eq "Test Course" }.single()
        }

        httpClient.post("/api/v1/projects/create") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val projectRequest = ProjectRequest(
                name = "Test Project", description = "About Test Project", courseId = courseEntity.id.value
            )
            setBody(projectRequest)
        }
            .apply { assertEquals(HttpStatusCode.OK, status) }

        val projectEntity = suspendTransaction(db = DatabaseConnection.postgres, readOnly = true) {
            ProjectEntity.find { ProjectTable.name eq "Test Project" }.single()
        }

        httpClient.put("/api/v1/projects/update") {
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
            val projectRequest = ProjectRequest(id = projectEntity.id.value, name = "New Test Project",)
            setBody(projectRequest)
        }
            .apply { assertEquals(HttpStatusCode.OK, status) }

        httpClient.delete("/api/v1/projects/delete") {
            parameter("projectId", projectEntity.id.value)
            header(HttpHeaders.Host, "localhost:8080")
            header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-CSRF-Token", csrfToken.token)
            basicAuth("admin@gmail.com", "admin")
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