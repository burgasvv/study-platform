package org.burgas.service

import io.ktor.http.content.*
import org.burgas.dao.ImageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.service.contract.DocService
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.Connection
import java.util.*

class ImageService : DocService<UUID, ImageEntity> {

    override suspend fun findEntity(id: UUID): ImageEntity = suspendTransaction(
        db = DatabaseConnection.postgres, readOnly = true
    ) {
        ImageEntity.findById(id)!!
    }

    override suspend fun upload(partData: PartData): ImageEntity = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        ImageEntity.new { this.upload(partData) }
    }

    override suspend fun remove(id: UUID) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        ImageEntity.findById(id)!!.delete()
    }
}