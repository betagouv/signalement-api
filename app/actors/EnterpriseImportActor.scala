package actors

import akka.NotUsed

import java.io.BufferedInputStream
import java.time.OffsetDateTime
import java.util.UUID
import java.util.zip.ZipInputStream
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.{FlowShape, IOResult}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition, Sink, Source, StreamConverters}
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import com.google.inject.AbstractModule

import javax.inject.{Inject, Singleton}
import models.{CompanyFile, EnterpriseImportInfo, EtablissementFile, UniteLegaleFile}
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import repositories._
import utils.SIREN

import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}

object EnterpriseSyncActor {
  def props = Props[EnterpriseSyncActor]

  sealed trait Command

  final case class Start(
    companyFile: CompanyFile
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
  enterpriseSyncInfoRepo: EnterpriseImportInfoRepository,
  companyDataRepository: CompanyDataRepository,
)(
  implicit val executionContext: ExecutionContext
) extends Actor {

  import EnterpriseSyncActor._

  implicit val session = SlickSession.forConfig("slick.dbs.company_db")
  implicit val actorSystem: ActorSystem = context.system
  val batchSize = 50000
  actorSystem.registerOnTermination(() => session.close())

  private[this] val logger = Logger(this.getClass)


  private[this] var processedFiles: Map[String, ProcessedFile] = Map()

  override def receive = {
    case Start(companyFile) =>
      for {
      _ <- cancel(companyFile.name)
      jobId = UUID.randomUUID()
      _ <- enterpriseSyncInfoRepo.create(EnterpriseImportInfo(
        id = jobId,
        fileName = companyFile.name,
        fileUrl = companyFile.url.toString,
        linesCount = companyFile.approximateSize,
      ))
      _ = ingestFile(jobId, companyFile)
      } yield jobId

    case Cancel(name) => cancel(name)

  }

  private[this] def cancel(name: String) = for {
   _ <- enterpriseSyncInfoRepo.updateAllEndedAt(name, OffsetDateTime.now)
    _ <- enterpriseSyncInfoRepo.updateAllError(name, "<CANCELLED>")
    _ = processedFiles.get(name).map(processFile => {
      processFile.stream.close()
      processedFiles = processedFiles - name
    })
  } yield ()


  private[this] def ingestFile[T](
                                   jobId : UUID,
                                   companyFile: CompanyFile
                                 ): ZipInputStream = {
    logger.debug(s"Start importing from ${companyFile.url.toString}")
    var linesDone = 0d

    val inputstream = new ZipInputStream(new BufferedInputStream(companyFile.url.openStream()))
    inputstream.getNextEntry

  val EtablissementIngestionFlow: Flow[Map[String, String], Int, NotUsed] =
    Flow[Map[String,String]]
      .map(_.mapValues(x => {
      Option(x).filter(_.trim.nonEmpty)
    }): Map[String, Option[String]])
    .filter{ columnsValueMap =>
        columnsValueMap.contains("siret") && columnsValueMap.contains("siren")
    }.grouped(batchSize)
      .map { x =>
      println(s"------------------ x ------------------")
      x
    }
    .via(
      Slick.flow(4,group => group.map(companyDataRepository.insertAll(_)).reduceLeft(_.andThen(_)))
    )

  val UniteLegaleIngestionFlow: Flow[Map[String, String], Int, NotUsed] =   Flow[Map[String,String]]
    .map{ columsValueMap =>
      columsValueMap.get("siren").map(siren => {
        val enterpriseName = columsValueMap.get("denominationunitelegale")
          .orElse(columsValueMap.get("denominationusuelle1unitelegale"))
          .orElse(columsValueMap.get("denominationusuelle2unitelegale"))
          .orElse(columsValueMap.get("denominationusuelle3unitelegale"))
          .getOrElse(columsValueMap.getOrElse("prenomusuelunitelegale", "") + " " + columsValueMap.get("nomusageunitelegale").orElse(columsValueMap.get("nomunitelegale")).getOrElse(""))
        (SIREN(siren), enterpriseName)
      })
    }
    .collect {
      case Some(value) => value
    }
    .via(Slick.flow(companyDataRepository.updateName(_)))


    val source: Source[Map[String, String], Future[IOResult]] = StreamConverters.fromInputStream(() => inputstream)
//      .throttle(5000, 1.second, 1, ThrottleMode.Shaping)
      .via(CsvParsing.lineScanner(maximumLineLength = 4096))
      .drop(1)
      .via(CsvToMap.withHeadersAsStrings(StandardCharsets.UTF_8, companyFile.headers: _*))

    val processFileFlow = Flow.fromGraph(GraphDSL.create(){ implicit builder =>

      import GraphDSL.Implicits._

      val partition = builder.add(Partition[Map[String, String]](2, _ => companyFile match {
        case EtablissementFile => 0
        case UniteLegaleFile => 1
      }))

      val merge = builder.add(Merge[Int](inputPorts = 2, eagerComplete = true))

      partition.out(0) ~> EtablissementIngestionFlow ~> merge.in(0)
      partition.out(1) ~> UniteLegaleIngestionFlow ~> merge.in(1)

      FlowShape(partition.in, merge.out)
    })


    val stream = source.via(processFileFlow).map{
      _ =>
        linesDone = linesDone + batchSize
        enterpriseSyncInfoRepo.updateLinesDone(jobId, linesDone)
    }.runWith(Sink.ignore)

    processedFiles = processedFiles + (companyFile.name -> ProcessedFile(
      infoId = jobId,
      stream = inputstream,
    ))

    stream.map(_ => enterpriseSyncInfoRepo.updateEndedAt(jobId))
     .recover { case err =>
       logger.error(s"Error occurred while importing ${companyFile.url}",err)
       enterpriseSyncInfoRepo.updateError(jobId, err.toString)
     }

    inputstream
  }
}
