package org.burgas.service

import io.ktor.http.content.MultiPartData
import kotlinx.serialization.json.Json
import org.burgas.dao.ProjectEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.ProjectRequest
import org.burgas.dto.ProjectResponse
import org.burgas.redis.CacheHandler
import org.burgas.redis.RedisKeys
import org.burgas.service.contract.DesignService
import org.burgas.service.contract.FindService
import org.burgas.service.contract.ModifyService
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.Connection
import java.util.*

class ProjectService : CacheHandler<ProjectEntity>, FindService<UUID, ProjectEntity, ProjectResponse>,
    DesignService<UUID, ProjectRequest, ProjectResponse>, ModifyService<ProjectRequest, ProjectResponse> {

    private val redis = DatabaseConnection.redisPool.resource
    private val fileService = FileService()

    override suspend fun handleCache(entity: ProjectEntity) {
        val projectKey = RedisKeys.PROJECT_KEY.format(entity.id.value)
        if (redis.exists(projectKey)) redis.del(projectKey)

        val course = entity.course
        val courseKey = RedisKeys.COURSE_KEY.format(course.id.value)
        if (redis.exists(courseKey)) redis.del(courseKey)
    }

    override suspend fun findEntity(id: UUID): ProjectEntity = suspendTransaction(
        db = DatabaseConnection.postgres, readOnly = true
    ) {
        ProjectEntity.findById(id)!!.load(ProjectEntity::course, ProjectEntity::task)
    }

    override suspend fun findById(id: UUID): ProjectResponse = suspendTransaction(
        db = DatabaseConnection.postgres, readOnly = true
    ) {
        val projectKey = RedisKeys.PROJECT_KEY.format(id)
        if (redis.exists(projectKey)) {
            Json.decodeFromString<ProjectResponse>(redis.get(projectKey))
        } else {
            val projectResponse = findEntity(id).toResponse()
            redis.set(projectKey, Json.encodeToString(projectResponse))
            projectResponse
        }
    }

    override suspend fun create(request: ProjectRequest): ProjectResponse = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val projectEntity = ProjectEntity.new { this.insert(request) }.load(ProjectEntity::course, ProjectEntity::task)
        handleCache(projectEntity)
        val projectKey = RedisKeys.PROJECT_KEY.format(projectEntity.id.value)
        val projectResponse = projectEntity.toResponse()
        redis.set(projectKey, Json.encodeToString(projectResponse))
        projectResponse
    }

    override suspend fun update(request: ProjectRequest): ProjectResponse = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val projectEntity = ProjectEntity.findByIdAndUpdate(request.id!!) { it.update(request) }!!
            .load(ProjectEntity::course, ProjectEntity::task)
        handleCache(projectEntity)
        val projectKey = RedisKeys.PROJECT_KEY.format(projectEntity.id.value)
        val projectResponse = projectEntity.toResponse()
        redis.set(projectKey, Json.encodeToString(projectResponse))
        projectResponse
    }

    override suspend fun delete(id: UUID) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val projectEntity = findEntity(id)
        projectEntity.task?.delete()
        projectEntity.delete()
        handleCache(projectEntity)
    }

    suspend fun uploadTask(projectId: UUID, multiPartData: MultiPartData) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val projectEntity = findEntity(projectId)
        val fileEntity = fileService.upload(multiPartData.readPart()!!)
        projectEntity.task = fileEntity
        handleCache(projectEntity)
    }

    suspend fun removeTask(projectId: UUID, taskId: UUID) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val projectEntity = findEntity(projectId)
        if (projectEntity.task?.id?.value == taskId) {
            fileService.remove(taskId)
            handleCache(projectEntity)
        } else {
            throw IllegalArgumentException("This task is not a part of project")
        }
    }
}