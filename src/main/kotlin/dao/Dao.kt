package org.burgas.dao

import io.ktor.http.content.PartData
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.toByteArray
import kotlinx.io.readByteArray
import org.burgas.database.*
import org.burgas.dto.Dependency
import org.burgas.dto.FileResponse
import org.burgas.dto.IdentityRequest
import org.burgas.dto.ImageResponse
import org.burgas.dto.Request
import org.burgas.dto.Response
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.mindrot.jbcrypt.BCrypt
import java.util.*

interface File

interface Dao

interface Uploader<F : File> {
    fun upload(partData: PartData): F
}

interface Inserter<R : Request> {
    fun insert(request: R)
}

interface Updater<R : Request> {
    fun update(request: R)
}

interface DependencyMapper<D : Dependency> {
    fun toDependency(): D
}

interface ResponseMapper<R : Response> {
    fun toResponse(): R
}

class ImageEntity(id: EntityID<UUID>) : UUIDEntity(id), File, ResponseMapper<ImageResponse>, Uploader<ImageEntity> {
    companion object : UUIDEntityClass<ImageEntity>(ImageTable)

    var name by ImageTable.name
    var contentType by ImageTable.contentType
    var preview by ImageTable.preview
    var data by ImageTable.data

    @OptIn(InternalAPI::class)
    override fun upload(partData: PartData): ImageEntity {
        if (partData is PartData.FileItem) {
            if (partData.contentType!!.contentType.startsWith("image")) {
                this.name = partData.originalFileName!!
                this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
                this.preview = true
                this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                return this
            } else {
                throw IllegalArgumentException("Content type is not image type")
            }
        } else {
            throw IllegalArgumentException("Part data is not File Item")
        }
    }

    override fun toResponse(): ImageResponse {
        return ImageResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType,
            preview = this.preview
        )
    }
}

class FileEntity(id: EntityID<UUID>) : UUIDEntity(id), File, ResponseMapper<FileResponse>, Uploader<FileEntity> {
    companion object : UUIDEntityClass<FileEntity>(FileTable)

    var name by FileTable.name
    var contentType by FileTable.contentType
    var data by FileTable.data

    @OptIn(InternalAPI::class)
    override fun upload(partData: PartData): FileEntity {
        if (partData is PartData.FileItem) {
            if (partData.contentType!!.contentType.startsWith("application")) {
                this.name = partData.originalFileName!!
                this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
                this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                return this
            } else {
                throw IllegalArgumentException("Content type is not application type")
            }
        } else {
            throw IllegalArgumentException("Part data is not File Item")
        }
    }

    override fun toResponse(): FileResponse {
        return FileResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType
        )
    }
}

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, Inserter<IdentityRequest>, Updater<IdentityRequest> {
    companion object : UUIDEntityClass<IdentityEntity>(IdentityTable)

    var authority by IdentityTable.authority
    var email by IdentityTable.email
    var password by IdentityTable.password
    var status by IdentityTable.status
    var firstname by IdentityTable.firstname
    var lastname by IdentityTable.lastname
    var patronymic by IdentityTable.patronymic
    var about by IdentityTable.about

    var image by ImageEntity.optionalReferencedOn(IdentityTable.imageId)
    var files by FileEntity.via(IdentityFileTable.identityId, IdentityFileTable.fileId)
    var courses by CourseEntity.via(IdentityCourseTable.identityId, IdentityCourseTable.courseId)

    override fun insert(request: IdentityRequest) {
        this.authority = request.authority!!
        this.email = request.email!!
        this.password = BCrypt.hashpw(request.password!!, BCrypt.gensalt())
        this.status = request.status ?: true
        this.firstname = request.firstname!!
        this.lastname = request.lastname!!
        this.patronymic = request.patronymic!!
        this.about = request.about!!
    }

    override fun update(request: IdentityRequest) {
        this.authority = request.authority ?: this.authority
        this.email = request.email ?: this.email
        this.firstname = request.firstname ?: this.firstname
        this.lastname = request.lastname ?: this.lastname
        this.patronymic = request.patronymic ?: this.patronymic
        this.about = request.about ?: this.about
    }
}

class CourseEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao {
    companion object : UUIDEntityClass<CourseEntity>(CourseTable)

    var name by CourseTable.name
    var description by CourseTable.description
    var createdAt by CourseTable.createdAt

    var identities by IdentityEntity.via(IdentityCourseTable.courseId, IdentityCourseTable.identityId)
    val projects by ProjectEntity.referrersOn(ProjectTable.courseId)
}

class ProjectEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao {
    companion object : UUIDEntityClass<ProjectEntity>(ProjectTable)

    var name by ProjectTable.name
    var description by ProjectTable.description
    var createdAt by ProjectTable.createdAt

    var course by CourseEntity.referencedOn(ProjectTable.courseId)
    var task by FileEntity.optionalReferencedOn(ProjectTable.taskId)
}