package models.report.sampledata

import models.report.ReportCategory.AchatInternet
import models.report.ReportCategory.AchatMagasin
import models.report.ReportCategory.CafeRestaurant
import models.report.ReportCategory.DemarchageAbusif
import models.report.ReportCategory.DemarchesAdministratives
import models.report.ReportCategory.Immobilier
import models.report.ReportCategory
import models.report.ReportTag
import models.report.ReportTag.AppelCommercial
import models.report.ReportTag.CommandeEffectuee
import models.report.ReportTag.DemarchageTelephonique
import models.report.ReportTag.Hygiene
import models.report.ReportTag.Internet
import models.report.ReportTag.LitigeContractuel
import models.report.ReportTag.ProduitAlimentaire
import models.report.ReportTag.ProduitDangereux
import models.report.ReportTag.ReponseConso
import models.report.sampledata.ConsoUserGenerator.ConsumerUser
import models.report.sampledata.ConsoUserGenerator.randomConsumerUser
import utils.URL

import scala.util.Random

object ReportGenerator {

  val sampleGtin = "3474341105842"

  case class SampleReportBlueprint(
      conso: ConsumerUser,
      category: ReportCategory,
      tags: List[ReportTag],
      details: Seq[(String, String)],
      subcategories: List[String],
      website: Option[URL] = None,
      phone: Option[String] = None,
      barcodeProductGtin: Option[String] = None,
      employeeConsumer: Boolean = false
  )

  def generateRandomNumberOfReports(
      reportsAmountFactor: Double = 1
  ): List[SampleReportBlueprint] = {
    val n = Math.max(1, Math.round(Random.between(1, 4) * reportsAmountFactor).toInt)
    (1 to n).map(_ => generateRandomReport()).toList
  }

