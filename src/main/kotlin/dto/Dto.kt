package org.burgas.dto

import kotlinx.serialization.Serializable
import org.burgas.database.Authority
import org.burgas.serialization.UUIDSerializer
import java.util.*

interface Request

interface Dependency

interface Response

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
    val id: UUID?,
    val name: String?,
    val contentType: String?,
    val preview: Boolean?
) : Response

@Serializable
data class FileResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val name: String?,
    val contentType: String?
) : Response

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val authority: Authority?,
    val email: String?,
    val password: String?,
    val status: Boolean?,
    val firstname: String?,
    val lastname: String?,
    val patronymic: String?,
    val about: String?
) : Request

@Serializable
data class IdentityDependency(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val email: String?,
    val firstname: String?,
    val lastname: String?,
    val patronymic: String?,
    val about: String?,
    val image: ImageResponse?
) : Dependency

@Serializable
data class IdentityResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val email: String?,
    val firstname: String?,
    val lastname: String?,
    val patronymic: String?,
    val about: String?,
    val image: ImageResponse?,
    val files: Set<FileResponse>?,
    val courses: Set<CourseDependency>?
)

@Serializable
data class CourseRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val name: String?,
    val description: String?
) : Request

@Serializable
data class CourseDependency(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val name: String?,
    val description: String?,
    val createdAt: String?
) : Dependency

@Serializable
data class CourseResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val name: String?,
    val description: String?,
    val createdAt: String?,
    val identities: Set<IdentityDependency>?,
    val projects: Set<ProjectDependency>?
) : Response

@Serializable
data class ProjectRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val name: String?,
    val description: String?,
    @Serializable(with = UUIDSerializer::class)
    val courseId: UUID?
)

@Serializable
data class ProjectDependency(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val name: String?,
    val description: String?,
    val createdAt: String?
) : Dependency

@Serializable
data class ProjectResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val name: String?,
    val description: String?,
    val createdAt: String?,
    val course: CourseDependency?,
    val task: FileResponse?
)