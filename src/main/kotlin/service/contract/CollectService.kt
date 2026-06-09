package org.burgas.service.contract

import org.burgas.dto.Response

interface CollectService<R : Response> {

    suspend fun findAll(): Set<R>
}