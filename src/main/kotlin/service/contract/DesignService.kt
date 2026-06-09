package org.burgas.service.contract

import org.burgas.dto.Request
import org.burgas.dto.Response

interface DesignService<ID, Req : Request, Res : Response> {

    suspend fun create(request: Req): Res

    suspend fun delete(id: ID)
}