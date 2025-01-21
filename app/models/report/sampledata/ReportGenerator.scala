package models.report.sampledata

import models.company.Company
import models.report.ReportDraft
import models.report.ReportCategory.AchatInternet
import models.report.ReportCategory.DemarchesAdministratives
import models.report.ReportTag.CommandeEffectuee
import models.report.ReportTag.Internet
import models.report.ReportTag.LitigeContractuel
import models.report.sampledata.SampleDataUtils.buildSampleReport
import models.report.sampledata.SampleDataUtils.randomConsumerUser
import utils.URL

import scala.util.Random

object ReportGenerator {

  def generateRandomNumberOfReports(company: Company, reportsAmountFactor: Double = 1): List[ReportDraft] = {
    val n = Math.max(1, Math.round(Random.between(1, 4) * reportsAmountFactor).toInt)
    (1 to n).map(_ => generateRandomReport(company)).toList
  }

  private def generateRandomReport(company: Company) = {
    val randomReportsList = List(
      // We try to keep realistic data
      // Those are real reports taken from prod, with names, urls, amounts etc. changed for anonymity
      buildSampleReport(
        company,
        conso = randomConsumerUser(),
        category = AchatInternet,
        subcategories = List("Une_commande_effectuee", "Commande_jamais_livree_et_le_site_est_toujours_ouvert"),
        tags = List(LitigeContractuel, Internet, CommandeEffectuee),
        details = List(
          "Description :" -> "J'ai commandé un oreiller boba paris  sur le site instagram  pour une pub d oreiller depuis le 10 décembre 2024 pas de téléphone pour les contacter juste une adresse mail envoyer deux mails aucune réponse carte débité de 43.80 euros merci"
        ),
        website = Some(URL("https://boba-paris.com"))
      ),
      buildSampleReport(
        company,
        conso = randomConsumerUser().copy(contactAgreement = false),
        category = DemarchesAdministratives,
        subcategories =
          List("Site_internet_pour_obtenir_un_document_administratif_casier_acte_de_naissance_vignette_CritAir"),
        tags = List(Internet),
        details = List(
          "Description :" -> "Site bénéficiant d'un agrément du ministère de l'intérieur (213900). Entretient savamment la confusion avec un service public dans sa communication. Une fois hameçonné, tout est mis en oeuvre pour prélever indûment de l'argent, notamment un abonnement mensuel de 12,90 euros dont vous ne savez même pas que vous y avez souscrit. Pour le résilier, le site vous demande de vous désabonner dans votre espace client, or la FONCTIONNALITÉ DE DÉSABONNEMENT n'apparaît nulle part. Le client est obligé de batailler à coup de mails pour obtenir gain de cause. Devant tant de malhonnêteté et de mauvaise foi, j'ai demandé la rétraction de ma carte grise.  L'entreprise y souscrit mais retient 62,99 euros avec des arguments fumeux, se réfugiant derrière une création de frais de dossier. La démarche téléphonique est payante (0,80 euro la minute) et cinq bonnes minutes s'écoulent avant que vous soyez pris en ligne. Bref, un site que je qualifierai de malhonnête, sur lequel tout est mis en oeuvre pour 'ponctionner\" de l'argent en misant sur le manque de vigilance induit par une apparence de sérieux. Il est difficile de concevoir que le ministère de l'intérieur puisse accorder un agrément à des sociétés aussi peu scrupuleuses de l'honnêteté que l'on est en droit d'attendre pour l'obtention d'un document administratif.\nAprès consultation (hélas pas avant), les avis internet concernant ce site sont éloquents, 2 étoiles sur 5 et le problème de l'abonnement abusivement imposé et non résiliable en ligne souvent évoqué. \nJe procède ce jour à un signalement auprès du ministère de l'intérieur .\nCordialement.\nWilfried Lejeune\nN° dossier X2875929"
        ),
        website = Some(URL("https://sitepouravoircartegrise.com"))
      )
    )
    Random.shuffle(randomReportsList).head
  }

}
