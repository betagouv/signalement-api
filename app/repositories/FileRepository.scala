package repositories

import java.io.InputStream
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.Report
import org.postgresql.PGConnection
import org.postgresql.largeobject.LargeObjectManager
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.{JdbcProfile, SetParameter}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  val bufferSize: Int = 4096

  def uploadFile(inputStream: InputStream): Future[Option[Long]] = {

    db.run {
      SimpleDBIO { khan =>
        khan.connection.setAutoCommit(false)
        val largeObjectApi = khan.connection.unwrap(classOf[PGConnection]).getLargeObjectAPI
        val largeObjectId = largeObjectApi.createLO()
        val largeObject = largeObjectApi.open(largeObjectId, LargeObjectManager.WRITE)

        val bytes = new Array[Byte](bufferSize)

        Iterator.continually {
          val bytesRead = inputStream.read(bytes)
          val bytesToWrite = if (bytesRead <= 0) {
            //nothing was read, so just return an empty byte array
            new Array[Byte](0)
          } else if (bytesRead < bufferSize) {
            //the read operation hit the end of the stream, so remove the unneeded cells
            val actualBytes = new Array[Byte](bytesRead)
            bytes.copyToArray(actualBytes)
            actualBytes
          } else {
            bytes
          }
          largeObject.write(bytesToWrite)
          bytesRead
        }.takeWhile {
          _ > 0
        }.length //call .length to force evaluation
        largeObject.close()
        Some(largeObjectId)
      }
    }
  }
}
