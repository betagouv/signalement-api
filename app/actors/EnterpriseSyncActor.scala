package actors

import java.io.FileInputStream
import java.time.OffsetDateTime
import java.util.UUID

import akka.actor.{Actor, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Framing, StreamConverters}
import akka.util.ByteString
import com.google.inject.AbstractModule
import javax.inject.{Inject, Singleton}
import models.{EnterpriseSyncInfo, EnterpriseSyncInfoUpdate}
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import repositories._

import scala.concurrent.{ExecutionContext, Future}

object EnterpriseSyncActor {
  def props = Props[EnterpriseSyncActor]

  sealed trait Command

  final case class Start[T](
    name: String,
    url: String,
    approximateLinesCount: Double, // TODO Should be calculated
    mapper: Seq[String] => T,
    action: T => Future[Any],
  ) extends Command

  final case class Stop(name: String) extends Command

}

class EnterpriseSyncActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure = {
    bindActor[EnterpriseSyncActor]("enterprise-sync-actor")
  }
}

final case class ProcessedFile(
  infoId: UUID,
  stream: {def close(): Unit},
)

@Singleton
class EnterpriseSyncActor @Inject()(
  enterpriseSyncInfoRepository: EnterpriseSyncInfoRepository,
)(
  implicit val executionContext: ExecutionContext
) extends Actor {

  import EnterpriseSyncActor._

  private[this]      val logger    = Logger(this.getClass)
  private[this] lazy val batchSize = 1
  implicit           val mat       = ActorMaterializer()

  private[this] var processedFiles: Map[String, ProcessedFile] = Map()

  override def receive = {
    case Start(name, url, approximateLinesCount, mapper, action) => {
      val generatedInfoId = UUID.randomUUID()
      val future          = enterpriseSyncInfoRepository.create(EnterpriseSyncInfo(
        id = generatedInfoId,
        fileName = url,
        fileUrl = url,
        linesCount = approximateLinesCount,
      ))
      processedFiles = processedFiles + (name -> ProcessedFile(
        infoId = generatedInfoId,
        stream = streamFile(url, mapper, action, (linesDone: Double) => {
          enterpriseSyncInfoRepository.update(generatedInfoId, EnterpriseSyncInfoUpdate(linesDone = Some(linesDone)))
        })
      ))
      future
    }
    case Stop(name) => {
      processedFiles.get(name).map(processFile => {
        processFile.stream.close()
        enterpriseSyncInfoRepository.update(processFile.infoId, EnterpriseSyncInfoUpdate(endedAt = Some(OffsetDateTime.now)))
        processedFiles = processedFiles - name
      })
    }
  }

  private[this] def streamFile[T](url: String, mapper: Seq[String] => T, action: T => Future[Any], onLinesDone: Double => Unit): {def close(): Unit} = {
    logger.debug(s"Start dumping from ${url}")
    var linesDone = 0d

    //      Source.fromInputStream(new FileInputStream(file.url))
    val inputstream = new FileInputStream(url)
    //    val inputstream = new ZipInputStream(new BufferedInputStream(new URL(url).openStream()))
    //    Stream.continually(inputstream.getNextEntry).takeWhile(_ != null).foreach { zipFile =>
    //      Source.fromInputStream(inputstream)
    StreamConverters.fromInputStream(() => inputstream)
      .via(Framing.delimiter(ByteString("\n"), 4096))
      .map(_.utf8String)
      .drop(1)
      .log("error logging")
      .grouped(batchSize)
      .runForeach(lines => {
        action(mapper(lines)).map(x => {
          linesDone = linesDone + lines.size
          onLinesDone(linesDone)
        })
      })
    inputstream
  }
}