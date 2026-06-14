package org.burgas.service

import io.ktor.http.content.*
import kotlinx.serialization.json.Json
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.IdentityRequest
import org.burgas.dto.IdentityResponse
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
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.util.UUID

class IdentityService : CacheHandler<IdentityEntity>, FindService<UUID, IdentityEntity, IdentityResponse>,
    CollectService<IdentityResponse>, DesignService<UUID, IdentityRequest, IdentityResponse>,
    ModifyService<IdentityRequest, IdentityResponse> {

    private val redis = DatabaseConnection.redisPool.resource
    private val imageService = ImageService()
    private val fileService = FileService()

    override suspend fun handleCache(entity: IdentityEntity) {
        val identityKey = RedisKeys.IDENTITY_KEY.format(entity.id.value)
        if (redis.exists(identityKey)) redis.del(identityKey)

        val courses = entity.courses
        if (!courses.empty()) {
            courses.forEach { courseEntity ->
                val courseKey = RedisKeys.COURSE_KEY.format(courseEntity.id.value)
                if (redis.exists(courseKey)) redis.del(courseKey)
            }
        }
    }

    override suspend fun findEntity(id: UUID): IdentityEntity = suspendTransaction(
        db = DatabaseConnection.postgres, readOnly = true
    ) {
        IdentityEntity.findById(id)!!
            .load(IdentityEntity::image, IdentityEntity::files, IdentityEntity::courses)
    }

    override suspend fun findById(id: UUID): IdentityResponse = suspendTransaction(
        db = DatabaseConnection.postgres, readOnly = true
    ) {
        val identityKey = RedisKeys.IDENTITY_KEY.format(id)
        if (redis.exists(identityKey)) {
            Json.decodeFromString<IdentityResponse>(redis.get(identityKey))
        } else {
            val identityResponse = findEntity(id).toResponse()
            redis.set(identityKey, Json.encodeToString(identityResponse))
            identityResponse
        }
    }

    override suspend fun findAll(): Set<IdentityResponse> = suspendTransaction(
        db = DatabaseConnection.postgres, readOnly = true
    ) {
        IdentityEntity.all()
            .with(IdentityEntity::image, IdentityEntity::files, IdentityEntity::courses)
            .map { it.toResponse() }
            .toHashSet()
    }

    override suspend fun create(request: IdentityRequest): IdentityResponse = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.new { this.insert(request) }
            .load(IdentityEntity::image, IdentityEntity::files, IdentityEntity::courses)
        handleCache(identityEntity)
        val identityKey = RedisKeys.IDENTITY_KEY.format(identityEntity.id.value)
        val identityResponse = identityEntity.toResponse()
        redis.set(identityKey, Json.encodeToString(identityResponse))
        identityResponse
    }

    override suspend fun update(request: IdentityRequest): IdentityResponse = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.findByIdAndUpdate(request.id!!) { it.update(request) }!!
            .load(IdentityEntity::image, IdentityEntity::files, IdentityEntity::courses)
        handleCache(identityEntity)
        val identityKey = RedisKeys.IDENTITY_KEY.format(identityEntity.id.value)
        val identityResponse = identityEntity.toResponse()
        redis.set(identityKey, Json.encodeToString(identityResponse))
        identityResponse
    }

    override suspend fun delete(id: UUID) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = findEntity(id)
        identityEntity.files.forEach { it.delete() }
        identityEntity.delete()
        handleCache(identityEntity)
    }

    suspend fun uploadImage(identityId: UUID, multiPartData: MultiPartData) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = findEntity(identityId)
        val imageEntity = imageService.upload(multiPartData.readPart()!!)
        identityEntity.image = imageEntity
        handleCache(identityEntity)
    }

    suspend fun removeImage(identityId: UUID) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = findEntity(identityId)
        identityEntity.image!!.delete()
        handleCache(identityEntity)
    }

    suspend fun uploadFile(identityId: UUID, multiPartData: MultiPartData) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = findEntity(identityId)
        val fileEntity = fileService.upload(multiPartData.readPart()!!)
        identityEntity.files = SizedCollection(identityEntity.files + fileEntity)
        handleCache(identityEntity)
    }

    suspend fun removeFile(identityId: UUID, fileId: UUID) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = findEntity(identityId)
        if (identityEntity.files.map { it.id.value }.contains(fileId)) {
            fileService.remove(fileId)
            handleCache(identityEntity)
        } else {
            throw IllegalArgumentException("Input file not in identity file list")
        }
    }

    suspend fun changePassword(identityRequest: IdentityRequest) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = findEntity(identityRequest.id!!)
        if (!BCrypt.checkpw(identityRequest.password!!, identityEntity.password)) {
            identityEntity.password = BCrypt.hashpw(identityRequest.password, BCrypt.gensalt())
            handleCache(identityEntity)
        } else {
            throw IllegalArgumentException("Passwords matched")
        }
    }

    suspend fun changeStatus(identityRequest: IdentityRequest) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = findEntity(identityRequest.id!!)
        if (identityRequest.status!! != identityEntity.status) {
            identityEntity.status = identityRequest.status
            handleCache(identityEntity)
        } else {
            throw IllegalArgumentException("Statuses matched")
        }
    }
}