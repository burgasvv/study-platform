package org.burgas.dao

import org.burgas.database.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

class ImageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ImageEntity>(ImageTable)

    var name by ImageTable.name
    var contentType by ImageTable.contentType
    var preview by ImageTable.preview
    var data by ImageTable.data
}

class FileEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FileEntity>(FileTable)

    var name by FileTable.name
    var contentType by FileTable.contentType
    var data by FileTable.data
}

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id) {
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
}

class CourseEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CourseEntity>(CourseTable)

    var name by CourseTable.name
    var description by CourseTable.description
    var createdAt by CourseTable.createdAt

    var identities by IdentityEntity.via(IdentityCourseTable.courseId, IdentityCourseTable.identityId)
    val projects by ProjectEntity.referrersOn(ProjectTable.courseId)
}

class ProjectEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ProjectEntity>(ProjectTable)

    var name by ProjectTable.name
    var description by ProjectTable.description
    var createdAt by ProjectTable.createdAt

    var course by CourseEntity.referencedOn(ProjectTable.courseId)
    var task by FileEntity.optionalReferencedOn(ProjectTable.taskId)
}