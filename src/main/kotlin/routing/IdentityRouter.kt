package org.burgas.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.util.AttributeKey
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.AuthSession
import org.burgas.dto.IdentityRequest
import org.burgas.service.IdentityService
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

fun Application.configureIdentityRouter() {

    val identityService = IdentityService()
    val bodyUrls: List<String> = listOf(
        "/api/v1/identities/update", "/api/v1/identities/change-password"
    )
    val paramUrls: List<String> = listOf(
        "/api/v1/identities/by-id", "/api/v1/identities/delete", "/api/v1/identities/upload-image",
        "/api/v1/identities/remove-image", "/api/v1/identities/upload-file", "/api/v1/identities/remove-image"
    )

    intercept(ApplicationCallPipeline.Plugins) {

        if (paramUrls.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val identityId = UUID.fromString(call.parameters["identityId"])

            val identityEntity = suspendTransaction(db = DatabaseConnection.postgres, readOnly = true) {
                IdentityEntity.findById(identityId)!!
            }
            if (identityEntity.email == authSession.email) {
                proceed()
            } else {
                throw IllegalArgumentException("Not authorized")
            }

        } else if (bodyUrls.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val identityRequest = call.receive(IdentityRequest::class)

            val identityEntity = suspendTransaction(db = DatabaseConnection.postgres, readOnly = true) {
                IdentityEntity.findById(identityRequest.id!!)!!
            }
            if (identityEntity.email == authSession.email) {
                call.attributes[AttributeKey<IdentityRequest>("identityRequest")] = identityRequest
                proceed()
            } else {
                throw IllegalArgumentException("Not authorized")
            }

        } else {
            proceed()
        }
    }

    routing {

        route("/api/v1/identities") {

            post("/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                identityService.create(identityRequest)
                call.respond(HttpStatusCode.OK)
            }

            authenticate("basic-auth-admin") {

                get {
                    call.respond(HttpStatusCode.OK, identityService.findAll())
                }

                put("/change-status") {
                    val identityRequest = call.receive(IdentityRequest::class)
                    identityService.changeStatus(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }

            authenticate("basic-auth-session") {

                get("/by-id") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    call.respond(HttpStatusCode.OK, identityService.findById(identityId))
                }

                post("/update") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    val identityResponse = identityService.update(identityRequest)
                    call.respondRedirect("/api/v1/identities/by-id?identityId=${identityResponse.id}")
                }

                delete("/delete") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.delete(identityId)
                    call.respondRedirect("/api/v1/security/logout")
                }

                post("/upload-image") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.uploadImage(identityId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-image") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.removeImage(identityId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-file") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.uploadFile(identityId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-file") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    val fileId = UUID.fromString(call.parameters["fileId"])
                    identityService.removeFile(identityId, fileId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/change-password") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    identityService.changePassword(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}