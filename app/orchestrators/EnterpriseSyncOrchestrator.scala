package orchestrators

import actors.EnterpriseSyncActor
import akka.actor.ActorRef
import akka.pattern.ask
import javax.inject.{Inject, Named}
import repositories.{CompanyDataRepository, EnterpriseSyncInfoRepository}
import utils.SIREN

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


class EnterpriseSyncOrchestrator @Inject()(
  companyDataRepository: CompanyDataRepository,
  @Named("enterprise-sync-actor") enterpriseActor: ActorRef,
)(implicit val executionContext: ExecutionContext) {

  implicit val timeout: akka.util.Timeout = 5.seconds

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

  private[this] val etablissementActorName = "StockEtablissement"

  private[this] val uniteLegaleActorName = "StockUniteLegale"

  private[this] lazy val syncEtablissementFileActor = EnterpriseSyncActor.Start(
    name = etablissementActorName,
    //    url = s"/Users/alexandreac/Workspace/signalconso/HEAD_StockEtablissement_utf8.csv",
    url = s"https://files.data.gouv.fr/insee-sirene/StockEtablissement_utf8.zip",
    approximateLinesCount = 32e6,
    mapper = (lines: Seq[String]) => {
      lines.map(getByColumnName(CSVFilesColumns.etablissement)).map(getValue => {
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
    action = companyDataRepository.insertAll,
    // TODO Don't use because cancelling the stream will tigger onEnd
    //    onEnd = () => enterpriseActor ? syncUniteLegaleFileActor,
  )

  private[this] lazy val syncUniteLegaleFileActor = EnterpriseSyncActor.Start(
    name = uniteLegaleActorName,
    url = s"https://files.data.gouv.fr/insee-sirene/StockUniteLegale_utf8.zip",
    //    url = s"/Users/alexandreac/Workspace/signalconso/HEAD_StockUniteLegale_utf8.csv",
    approximateLinesCount = 23e6,
    mapper = (lines: Seq[String]) => {
      lines
        .map(getByColumnName(CSVFilesColumns.uniteLegale))
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


  def startEntrepriseFile = {
    val x = enterpriseActor ? syncEtablissementFileActor
    x.map(z => println("RETURN TYPE ???" + z))
    x
  }

  def cancelEntrepriseFile = {
    enterpriseActor ? EnterpriseSyncActor.Cancel(etablissementActorName)
  }

  def cancelUniteLegaleFile = {
    enterpriseActor ? EnterpriseSyncActor.Cancel(uniteLegaleActorName)
  }

  def startUniteLegaleFile = {
    enterpriseActor ? syncUniteLegaleFileActor
  }

  private[this] object CSVFilesColumns {
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

}
