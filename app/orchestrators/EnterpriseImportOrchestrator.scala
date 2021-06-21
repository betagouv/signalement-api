package orchestrators

import actors.EnterpriseSyncActor
import akka.actor.ActorRef
import akka.pattern.ask
import javax.inject.{Inject, Named}
import models.{CompanyData, EnterpriseImportInfo}
import repositories.{CompanyDataRepository, EnterpriseImportInfoRepository}
import utils.{SIREN, SIRET}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


class EnterpriseImportOrchestrator @Inject()(
  enterpriseSyncInfoRepository: EnterpriseImportInfoRepository,
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

  private[this] lazy val startEtablissementFileActor = EnterpriseSyncActor.Start(
    name = etablissementActorName,
    url = s"https://files.data.gouv.fr/insee-sirene/StockEtablissement_utf8.zip",
    approximateLinesCount = 32e6,
    mapper = (lines: Seq[String]) => lines
      .map(getByColumnName(CSVFilesColumns.etablissement))
      .filter(getValue => getValue("siret").isDefined && getValue("siren").isDefined)
      .map(getValue => CompanyData(
        siret = SIRET(getValue("siret").get),
        siren = SIREN(getValue("siren").get),
        dateDernierTraitementEtablissement = getValue("datederniertraitementetablissement"),
        etablissementSiege = getValue("etablissementsiege"),
        complementAdresseEtablissement = getValue("complementadresseetablissement"),
        numeroVoieEtablissement = getValue("numerovoieetablissement"),
        indiceRepetitionEtablissement = getValue("indicerepetitionetablissement"),
        typeVoieEtablissement = getValue("typevoieetablissement"),
        libelleVoieEtablissement = getValue("libellevoieetablissement"),
        codePostalEtablissement = getValue("codepostaletablissement"),
        libelleCommuneEtablissement = getValue("libellecommuneetablissement"),
        libelleCommuneEtrangerEtablissement = getValue("libellecommuneetrangeretablissement"),
        distributionSpecialeEtablissement = getValue("distributionspecialeetablissement"),
        codeCommuneEtablissement = getValue("codecommuneetablissement"),
        codeCedexEtablissement = getValue("codecedexetablissement"),
        libelleCedexEtablissement = getValue("libellecedexetablissement"),
        denominationUsuelleEtablissement = getValue("denominationusuelleetablissement"),
        enseigne1Etablissement = getValue("enseigne1etablissement"),
        activitePrincipaleEtablissement = getValue("activiteprincipaleetablissement").getOrElse(""),
        etatAdministratifEtablissement = getValue("etatadministratifetablissement"),
      )),
    action = companyDataRepository.insertAllRaw
  )

  private[this] lazy val startUniteLegaleFileActor = EnterpriseSyncActor.Start(
    name = uniteLegaleActorName,
    url = s"https://files.data.gouv.fr/insee-sirene/StockUniteLegale_utf8.zip",
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

  def getLastEtablissementImportInfo(): Future[Option[EnterpriseImportInfo]] = {
    enterpriseSyncInfoRepository.findLast(etablissementActorName)
  }

  def getUniteLegaleImportInfo(): Future[Option[EnterpriseImportInfo]] = {
    enterpriseSyncInfoRepository.findLast(uniteLegaleActorName)
  }

  def startEtablissementFile = {
    enterpriseActor ? startEtablissementFileActor
  }

  def startUniteLegaleFile = {
    enterpriseActor ? startUniteLegaleFileActor
  }

  def cancelEntrepriseFile = {
    enterpriseActor ? EnterpriseSyncActor.Cancel(etablissementActorName)
  }

  def cancelUniteLegaleFile = {
    enterpriseActor ? EnterpriseSyncActor.Cancel(uniteLegaleActorName)
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
