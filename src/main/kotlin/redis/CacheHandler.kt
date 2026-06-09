package org.burgas.redis

import org.burgas.dao.Dao

interface CacheHandler<D : Dao> {

    suspend fun handleCache(entity: D)
}