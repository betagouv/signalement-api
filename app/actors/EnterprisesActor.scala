package actors

import java.io.FileInputStream
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import akka.actor.{Actor, Props}
import com.google.inject.AbstractModule
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import repositories._
import utils.SIREN

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

object EnterprisesActor {
  def props = Props[EnterprisesActor]

  case object EnterpriseActorSync

}


class EnterprisesActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure = {
    bindActor[EnterprisesActor]("enterprise-actor")
  }
}

case class StockFile[T](
  url: String,
  // TODO Should be calculated
  approximateLinesCount: Double,
  mapper: Seq[String] => T,
  action: T => Future[Any],
)

@Singleton
class EnterprisesActor @Inject()(
  companyDataRepository: CompanyDataRepository,
)(
  implicit val executionContext: ExecutionContext
) extends Actor {

  import EnterprisesActor._

  private[this]      val logger    = Logger(this.getClass)
  private[this] lazy val batchSize = 1

  private[this] val stockEtablissementFile = StockFile(
    url = s"/Users/alexandreac/Workspace/signalconso/signalement-api/HEAD_StockEtablissement_utf8.csv",
    //    url = s"https://files.data.gouv.fr/insee-sirene/StockEtablissement_utf8.zip",
    approximateLinesCount = 32e6,
    mapper = (lines: Seq[String]) => {
      lines.map(getByColumnName(CSVStockFilesColumns.etablissement)).map(getValue => {
        Map(
          "siret" -> getValue("siret"),
          "siren" -> getValue("siren"),
          "datederniertraitementetablissement" -> getValue("datederniertraitementetablissement"),
          "etablissementsiege" -> getValue("etablissementsiege"),
          "complementadresseetablissement" -> getValue("complementadresseetablissement"),
          "numerovoieetablissement" -> getValue("numerovoieetablissement"),
          "indicerepetitionetablissement" -> getValue("indicerepetitionetablissement"),
          "typevoieetablissement" -> getValue("typevoieetablissement"),
          "libellevoieetablissement" -> getValue("libellevoieetablissement"),
          "codepostaletablissement" -> getValue("codepostaletablissement"),
          "libellecommuneetablissement" -> getValue("libellecommuneetablissement"),
          "libellecommuneetrangeretablissement" -> getValue("libellecommuneetrangeretablissement"),
          "distributionspecialeetablissement" -> getValue("distributionspecialeetablissement"),
          "codecommuneetablissement" -> getValue("codecommuneetablissement"),
          "codecedexetablissement" -> getValue("codecedexetablissement"),
          "libellecedexetablissement" -> getValue("libellecedexetablissement"),
          "denominationusuelleetablissement" -> getValue("denominationusuelleetablissement"),
          "enseigne1etablissement" -> getValue("enseigne1etablissement"),
          "activiteprincipaleetablissement" -> getValue("activiteprincipaleetablissement"),
          "etatadministratifetablissement" -> getValue("etatadministratifetablissement"),
        )
      })
    }: Seq[Map[String, Option[String]]],
    action = companyDataRepository.insertAll
  )

  private[this] val stockUniteLegaleFile = StockFile(
    //    url = s"https://files.data.gouv.fr/insee-sirene/StockUniteLegale_utf8.zip",
    url = s"/Users/alexandreac/Workspace/signalconso/signalement-api/HEAD_StockUniteLegale_utf8.csv",
    approximateLinesCount = 23e6,
    mapper = (lines: Seq[String]) => {
      lines
        .map(getByColumnName(CSVStockFilesColumns.uniteLegale))
        .flatMap(getValue => {
          getValue("siren").map(siren => {
            val enterpriseName = getValue("denominationunitelegale")
              .orElse(getValue("denominationusuelle1unitelegale"))
              .orElse(getValue("denominationusuelle2unitelegale"))
              .orElse(getValue("denominationusuelle3unitelegale"))
              .getOrElse(getValue("prenomusuelunitelegale").getOrElse("") + " " + getValue("nomusageunitelegale").orElse(getValue("nomunitelegale")).getOrElse(""))
            (SIREN(siren), enterpriseName)
          })
        })
    },
    action = companyDataRepository.updateNames
  )

  val files = Seq(
    stockEtablissementFile,
    stockUniteLegaleFile
  )

  override def receive = {
    case EnterpriseActorSync => {
      files.foreach(streamFile(_))
    }
  }

  private[this] def getByColumnName(columns: Seq[String]) = {
    val indexedColumns = columns.zipWithIndex.toMap
    (line: String) => {
      val items = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)")
      (columnName: String) => {
        indexedColumns.get(columnName)
          .flatMap(items.lift)
          .map(removeQuotesAround)
          .collect { case x if x.trim.nonEmpty => x }
      }: Option[String]
    }
  }

  private[this] def removeQuotesAround(str: String): String = {
    val pattern = "^\"(.*?)\"".r
    str match {
      case pattern(x) => x
      case _ => str
    }
  }
  //  def sync() = {
  //    val uniteleg = "/Users/alexandreac/Workspace/signalconso/signalement-api/HEAD_StockUniteLegale_utf8.csv"
  //    val eta      = "/Users/alexandreac/Workspace/signalconso/signalement-api/HEAD_StockEtablissement_utf8.csv"
  //    //    streamFile(uniteleg, insertCompanyData)
  //    streamFile(eta, updateDenominationEtablissement)
  //  }

  //  def streamFile(fileUrl: String, action: Seq[String] => Unit) = {
  ////    Source.fromInputStream(new FileInputStream(fileUrl)).getLines().drop(1).grouped(batchSize).foreach(action)
  //
  //        val inputstream = new ZipInputStream(new BufferedInputStream(new URL(fileUrl).openStream()))
  //        Stream.continually(inputstream.getNextEntry).takeWhile(_ != null).foreach { file =>
  //          Source.fromInputStream(inputstream).getLines().drop(1).grouped(batchSize).foreach(action)
  //        }
  //  }


  def streamFile[T](file: StockFile[T]) = {
    logger.debug(s"Start dumping from ${file.url}")

    var linesDone = 0

    val scheduledTask = new ScheduledThreadPoolExecutor(1).scheduleAtFixedRate(() => println(linesDone), 1, 5, TimeUnit.SECONDS)

    Source.fromInputStream(new FileInputStream(file.url))
      //    val inputstream = new ZipInputStream(new BufferedInputStream(new URL(file.url).openStream()))
      //    Stream.continually(inputstream.getNextEntry).takeWhile(_ != null).foreach { zipFile =>
      //      Source.fromInputStream(inputstream)
      .getLines().drop(1).grouped(batchSize).foreach(lines => {
      linesDone = linesDone + 1
      file.action(file.mapper(lines)).map(x => linesDone = linesDone + lines.size)
    })
    //    }
    scheduledTask.cancel(false)
  }
}

