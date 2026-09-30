package org.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import kotlin.math.exp

class RedisClient{
    private val pool = JedisPool(
        JedisPoolConfig().apply {
            maxTotal = 32       // enough headroom for N workers doing brpop + concurrent ops
            maxIdle = 16
            minIdle = 4
            testOnBorrow = true
        },
        "localhost", 6379
    )

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
    suspend fun VisitedSize():Long=withContext(Dispatchers.IO){

        pool.resource.use {jedis->
            jedis.scard("visited:urls")

        }
    }

    suspend fun cacheRobotsTxt(domain:String,content:String,ttl:Long=86400)=withContext(Dispatchers.IO){
        pool.resource.use { jedis ->
            jedis.setex("robots:$domain",ttl,content)
        }
    }

    suspend fun getCachedRobotsTxt(domain:String):String?=withContext(Dispatchers.IO){
        pool.resource.use{jedis ->
            jedis.get("robots:$domain")
        }

    }

    suspend fun isDomainAvailable(domain:String,delayMs: Long): Boolean=withContext(Dispatchers.IO){
        if(delayMs<=0){
            return@withContext true
        }
        pool.resource.use { jedis ->
            val key = "ratelimit:$domain"
            val now = System.currentTimeMillis()

            val expire = now+delayMs
            val result = jedis.set(key, expire.toString(),redis.clients.jedis.params.SetParams().nx().px(delayMs))

            result!=null
        }
    }

    fun delete(key:String){
        pool.resource.use{jedis->
            jedis.del(key)
        }
    }

    fun close() = pool.close()



}