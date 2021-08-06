package io.scalac.githubrank

import com.github.benmanes.caffeine.cache.{CacheLoader, Caffeine, RemovalCause}
import com.google.protobuf.any.Any
import io.scalac.githubrank.grpc._
import org.mapdb.{DBMaker, Serializer}
import org.slf4j.LoggerFactory

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

/**
 * This is an implementation of an in-memory and persistent cache for the Http ETag cache supported by the GitHub API
 * When the GitHub API returns a response code 304 (Not Modified), the data is fetched from the local cache, thus not
 * increasing the rate request limit of the GitHub API! :)
 * @param MAX_ENTRIES Number of max entries in the cache
 * @param ec
 */
class HttpCache(val MAX_ENTRIES: Int)(implicit val ec: ExecutionContext)  {

  protected val db = DBMaker.fileDB("db")
    .transactionEnable()
    .closeOnJvmShutdown()
    .make()

  val entitiesCollection = db.treeMap("responses_cache")
    .keySerializer(Serializer.STRING)
    .valueSerializer(Serializer.BYTE_ARRAY)
    .createOrOpen()

  val logger = LoggerFactory.getLogger(this.getClass)

  val cache = Caffeine.newBuilder()
    .maximumSize(MAX_ENTRIES)
    .executor(ec.asInstanceOf[Executor])
    .removalListener((key: String, value: CacheItem, cause: RemovalCause) => {
      if(cause.wasEvicted())
        logger.debug(s"REMOVING ${key} FROM CACHE $cause\n")
    })
    .build[String, CacheItem](
      // This code loads the cached response for an URL (if not in memory) from the file database
      new CacheLoader[String, CacheItem] {
        override def load(key: String): CacheItem = entitiesCollection.get(key) match {
          case res if res == null => null
          case res =>

            val item = Any.parseFrom(res).unpack(CacheItem)

            logger.info(s"reading from db: ${item}")

            item
        }
    })

  def put(key: String, value: CacheItem): Unit = synchronized {
    cache.put(key, value)
    entitiesCollection.put(key, Any.pack(value).toByteArray)
  }

  def get(key: String): Option[CacheItem] = {
    cache.get(key) match {
      case res if res == null => None
      case res => Some(res)
    }
  }

  def close(): Unit = {
    db.close()
  }

}