object CSVStockFilesColumns {
  val etablissement = Seq(
    "siren",
    "nic",
    "siret",
    "statutdiffusionetablissement",
    "datecreationetablissement",
    "trancheeffectifsetablissement",
    "anneeeffectifsetablissement",
    "activiteprincipaleregistremetiersetablissement",
    "datederniertraitementetablissement",
    "etablissementsiege",
    "nombreperiodesetablissement",
    "complementadresseetablissement",
    "numerovoieetablissement",
    "indicerepetitionetablissement",
    "typevoieetablissement",
    "libellevoieetablissement",
    "codepostaletablissement",
    "libellecommuneetablissement",
    "libellecommuneetrangeretablissement",
    "distributionspecialeetablissement",
    "codecommuneetablissement",
    "codecedexetablissement",
    "libellecedexetablissement",
    "codepaysetrangeretablissement",
    "libellepaysetrangeretablissement",
    "complementadresse2etablissement",
    "numerovoie2etablissement",
    "indicerepetition2etablissement",
    "typevoie2etablissement",
    "libellevoie2etablissement",
    "codepostal2etablissement",
    "libellecommune2etablissement",
    "libellecommuneetranger2etablissement",
    "distributionspeciale2etablissement",
    "codecommune2etablissement",
    "codecedex2etablissement",
    "libellecedex2etablissement",
    "codepaysetranger2etablissement",
    "libellepaysetranger2etablissement",
    "datedebut",
    "etatadministratifetablissement",
    "enseigne1etablissement",
    "enseigne2etablissement",
    "enseigne3etablissement",
    "denominationusuelleetablissement",
    "activiteprincipaleetablissement",
    "nomenclatureactiviteprincipaleetablissement",
    "caractereemployeuretablissement"
  )

  val uniteLegale = Seq(
    "siren",
    "statutdiffusionunitelegale",
    "unitepurgeeunitelegale",
    "datecreationunitelegale",
    "sigleunitelegale",
    "sexeunitelegale",
    "prenom1unitelegale",
    "prenom2unitelegale",
    "prenom3unitelegale",
    "prenom4unitelegale",
    "prenomusuelunitelegale",
    "pseudonymeunitelegale",
    "identifiantassociationunitelegale",
    "trancheeffectifsunitelegale",
    "anneeeffectifsunitelegale",
    "datederniertraitementunitelegale",
    "nombreperiodesunitelegale",
    "categorieentreprise",
    "anneecategorieentreprise",
    "datedebut",
    "etatadministratifunitelegale",
    "nomunitelegale",
    "nomusageunitelegale",
    "denominationunitelegale",
    "denominationusuelle1unitelegale",
    "denominationusuelle2unitelegale",
    "denominationusuelle3unitelegale",
    "categoriejuridiqueunitelegale",
    "activiteprincipaleunitelegale",
    "nomenclatureactiviteprincipaleunitelegale",
    "nicsiegeunitelegale",
    "economiesocialesolidaireunitelegale",
    "caractereemployeurunitelegale"
  )
}