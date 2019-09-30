package controllers

import java.time.{LocalDate, OffsetDateTime}
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.UserRoles.{Admin, Pro}
import models._
import play.api.{Configuration, Environment, Logger}
import repositories._
import services.{MailerService, S3Service}
import tasks.ReminderTask
import utils.Constants.ActionEvent._
import utils.Constants.EventType.PRO
import utils.Constants.StatusPro._
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithPermission}

import scala.concurrent.{ExecutionContext, Future}

class ReminderTestController @Inject()(reportRepository: ReportRepository,
                                       eventRepository: EventRepository,
                                       userRepository: UserRepository,
                                       mailerService: MailerService,
                                       s3Service: S3Service,
                                       val silhouette: Silhouette[AuthEnv],
                                       val silhouetteAPIKey: Silhouette[APIKeyEnv],
                                       configuration: Configuration,
                                       environment: Environment,
                                       reminderTask: ReminderTask)
                                      (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  // date de la forme 2011-12-03

  /**
    * Crée un OffsetDateTime à partir d'une chaîne de caractères
    * @param str une chaîne représentant une date de la forme "2011-12-03"
    */
  private def createDate(str: String) = {
    OffsetDateTime.parse(s"${str}T10:15:30+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  /**
    * Préparation du jeu de test en base de données, afin de lancer le test manuel
    */
  def prepareBeforeTest() = SecuredAction(WithPermission(UserPermission.createEvent)).async { implicit request =>

    // now considéré pour le jeu de test: 26/09/2019

    val fakeUserUuid = UUID.randomUUID()
    val userEmailUuid = UUID.randomUUID()
    val userWithoutEmailUuid = UUID.randomUUID()

    val userWithoutEmail = User(userWithoutEmailUuid, "11111111111111", "", Some("123123"), None, None, Some("test"), Pro)
    val userEmail = User(userEmailUuid, "22222222222222", "", None, Some("user-test@gmail.com"), None, Some("test"), Pro)

    // 1. TRAITEMENT EN COURS  ____________________________________________________________________________________________________

    // report 1 avec un user sans email -------------------------------------------------------------------------------------------
    val r1_uuid = UUID.randomUUID()
    val r1_date = createDate("2019-09-26")
    val r1 = Report(Some(r1_uuid), "test", List.empty, List("détails test"), "company1", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("11111111111111"), Some(r1_date), "r1", "nom 1", "email 1", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // CONTACT_COURRIER plus vieux de 21j
    val r1_e1_uuid = UUID.randomUUID()
    val r1_e1_date = createDate("2019-09-01");
    val r1_e1 = Event(Some(r1_e1_uuid), Some(r1_uuid), fakeUserUuid, Some(r1_e1_date), PRO, CONTACT_COURRIER,
      None, Some("test"))

    // report 2 avec un user sans email -------------------------------------------------------------------------------------------
    val r2_uuid = UUID.randomUUID()
    val r2_date = createDate("2019-09-26")
    val r2 = Report(Some(r2_uuid), "test", List.empty, List("détails test"), "company1", "addresse + id " + UUID.randomUUID().toString,
      None, Some("11111111111111"), Some(r2_date), "r2", "nom 1", "email 1", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // CONTACT_COURRIER moins vieux que 21j
    val r2_e1_uuid = UUID.randomUUID()
    val r2_e1_date = createDate("2019-09-24");
    val r2_e1 = Event(Some(r2_e1_uuid), Some(r2_uuid), fakeUserUuid, Some(r2_e1_date), PRO, CONTACT_COURRIER,
      None, Some("test"))

    // report 3 avec un user avec email -------------------------------------------------------------------------------------------
    val r3_uuid = UUID.randomUUID()
    val r3_date = createDate("2019-09-26")
    val r3 = Report(Some(r3_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r3_date), "r3", "nom 2", "email 2", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // CONTACT_EMAIL plus vieux de 7j
    val r3_e1_uuid = UUID.randomUUID()
    val r3_e1_date = createDate("2019-09-01")
    val r3_e1 = Event(Some(r3_e1_uuid), Some(r3_uuid), fakeUserUuid, Some(r3_e1_date), PRO, CONTACT_EMAIL,
      None, Some("test"))

    // report 4 avec un user avec email -------------------------------------------------------------------------------------------
    val r4_uuid = UUID.randomUUID()
    val r4_date = createDate("2019-09-26")
    val r4 = Report(Some(r4_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r4_date), "r4", "nom 2", "email 2", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // CONTACT_EMAIL moins vieux de 7j
    val r4_e1_uuid = UUID.randomUUID()
    val r4_e1_date = createDate("2019-09-24");
    val r4_e1 = Event(Some(r4_e1_uuid), Some(r4_uuid), fakeUserUuid, Some(r4_e1_date), PRO, CONTACT_EMAIL,
      None, Some("test"))

    // report 5 avec un user sans email -------------------------------------------------------------------------------------------
    val r5_uuid = UUID.randomUUID()
    val r5_date = createDate("2019-09-26")
    val r5 = Report(Some(r5_uuid), "test", List.empty, List("détails test"), "company1", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("11111111111111"), Some(r5_date), "r5", "nom 1", "email 1", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // RELANCE plus vieux de 21j
    val r5_e1_uuid = UUID.randomUUID()
    val r5_e1_date = createDate("2019-09-01");
    val r5_e1 = Event(Some(r5_e1_uuid), Some(r5_uuid), fakeUserUuid, Some(r5_e1_date), PRO, RELANCE,
      None, Some("test"))

    // report 6 avec un user sans email -------------------------------------------------------------------------------------------
    val r6_uuid = UUID.randomUUID()
    val r6_date = createDate("2019-09-26")
    val r6 = Report(Some(r6_uuid), "test", List.empty, List("détails test"), "company1", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("11111111111111"), Some(r6_date), "r6", "nom 1", "email 1", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // RELANCE moins vieux que 21j
    val r6_e1_uuid = UUID.randomUUID()
    val r6_e1_date = createDate("2019-09-24");
    val r6_e1 = Event(Some(r6_e1_uuid), Some(r6_uuid), fakeUserUuid, Some(r6_e1_date), PRO, RELANCE,
      None, Some("test"))

    // report 7 avec un user avec email -------------------------------------------------------------------------------------------
    val r7_uuid = UUID.randomUUID()
    val r7_date = createDate("2019-09-26")
    val r7 = Report(Some(r7_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r7_date), "r7", "nom 2", "email 2", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // RELANCE plus vieux de 7j
    val r7_e1_uuid = UUID.randomUUID()
    val r7_e1_date = createDate("2019-09-01");
    val r7_e1 = Event(Some(r7_e1_uuid), Some(r7_uuid), fakeUserUuid, Some(r7_e1_date), PRO, RELANCE,
      None, Some("test"))

    // report 8 avec un user avec email -------------------------------------------------------------------------------------------
    val r8_uuid = UUID.randomUUID()
    val r8_date = createDate("2019-09-26")
    val r8 = Report(Some(r8_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r8_date), "r8", "nom 2", "email 2", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // RELANCE moins vieux que 7j
    val r8_e1_uuid = UUID.randomUUID()
    val r8_e1_date = createDate("2019-09-24");
    val r8_e1 = Event(Some(r8_e1_uuid), Some(r8_uuid), fakeUserUuid, Some(r8_e1_date), PRO, RELANCE,
      None, Some("test"))

    // report 9 avec un user avec email -------------------------------------------------------------------------------------------
    val r9_uuid = UUID.randomUUID()
    val r9_date = createDate("2019-09-26")
    val r9 = Report(Some(r9_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r9_date), "r9", "nom 2", "email 2", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // Dernièree RELANCE plus vieux que 7j
    val r9_e1_uuid = UUID.randomUUID()
    val r9_e1_date = createDate("2019-09-01");
    val r9_e1 = Event(Some(r9_e1_uuid), Some(r9_uuid), fakeUserUuid, Some(r9_e1_date), PRO, RELANCE,
      None, Some("test"))

    // 1ère RELANCE 1 semaine avant
    val r9_e2_uuid = UUID.randomUUID()
    val r9_e2_date = createDate("2019-08-24");
    val r9_e2 = Event(Some(r9_e2_uuid), Some(r9_uuid), fakeUserUuid, Some(r9_e2_date), PRO, RELANCE,
      None, Some("test"))

    // report 10 avec un user avec email -------------------------------------------------------------------------------------------
    val r10_uuid = UUID.randomUUID()
    val r10_date = createDate("2019-09-26")
    val r10 = Report(Some(r10_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r10_date), "r10", "nom 2", "email 2", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // Dernière RELANCE moins vieux que 7j
    val r10_e1_uuid = UUID.randomUUID()
    val r10_e1_date = createDate("2019-09-24");
    val r10_e1 = Event(Some(r10_e1_uuid), Some(r10_uuid), fakeUserUuid, Some(r10_e1_date), PRO, RELANCE,
      None, Some("test"))

    // 1ère RELANCE 1 semaine avant
    val r10_e2_uuid = UUID.randomUUID()
    val r10_e2_date = createDate("2019-09-17");
    val r10_e2 = Event(Some(r10_e2_uuid), Some(r10_uuid), fakeUserUuid, Some(r10_e2_date), PRO, RELANCE,
      None, Some("test"))

    // 2. SIGNALEMENT TRANSMIS  ____________________________________________________________________________________________________

    // report 11 avec un user avec email -------------------------------------------------------------------------------------------
    val r11_uuid = UUID.randomUUID()
    val r11_date = createDate("2019-09-26")
    val r11 = Report(Some(r11_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r11_date), "r11", "nom 2", "email 2", true, List.empty, Some(SIGNALEMENT_TRANSMIS), None)

    // ENVOI EMAIL plus vieux que 7j
    val r11_e1_uuid = UUID.randomUUID()
    val r11_e1_date = createDate("2019-09-01")
    val r11_e1 = Event(Some(r11_e1_uuid), Some(r11_uuid), fakeUserUuid, Some(r11_e1_date), PRO, CONTACT_EMAIL,
      None, Some("test"))

    // report 12 avec un user avec email -------------------------------------------------------------------------------------------
    val r12_uuid = UUID.randomUUID()
    val r12_date = createDate("2019-09-26")
    val r12 = Report(Some(r12_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r12_date), "r12", "nom 2", "email 2", true, List.empty, Some(SIGNALEMENT_TRANSMIS), None)

    // ENVOI EMAIL plus vieux que 7j
    val r12_e1_uuid = UUID.randomUUID()
    val r12_e1_date = createDate("2019-09-24")
    val r12_e1 = Event(Some(r12_e1_uuid), Some(r12_uuid), fakeUserUuid, Some(r12_e1_date), PRO, CONTACT_EMAIL,
      None, Some("test"))

    // report 13 avec un user avec email -------------------------------------------------------------------------------------------
    val r13_uuid = UUID.randomUUID()
    val r13_date = createDate("2019-09-26")
    val r13 = Report(Some(r13_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r13_date), "r13", "nom 2", "email 2", true, List.empty, Some(SIGNALEMENT_TRANSMIS), None)

    // RELANCE plus vieux que 7j
    val r13_e1_uuid = UUID.randomUUID()
    val r13_e1_date = createDate("2019-09-01");
    val r13_e1 = Event(Some(r13_e1_uuid), Some(r13_uuid), fakeUserUuid, Some(r13_e1_date), PRO, RELANCE,
      None, Some("test"))

    // report 14 avec un user avec email -------------------------------------------------------------------------------------------
    val r14_uuid = UUID.randomUUID()
    val r14_date = createDate("2019-09-26")
    val r14 = Report(Some(r14_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r14_date), "r14", "nom 2", "email 2", true, List.empty, Some(SIGNALEMENT_TRANSMIS), None)

    // RELANCE moins vieux que 7j
    val r14_e1_uuid = UUID.randomUUID()
    val r14_e1_date = createDate("2019-09-24");
    val r14_e1 = Event(Some(r14_e1_uuid), Some(r14_uuid), fakeUserUuid, Some(r14_e1_date), PRO, RELANCE,
      None, Some("test"))

    // report 15 avec un user avec email -------------------------------------------------------------------------------------------
    val r15_uuid = UUID.randomUUID()
    val r15_date = createDate("2019-09-26")
    val r15 = Report(Some(r15_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r15_date), "r15", "nom 2", "email 2", true, List.empty, Some(SIGNALEMENT_TRANSMIS), None)

    // Dernière RELANCE plus vieux que 7j
    val r15_e1_uuid = UUID.randomUUID()
    val r15_e1_date = createDate("2019-09-01");
    val r15_e1 = Event(Some(r15_e1_uuid), Some(r15_uuid), fakeUserUuid, Some(r15_e1_date), PRO, RELANCE,
      None, Some("test"))

    // Première RELANCE
    val r15_e2_uuid = UUID.randomUUID()
    val r15_e2_date = createDate("2019-08-24");
    val r15_e2 = Event(Some(r15_e2_uuid), Some(r15_uuid), fakeUserUuid, Some(r15_e2_date), PRO, RELANCE,
      None, Some("test"))

    // report 16 avec un user avec email -------------------------------------------------------------------------------------------
    val r16_uuid = UUID.randomUUID()
    val r16_date = createDate("2019-09-26")
    val r16 = Report(Some(r16_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("22222222222222"), Some(r16_date), "r16", "nom 2", "email 2", true, List.empty, Some(SIGNALEMENT_TRANSMIS), None)

    // Dernière RELANCE moins vieux que 7j
    val r16_e1_uuid = UUID.randomUUID()
    val r16_e1_date = createDate("2019-09-24");
    val r16_e1 = Event(Some(r16_e1_uuid), Some(r16_uuid), fakeUserUuid, Some(r16_e1_date), PRO, RELANCE,
      None, Some("test"))

    // Première RELANCE
    val r16_e2_uuid = UUID.randomUUID()
    val r16_e2_date = createDate("2019-08-17");
    val r16_e2 = Event(Some(r16_e2_uuid), Some(r16_uuid), fakeUserUuid, Some(r16_e2_date), PRO, RELANCE,
      None, Some("test"))

    for {
      _ <- userRepository.create(User(fakeUserUuid, "test", "", None, None, None, None, Admin))
      _ <- userRepository.create(userWithoutEmail)
      _ <- userRepository.create(userEmail)
      _ <- reportRepository.create(r1)
      _ <- eventRepository.createEvent(r1_e1)
      _ <- reportRepository.create(r2)
      _ <- eventRepository.createEvent(r2_e1)
      _ <- reportRepository.create(r3)
      _ <- eventRepository.createEvent(r3_e1)
      _ <- reportRepository.create(r4)
      _ <- eventRepository.createEvent(r4_e1)
      _ <- reportRepository.create(r5)
      _ <- eventRepository.createEvent(r5_e1)
      _ <- reportRepository.create(r6)
      _ <- eventRepository.createEvent(r6_e1)
      _ <- reportRepository.create(r7)
      _ <- eventRepository.createEvent(r7_e1)
      _ <- reportRepository.create(r8)
      _ <- eventRepository.createEvent(r8_e1)
      _ <- reportRepository.create(r9)
      _ <- eventRepository.createEvent(r9_e1)
      _ <- eventRepository.createEvent(r9_e2)
      _ <- reportRepository.create(r10)
      _ <- eventRepository.createEvent(r10_e1)
      _ <- eventRepository.createEvent(r10_e2)
      _ <- reportRepository.create(r11)
      _ <- eventRepository.createEvent(r11_e1)
      _ <- reportRepository.create(r12)
      _ <- eventRepository.createEvent(r12_e1)
      _ <- reportRepository.create(r13)
      _ <- eventRepository.createEvent(r13_e1)
      _ <- reportRepository.create(r14)
      _ <- eventRepository.createEvent(r14_e1)
      _ <- reportRepository.create(r15)
      _ <- eventRepository.createEvent(r15_e1)
      _ <- eventRepository.createEvent(r15_e2)
      _ <- reportRepository.create(r16)
      _ <- eventRepository.createEvent(r16_e1)
      _ <- eventRepository.createEvent(r16_e2)

    } yield {
      Ok("Insertion jeu de test")
    }

    /*
      POUR VOIR LES LIGNES AJOUTÉES
      select * from events where detail = 'test';
      select * from signalement where categorie='test';
      select * from users where login = 'test';
      select * from users where lastname = 'test';
     */

    /*
      Log attendus
      [debug] t.ReminderTask - Résumé de la tâche : (r7,Traitement en cours,List(Relance),List(professional.reportNotification))
      [debug] t.ReminderTask - Résumé de la tâche : (r3,Traitement en cours,List(Relance),List(professional.reportNotification))
      [debug] t.ReminderTask - Résumé de la tâche : (r1,À traiter,List(Relance),List())
      [debug] t.ReminderTask - Résumé de la tâche : (r5,Signalement non consulté,List(Signalement non consulté),List(consumer.reportClosedByNoReading))
      [debug] t.ReminderTask - Résumé de la tâche : (r9,Signalement non consulté,List(Signalement non consulté),List(consumer.reportClosedByNoReading))
      [debug] t.ReminderTask - Résumé de la tâche : (r13,Signalement transmis,List(Relance),List(professional.reportNotification))
      [debug] t.ReminderTask - Résumé de la tâche : (r11,Signalement transmis,List(Relance),List(professional.reportNotification))
      [debug] t.ReminderTask - Résumé de la tâche : (r15,Signalement consulté ignoré,List(Signalement consulté ignoré),List(consumer.reportClosedByNoAction))    */
  }

  /**
    * Lancement de la tâche de relance manuellement
    */
  def runManuallyReminderTask() = SecuredAction(WithPermission(UserPermission.createEvent)).async { implicit request =>
    val now = LocalDate.parse("2019-09-26", DateTimeFormatter.ofPattern("yyyy-MM-d")).atStartOfDay()
    reminderTask.runTask(now, test = true)
    Future(Ok("runManuallyReminderTask"))
  }

    /*
    Suppression en SQL pur

    delete from events where report_id in (
        select id
        from signalement
        where categorie='test'
    );
    delete from signalement where categorie='test';
    delete from users where login = 'test';
    delete from users where lastname = 'test';
     */

}
