package org.burgas.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.util.AttributeKey
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.AuthSession
import org.burgas.dto.CourseIdentityRequest
import org.burgas.dto.CourseRequest
import org.burgas.service.CourseService
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID

fun Application.configureCourseRouter() {

    val courseService = CourseService()
    val bodyUrls: List<String> = listOf("/api/v1/courses/add-identity", "/api/v1/courses/remove-identity")

    intercept(ApplicationCallPipeline.Plugins) {

        if (bodyUrls.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val courseIdentityRequest = call.receive(CourseIdentityRequest::class)

            val identityEntity = suspendTransaction(db = DatabaseConnection.postgres, readOnly = true) {
                IdentityEntity.findById(courseIdentityRequest.identityId)!!
            }
            if (identityEntity.email == authSession.email) {
                call.attributes[AttributeKey<CourseIdentityRequest>("courseIdentityRequest")] = courseIdentityRequest
                proceed()
            } else {
                throw IllegalArgumentException("Not authorized")
            }

        } else {
            proceed()
        }
    }

    routing {

        route("/api/v1/courses") {

            authenticate("basic-auth-session") {

                get {
                    call.respond(HttpStatusCode.OK, courseService.findAll())
                }

                get("/by-id") {
                    val courseId = UUID.fromString(call.parameters["courseId"])
                    call.respond(HttpStatusCode.OK, courseService.findById(courseId))
                }

                put("/add-identity") {
                    val courseIdentityRequest = call.attributes[AttributeKey<CourseIdentityRequest>("courseIdentityRequest")]
                    courseService.addIdentity(courseIdentityRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/remove-identity") {
                    val courseIdentityRequest = call.attributes[AttributeKey<CourseIdentityRequest>("courseIdentityRequest")]
                    courseService.removeIdentity(courseIdentityRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }

            authenticate("basic-auth-admin") {

                post("/create") {
                    val courseRequest = call.receive(CourseRequest::class)
                    courseService.create(courseRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/update") {
                    val courseRequest = call.receive(CourseRequest::class)
                    courseService.update(courseRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val courseId = UUID.fromString(call.parameters["courseId"])
                    courseService.delete(courseId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}