package org.burgas.service

import kotlinx.serialization.json.Json
import org.burgas.dao.CourseEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.CourseIdentityRequest
import org.burgas.dto.CourseRequest
import org.burgas.dto.CourseResponse
import org.burgas.redis.CacheHandler
import org.burgas.redis.RedisKeys
import org.burgas.service.contract.CollectService
import org.burgas.service.contract.DesignService
import org.burgas.service.contract.FindService
import org.burgas.service.contract.ModifyService
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.Connection
import java.util.*

class CourseService : CacheHandler<CourseEntity>, FindService<UUID, CourseEntity, CourseResponse>,
    CollectService<CourseResponse>, DesignService<UUID, CourseRequest, CourseResponse>,
    ModifyService<CourseRequest, CourseResponse> {

    private val redis = DatabaseConnection.redisPool.resource
    private val identityService = IdentityService()

    override suspend fun handleCache(entity: CourseEntity) {
        val courseKey = RedisKeys.COURSE_KEY.format(entity.id.value)
        if (redis.exists(courseKey)) redis.del(courseKey)

        val identities = entity.identities
        if (!identities.empty()) {
            identities.forEach { identityEntity ->
                val identityKey = RedisKeys.IDENTITY_KEY.format(identityEntity.id.value)
                if (redis.exists(identityKey)) redis.del(identityKey)
            }
        }

        val projects = entity.projects
        if (!projects.empty()) {
            projects.forEach { projectEntity ->
                val projectKey = RedisKeys.PROJECT_KEY.format(projectEntity.id.value)
                if (redis.exists(projectKey)) redis.del(projectKey)
            }
        }
    }

    override suspend fun findEntity(id: UUID): CourseEntity = suspendTransaction(
        db = DatabaseConnection.postgres, readOnly = true
    ) {
        CourseEntity.findById(id)!!.load(CourseEntity::identities, CourseEntity::projects)
    }

    override suspend fun findById(id: UUID): CourseResponse = suspendTransaction(
        db = DatabaseConnection.postgres, readOnly = true
    ) {
        val courseKey = RedisKeys.COURSE_KEY.format(id)
        if (redis.exists(courseKey)) {
            Json.decodeFromString<CourseResponse>(redis.get(courseKey))
        } else {
            val courseResponse = findEntity(id).toResponse()
            redis.set(courseKey, Json.encodeToString(courseResponse))
            courseResponse
        }
    }

    override suspend fun findAll(): Set<CourseResponse> = suspendTransaction(
        db = DatabaseConnection.postgres, readOnly = true
    ) {
        CourseEntity.all().with(CourseEntity::identities, CourseEntity::projects)
            .map { it.toResponse() }.toHashSet()
    }

    override suspend fun create(request: CourseRequest): CourseResponse = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val courseEntity = CourseEntity.new { this.insert(request) }
            .load(CourseEntity::identities, CourseEntity::projects)
        handleCache(courseEntity)
        val courseKey = RedisKeys.COURSE_KEY.format(courseEntity.id.value)
        val courseResponse = courseEntity.toResponse()
        redis.set(courseKey, Json.encodeToString(courseResponse))
        courseResponse
    }

    override suspend fun update(request: CourseRequest): CourseResponse = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val courseEntity = CourseEntity.findByIdAndUpdate(request.id!!) { it.update(request) }!!
            .load(CourseEntity::identities, CourseEntity::projects)
        handleCache(courseEntity)
        val courseKey = RedisKeys.COURSE_KEY.format(courseEntity.id.value)
        val courseResponse = courseEntity.toResponse()
        redis.set(courseKey, Json.encodeToString(courseResponse))
        courseResponse
    }

    override suspend fun delete(id: UUID) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val courseEntity = findEntity(id)
        courseEntity.delete()
        handleCache(courseEntity)
    }

    suspend fun addIdentity(courseIdentityRequest: CourseIdentityRequest) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val courseEntity = findEntity(courseIdentityRequest.courseId)
        val identityEntity = identityService.findEntity(courseIdentityRequest.identityId)
        if (!courseEntity.identities.map { it.id.value }.contains(identityEntity.id.value)) {
            courseEntity.identities = SizedCollection(courseEntity.identities + identityEntity)
            handleCache(courseEntity)
            identityService.handleCache(identityEntity)
        } else {
            throw IllegalArgumentException("Identity already in course identity list")
        }
    }

    suspend fun removeIdentity(courseIdentityRequest: CourseIdentityRequest) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val courseEntity = findEntity(courseIdentityRequest.courseId)
        val identityEntity = identityService.findEntity(courseIdentityRequest.identityId)
        if (courseEntity.identities.map { it.id.value }.contains(identityEntity.id.value)) {
            courseEntity.identities = SizedCollection(courseEntity.identities - identityEntity)
            handleCache(courseEntity)
            identityService.handleCache(identityEntity)
        } else {
            throw IllegalArgumentException("Identity already in course identity list")
        }
    }
}