  private def generateRandomReport(): SampleReportBlueprint = {

    // We try to keep realistic data
    // Those are real reports taken from prod, with names, urls, amounts etc. changed for anonymity

    val reportSomethingNotDelivered =
      SampleReportBlueprint(
        conso = randomConsumerUser(),
        category = AchatInternet,
        subcategories = List("Une_commande_effectuee", "Commande_jamais_livree_et_le_site_est_toujours_ouvert"),
        tags = List(LitigeContractuel, Internet, CommandeEffectuee),
        details = List(
          "Description :" -> "J'ai commandé un oreiller boba paris  sur le site instagram  pour une pub d oreiller depuis le 10 décembre 2024 pas de téléphone pour les contacter juste une adresse mail envoyer deux mails aucune réponse carte débité de 43.80 euros merci"
        ),
        website = Some(URL("https://boba-paris.com"))
      )

    val reportDemarcheAdministratives = SampleReportBlueprint(
      conso = randomConsumerUser(contactAgreement = false),
      category = DemarchesAdministratives,
      subcategories =
        List("Site_internet_pour_obtenir_un_document_administratif_casier_acte_de_naissance_vignette_CritAir"),
      tags = List(Internet),
      details = List(
        "Description :" -> "Site bénéficiant d'un agrément du ministère de l'intérieur (213900). Entretient savamment la confusion avec un service public dans sa communication. Une fois hameçonné, tout est mis en oeuvre pour prélever indûment de l'argent, notamment un abonnement mensuel de 12,90 euros dont vous ne savez même pas que vous y avez souscrit. Pour le résilier, le site vous demande de vous désabonner dans votre espace client, or la FONCTIONNALITÉ DE DÉSABONNEMENT n'apparaît nulle part. Le client est obligé de batailler à coup de mails pour obtenir gain de cause. Devant tant de malhonnêteté et de mauvaise foi, j'ai demandé la rétraction de ma carte grise.  L'entreprise y souscrit mais retient 62,99 euros avec des arguments fumeux, se réfugiant derrière une création de frais de dossier. La démarche téléphonique est payante (0,80 euro la minute) et cinq bonnes minutes s'écoulent avant que vous soyez pris en ligne. Bref, un site que je qualifierai de malhonnête, sur lequel tout est mis en oeuvre pour 'ponctionner\" de l'argent en misant sur le manque de vigilance induit par une apparence de sérieux. Il est difficile de concevoir que le ministère de l'intérieur puisse accorder un agrément à des sociétés aussi peu scrupuleuses de l'honnêteté que l'on est en droit d'attendre pour l'obtention d'un document administratif.\nAprès consultation (hélas pas avant), les avis internet concernant ce site sont éloquents, 2 étoiles sur 5 et le problème de l'abonnement abusivement imposé et non résiliable en ligne souvent évoqué. \nJe procède ce jour à un signalement auprès du ministère de l'intérieur .\nCordialement.\nWilfried Lejeune\nN° dossier X2875929"
      ),
      website = Some(URL("https://sitepouravoircartegrise.com"))
    )

    val reportDemarcheTelephonique = SampleReportBlueprint(
      conso = randomConsumerUser(contactAgreement = false, phone = Some("0627339834")),
      category = DemarchageAbusif,
      subcategories = List(
        "Probleme_de_demarchage_telephonique",
        "Jai_recu_au_moins_5_appels_de_la_meme_entreprise_sur_les_30_derniers_jours"
      ),
      tags = List(DemarchageTelephonique, AppelCommercial),
      details = List(
        "Date du premier appel :"                                             -> "05/01/2025",
        "Date du second appel :"                                              -> "06/01/2025",
        "Date du troisième appel :"                                           -> "20/01/2025",
        "Date du quatrième appel :"                                           -> "21/01/2025",
        "Date du cinquième appel :"                                           -> "21/01/2025",
        "Est-ce que le vendeur s'est fait passer pour une autre entreprise :" -> "Non",
        "Description :" -> "je suis appelée une dizaine de fois /jour, d'un numéro pas totalement identique ainsi que mon époux, depuis environ un mois voir plus.\n0567195499\n0567195117\n0567281455\n0567195512"
      ),
      phone = Some("0165194512")
    )

    val reportProduitDangereuxAlimentaire = SampleReportBlueprint(
      conso = randomConsumerUser(contactAgreement = false),
      category = AchatMagasin,
      subcategories = List(
        "Un_produit_dangereux",
        "Produit_alimentaire",
        "Autre",
        "Allergie"
      ),
      tags = List(ProduitDangereux, ProduitAlimentaire),
      details = List(
        "Date de l'accident :"                                                                   -> "15/01/2025",
        "La personne qui a consommé l'aliment est-elle tombée malade et/ou s'est-elle blessée :" -> "Non",
        "Avez-vous déjà contacté le commerçant ou le fabricant pour ce problème :"               -> "Oui",
        "Description :" -> "Bonjour, Je souhaite signaler une erreur d'étiquetage d'un pain qui était censé être un pain aux figues mais qui était un pain \"sportif\" dans un mauvais emballage. Nous l'avons acheté à FRANCHOUILLE MAGASIN à Anse (69480) et c'est la deuxième fois que cela nous arrive. \nJe tiens à préciser que le pain sportif contient des fruits à coque, des allergènes qui peuvent provoquer un choc anaphylactique aux gens allergiques comportant un risque vital. Je considère cette erreur du Franchouille gravissime. Cela doit être signalé.\nCordialement"
      ),
      barcodeProductGtin = Some(sampleGtin)
    )

    val reportReponseConso = SampleReportBlueprint(
      conso = randomConsumerUser(contactAgreement = false),
      category = Immobilier,
      subcategories = List("Agence immobilière", "Jai_un_probleme_en_tant_que_locataire", "Charges_honoraires"),
      tags = List(ReponseConso),
      details = List(
        "Date du constat :" -> "20/01/2025",
        "Description :" -> "Je rejoins une collocation, ce qui nécessite de générer un avenant au contrat pour changement de locataire; l'agence réclame 450€ pour cet avenant ; alors qu'il n'y a aucune démarche à faire à part la génération de l'avenant (pas d'état des lieux, pas de déplacement, etc.)",
        "Votre question :" -> "Puis-je demander la réduction de ces frais? \nMerci,"
      )
    )

    val reportInformateurInterneHygiene = SampleReportBlueprint(
      conso = randomConsumerUser(contactAgreement = false),
      category = CafeRestaurant,
      subcategories = List("Hygiène", "Probleme_de_temperature"),
      tags = List(Hygiene),
      details = List(
        "Date du constat :"               -> "10/03/2024",
        "Heure du constat (facultatif) :" -> "de 8h à 9h",
        "Quel est le problème :"          -> "Stockage à température ambiante de produits frais ou congelés",
        "Pouvez-vous préciser :" -> "Armoire positive en panne , venaison chasse ne passe par des contrôles sanitaires.",
        "Description :" -> "Bonjour,  je travaillais l année dernière dans cette établissement,  le patron mr Champ pignon et chasseur , il tue sont propre gibier et le mets directement à la consommation dans son établissement, sans contrôle vétérinaire,  le gibier et stocker dans un congélateur , aucune date sur le sous vide , cerf et sanglier  , l état des congélateur et lamentable,  du sang partout , les chaînes du froid ne sont pas respecté le gibier et transvaser de véhicule en véhicule pour atterrir dans les congélateur. De plus l état de la cuisine à l époque laisse à désiré. Autres point les grenouilles cuisses,  arrivée fraîche, mérite d être contrôler ce monsieur possede un étang mais je doute que toutes les grenouilles viennent du même étang,  abattage dans une caves par des retraités. Conditionnement boîte type plastique à emporter,  chaînes du froids non respecté,  pas de dlc , quand trop de quantité congélateur,  congelé, décongelée, et recongeler !!!"
      ),
      employeeConsumer = true
    )

    Random
      .shuffle(
        List(
          reportSomethingNotDelivered,
          reportDemarcheAdministratives,
          reportDemarcheTelephonique,
          reportProduitDangereuxAlimentaire,
          reportReponseConso,
          reportInformateurInterneHygiene
        )
      )
      .head

  }

}
