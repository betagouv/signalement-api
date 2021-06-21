package actors

import java.io.BufferedInputStream
import java.net.URL
import java.time.OffsetDateTime
import java.util.UUID
import java.util.zip.ZipInputStream
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.{Framing, Keep, Sink, StreamConverters}
import akka.stream.{KillSwitches, ThrottleMode, UniqueKillSwitch}
import akka.util.ByteString
import com.google.inject.AbstractModule

import javax.inject.{Inject, Singleton}
import models.EnterpriseImportInfo
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import repositories._

import scala.concurrent.duration._
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
                                stream: UniqueKillSwitch,
)

@Singleton
class EnterpriseSyncActor @Inject()(
  enterpriseSyncInfoRepo: EnterpriseImportInfoRepository,
)(
  implicit val executionContext: ExecutionContext
) extends Actor {

  import EnterpriseSyncActor._
  implicit val actorSystem: ActorSystem = context.system

  private[this] val logger = Logger(this.getClass)
  private[this] lazy val batchSize = 5000


  private[this] var processedFiles: Map[String, ProcessedFile] = Map()

  override def receive = {
    case Start(name, url, approximateLinesCount, mapper, action, onEnd) => {
      cancel(name).map { _ =>
        val generatedInfoId = UUID.randomUUID()
        enterpriseSyncInfoRepo.create(EnterpriseImportInfo(
          id = generatedInfoId,
          fileName = name,
          fileUrl = url,
          linesCount = approximateLinesCount,
        ))
        val killSwitch = streamFile(
          url,
          mapper,
          action,
          onLinesDone = (linesDone: Double) => enterpriseSyncInfoRepo.updateLinesDone(generatedInfoId, linesDone),
          onComplete = () => enterpriseSyncInfoRepo.updateEndedAt(generatedInfoId).map(_ => onEnd()),
          onFailure = (t: Throwable) => {
            logger.debug(s"Error occurred while importing $url" + t)
            enterpriseSyncInfoRepo.updateError(generatedInfoId, t.toString).map(_ => onEnd())
          }
        )

        processedFiles = processedFiles + (name -> ProcessedFile(
          infoId = generatedInfoId,
          stream = killSwitch,
        ))
        generatedInfoId
      }
    }
    case Cancel(name) => {
      cancel(name)
    }
  }

  private[this] def cancel(name: String) = {
    for {
      _ <- Future.successful(processedFiles.get(name).map(processFile => {
        processFile.stream.shutdown()
        processedFiles = processedFiles - name
      }))
    _ <- enterpriseSyncInfoRepo.updateAllError(name, "<CANCELLED>")
   _ <-  enterpriseSyncInfoRepo.updateAllEndedAt(name, OffsetDateTime.now)
    } yield ()
  }

  private[this] def streamFile[T](
    url: String, mapper: Seq[String] => T,
    action: T => Future[Any],
    onLinesDone: Double => Unit,
    onComplete: () => Future[_],
    onFailure: Throwable => Future[_],
  )  = {
    logger.debug(s"Start importing from ${url}")
    var linesDone = 0d

    val inputstream = new ZipInputStream(new BufferedInputStream(new URL(url).openStream()))
    inputstream.getNextEntry

      val (killSwitch, stream) = StreamConverters.fromInputStream(() => inputstream)
        .throttle(batchSize, 1.second, 1, ThrottleMode.Shaping)
        .via(Framing.delimiter(ByteString("\n"), 4096))
        .map(_.utf8String)
        .drop(1)
        .grouped(batchSize)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.foreach[Seq[String]] { (lines: Seq[String]) =>
          action(mapper(lines)).map(_ => {
            linesDone = linesDone + lines.size
            onLinesDone(linesDone)
          })
        })(Keep.both)
        .run()

      stream
        .flatMap(_ => onComplete())
        .recover { case t =>
          onFailure(t).map(_ => killSwitch.shutdown())
        }

      killSwitch
  }
}
