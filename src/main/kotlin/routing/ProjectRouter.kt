package org.burgas.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import org.burgas.dao.ProjectEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.AuthSession
import org.burgas.dto.ProjectRequest
import org.burgas.service.ProjectService
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID

fun Application.configureProjectRouter() {

    val projectService = ProjectService()

    intercept(ApplicationCallPipeline.Plugins) {

        if (call.request.path().equals("/api/v1/projects/by-id", false)) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val projectId = UUID.fromString(call.parameters["projectId"])

            suspendTransaction(db = DatabaseConnection.postgres, readOnly = true) {
                val projectEntity = ProjectEntity.findById(projectId)!!.load(ProjectEntity::course, ProjectEntity::task)

                if (projectEntity.course.identities.map { it.email }.contains(authSession.email)) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Not authorized, you are not subscribed on course")
                }
            }

        } else {
            proceed()
        }
    }

    routing {

        route("/api/v1/projects") {

            authenticate("basic-auth-session") {

                get("/by-id") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    call.respond(HttpStatusCode.OK, projectService.findById(projectId))
                }
            }

            authenticate("basic-auth-admin") {

                post("/create") {
                    val projectRequest = call.receive(ProjectRequest::class)
                    projectService.create(projectRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/update") {
                    val projectRequest = call.receive(ProjectRequest::class)
                    projectService.update(projectRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    projectService.delete(projectId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/upload-task") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    projectService.uploadTask(projectId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-task") {
                    val projectId = UUID.fromString(call.parameters["projectId"])
                    val taskId = UUID.fromString(call.parameters["taskId"])
                    projectService.removeTask(projectId, taskId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}