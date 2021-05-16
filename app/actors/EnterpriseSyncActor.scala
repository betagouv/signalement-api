package actors

import java.io.{BufferedInputStream, FileInputStream}
import java.net.URL
import java.time.OffsetDateTime
import java.util.UUID
import java.util.zip.ZipInputStream

import akka.actor.{Actor, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Framing, StreamConverters}
import akka.util.ByteString
import com.google.inject.AbstractModule
import javax.inject.{Inject, Singleton}
import models.EnterpriseSyncInfo
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
    onEnd: () => Unit = () => {}
  ) extends Command

  final case class Cancel(name: String) extends Command

}

class EnterpriseSyncActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure = {
    bindActor[EnterpriseSyncActor]("enterprise-sync-actor")
  }
}

final case class ProcessedFile(
  infoId: UUID,
  stream: ZipInputStream,
)

@Singleton
class EnterpriseSyncActor @Inject()(
  enterpriseSyncInfoRepo: EnterpriseSyncInfoRepository,
)(
  implicit val executionContext: ExecutionContext
) extends Actor {

  import EnterpriseSyncActor._

  private[this] val logger = Logger(this.getClass)
  private[this] lazy val batchSize = 10
  implicit val mat: ActorMaterializer = ActorMaterializer()

  private[this] var processedFiles: Map[String, ProcessedFile] = Map()

  override def receive = {
    case Start(name, url, approximateLinesCount, mapper, action, onEnd) => {
      cancel(name)
      val generatedInfoId = UUID.randomUUID()
      enterpriseSyncInfoRepo.create(EnterpriseSyncInfo(
        id = generatedInfoId,
        fileName = name,
        fileUrl = url,
        linesCount = approximateLinesCount,
      ))
      val stream = streamFile(
        url,
        mapper,
        action,
        onLinesDone = (linesDone: Double) => enterpriseSyncInfoRepo.updateLinesDone(generatedInfoId, linesDone),
        onComplete = () => enterpriseSyncInfoRepo.updateEndedAt(generatedInfoId).map(x => onEnd()),
        onFailure = (t: Throwable) => {
          logger.debug(s"Error occurred while importing $url" + t)
          enterpriseSyncInfoRepo.updateError(generatedInfoId, t.toString).map(x => onEnd())
        }
      )

      processedFiles = processedFiles + (name -> ProcessedFile(
        infoId = generatedInfoId,
        stream = stream,
      ))
      println("==" + generatedInfoId)
      generatedInfoId
    }
    case Cancel(name) => {
      cancel(name)
    }
  }

  private[this] def cancel(name: String) = {
    processedFiles.get(name).map(processFile => {
      processFile.stream.close()
      enterpriseSyncInfoRepo.updateEndedAt(processFile.infoId, OffsetDateTime.now)
      enterpriseSyncInfoRepo.updateError(processFile.infoId, "<CANCELLED>")
      processedFiles = processedFiles - name
    })
  }

  private[this] def streamFile[T](
    url: String, mapper: Seq[String] => T,
    action: T => Future[Any],
    onLinesDone: Double => Unit,
    onComplete: () => Future[_],
    onFailure: Throwable => Future[_],
  ): ZipInputStream = {
    logger.debug(s"Start importing from ${url}")
    var linesDone = 0d

    //    val inputstream = new FileInputStream(url)
    val inputstream = new ZipInputStream(new BufferedInputStream(new URL(url).openStream()))
    inputstream.getNextEntry

    StreamConverters.fromInputStream(() => inputstream)
      .via(Framing.delimiter(ByteString("\n"), 4096))
      .map(_.utf8String)
      .drop(1)
      .grouped(batchSize)
      .runForeach(lines => {
        action(mapper(lines)).map(x => {
          linesDone = linesDone + lines.size
          onLinesDone(linesDone)
        })
      })
      .map(x => onComplete())
      .recover { case t => onFailure(t) }

    inputstream
  }
}
