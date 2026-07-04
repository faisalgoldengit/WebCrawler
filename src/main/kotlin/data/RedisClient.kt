package org.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

class RedisClient{
    private val pool = JedisPool(JedisPoolConfig(),"localhost",6379)


    suspend fun pushUrl(queueKey:String,url: String)=withContext(Dispatchers.IO){

        pool.resource.use{jedis->
            jedis.lpush(queueKey,url)

        }
    }

    suspend fun popUrlBlocking(queueKey:String,timeout:Int=0):String?=withContext(Dispatchers.IO){
            pool.resource.use{jedis->
                jedis.brpop(timeout.toDouble(),queueKey)?.value
            }

    }

    suspend fun markVisitedIfNew(url:String):Boolean=withContext(Dispatchers.IO) {
        pool.resource.use { jedis ->
            jedis.sadd("visited:urls",url)==1L

        }
    }



}