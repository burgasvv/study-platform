package org.burgas.dao

import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.io.readByteArray
import org.burgas.database.*
import org.burgas.dto.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

open class ImageEntity(id: EntityID<UUID>) : UUIDEntity(id), File, ResponseMapper<ImageResponse>, Uploader<ImageEntity> {
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

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, Inserter<IdentityRequest>, Updater<IdentityRequest>,
    DependencyMapper<IdentityDependency>, ResponseMapper<IdentityResponse> {

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

    override fun toDependency(): IdentityDependency {
        return IdentityDependency(
            id = this.id.value,
            email = this.email,
            firstname = this.firstname,
            lastname = this.lastname,
            patronymic = this.patronymic,
            about = this.about,
            image = this.image?.toResponse()
        )
    }

    override fun toResponse(): IdentityResponse {
        return IdentityResponse(
            id = this.id.value,
            email = this.email,
            firstname = this.firstname,
            lastname = this.lastname,
            patronymic = this.patronymic,
            about = this.about,
            image = this.image?.toResponse(),
            files = this.files.map { it.toResponse() }.toHashSet(),
            courses = this.courses.map { it.toDependency() }.toHashSet()
        )
    }
}

class CourseEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, Inserter<CourseRequest>, Updater<CourseRequest>,
    DependencyMapper<CourseDependency>, ResponseMapper<CourseResponse> {
    companion object : UUIDEntityClass<CourseEntity>(CourseTable)

    var name by CourseTable.name
    var description by CourseTable.description
    var createdAt by CourseTable.createdAt

    var identities by IdentityEntity.via(IdentityCourseTable.courseId, IdentityCourseTable.identityId)
    val projects by ProjectEntity.referrersOn(ProjectTable.courseId)

    override fun insert(request: CourseRequest) {
        this.name = request.name!!
        this.description = request.description!!
        this.createdAt = LocalDate.now().toKotlinLocalDate()
    }

    override fun update(request: CourseRequest) {
        this.name = request.name ?: this.name
        this.description = request.description ?: this.description
    }

    override fun toDependency(): CourseDependency {
        return CourseDependency(
            id = this.id.value,
            name = this.name,
            description = this.description,
            createdAt = this.createdAt.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
        )
    }

    override fun toResponse(): CourseResponse {
        return CourseResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            createdAt = this.createdAt.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
            identities = this.identities.map { it.toDependency() }.toHashSet(),
            projects = this.projects.map { it.toDependency() }.toHashSet()
        )
    }
}

class ProjectEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, Inserter<ProjectRequest>, Updater<ProjectRequest>,
    DependencyMapper<ProjectDependency>, ResponseMapper<ProjectResponse> {
    companion object : UUIDEntityClass<ProjectEntity>(ProjectTable)

    var name by ProjectTable.name
    var description by ProjectTable.description
    var createdAt by ProjectTable.createdAt

    var course by CourseEntity.referencedOn(ProjectTable.courseId)
    var task by FileEntity.optionalReferencedOn(ProjectTable.taskId)

    override fun insert(request: ProjectRequest) {
        this.name = request.name!!
        this.description = request.description!!
        this.createdAt = LocalDateTime.now().toKotlinLocalDateTime()
        this.course = CourseEntity.findById(request.courseId!!)!!
    }

    override fun update(request: ProjectRequest) {
        this.name = request.name ?: this.name
        this.description = request.description ?: this.description
        this.course = CourseEntity.findById(request.courseId ?: UUID(0,0)) ?: this.course
    }

    override fun toDependency(): ProjectDependency {
        return ProjectDependency(
            id = this.id.value,
            name = this.name,
            description = this.description,
            createdAt = this.createdAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }

    override fun toResponse(): ProjectResponse {
        return ProjectResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            createdAt = this.createdAt.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            course = this.course.toDependency(),
            task = this.task?.toResponse()
        )
    }
}