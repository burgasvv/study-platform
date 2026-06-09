package org.burgas.service

import io.ktor.http.content.PartData
import org.burgas.dao.FileEntity
import org.burgas.database.DatabaseConnection
import org.burgas.service.contract.DocService
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.Connection
import java.util.UUID

class FileService : DocService<UUID, FileEntity> {

    override suspend fun findEntity(id: UUID): FileEntity = suspendTransaction(
        db = DatabaseConnection.postgres, readOnly = true
    ) {
        FileEntity.findById(id)!!
    }

    override suspend fun upload(partData: PartData): FileEntity = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        FileEntity.new { this.upload(partData) }
    }

    override suspend fun remove(id: UUID) = suspendTransaction(
        db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        FileEntity.findById(id)!!.delete()
    }
}