package org.burgas.service.contract

import io.ktor.http.content.PartData
import org.burgas.dao.File

interface DocService<ID, F : File> {

    suspend fun findEntity(id: ID): F

    suspend fun upload(partData: PartData): F

    suspend fun remove(id: ID)
}