package org.burgas.dto

import kotlinx.serialization.Serializable
import org.burgas.database.Authority
import org.burgas.serialization.UUIDSerializer
import java.util.*

interface Request

interface Dependency

interface Response

@Serializable
data class AuthSession(
    val email: String
)

@Serializable
data class CsrfToken(
    @Serializable(with = UUIDSerializer::class)
    val token: UUID
)

@Serializable
data class ExceptionResponse(
    val status: String,
    val code: Int,
    val message: String
)

@Serializable
data class ImageResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null,
    val preview: Boolean? = null
) : Response

@Serializable
data class FileResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null
) : Response

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val authority: Authority? = null,
    val email: String? = null,
    val password: String? = null,
    val status: Boolean? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val about: String? = null
) : Request

@Serializable
data class IdentityDependency(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val about: String? = null,
    val image: ImageResponse? = null
) : Dependency

@Serializable
data class IdentityResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val about: String? = null,
    val image: ImageResponse? = null,
    val files: Set<FileResponse>? = null,
    val courses: Set<CourseDependency>? = null
) : Response

@Serializable
data class CourseRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null
) : Request

@Serializable
data class CourseDependency(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null
) : Dependency

@Serializable
data class CourseResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val identities: Set<IdentityDependency>? = null,
    val projects: Set<ProjectDependency>? = null
) : Response

@Serializable
data class ProjectRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val courseId: UUID? = null
) : Request

@Serializable
data class ProjectDependency(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null
) : Dependency

@Serializable
data class ProjectResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val course: CourseDependency? = null,
    val task: FileResponse? = null
) : Response