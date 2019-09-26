package controllers

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.UserRoles.{Admin, Pro}
import models._
import play.api.{Configuration, Environment, Logger}
import repositories._
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.EventType.PRO
import utils.Constants.StatusPro._
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithPermission}

import scala.concurrent.{ExecutionContext, Future}

class InjectTestController @Inject()(reportRepository: ReportRepository,
                                     eventRepository: EventRepository,
                                     userRepository: UserRepository,
                                     mailerService: MailerService,
                                     s3Service: S3Service,
                                     val silhouette: Silhouette[AuthEnv],
                                     val silhouetteAPIKey: Silhouette[APIKeyEnv],
                                     configuration: Configuration,
                                     environment: Environment)
                                    (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  val BucketName = configuration.get[String]("play.buckets.report")

  val fakeUserUuid = UUID.randomUUID()

  val userEmailUuid = UUID.randomUUID()
  val userWithoutEmailUuid = UUID.randomUUID()

  val r1_uuid = UUID.randomUUID()
  val r2_uuid = UUID.randomUUID()
  val r3_uuid = UUID.randomUUID()
  val r4_uid = UUID.randomUUID()

  val r1_e1_uuid: UUID = UUID.randomUUID()
  val r2_e1_uuid: UUID = UUID.randomUUID()


  // date de la forme 2011-12-03
  private def createDate(str: String) = {
    OffsetDateTime.parse(s"${str}T10:15:30+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  def prepareBeforeTest() = SecuredAction(WithPermission(UserPermission.createEvent)).async { implicit request =>

    val userWithoutEmail = User(userWithoutEmailUuid, "11111111111111", "", Some("123123"), None, None, Some("test"), Pro)
    val userEmail = User(userEmailUuid, "22222222222222", "", None, Some("user-test@gmail.com"), None, Some("test"), Pro)

    // report 1 avec un user sans email -------------------------------------------------------------------------------------------
    val r1_date = createDate("2019-09-26")
    val r1 = Report(Some(r1_uuid), "test", List.empty, List("détails test"), "company1", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
      None, Some("11111111111111"), Some(r1_date), "prenom 1", "nom 1", "email 1", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // CONTACT_COURRIER plus vieux de 21j
    val r1_e1_uuid = UUID.randomUUID()
    val r1_e1_date = createDate("2019-09-01");
    val r1_e1 = Event(Some(r1_e1_uuid), Some(r1_uuid), fakeUserUuid, Some(r1_e1_date), PRO, CONTACT_COURRIER,
      None, Some("test"))

    // report 2 avec un user avec email -------------------------------------------------------------------------------------------
    val r2_date = createDate("2019-09-26")
    val r2 = Report(Some(r2_uuid), "test", List.empty, List("détails test"), "company2", "addresse + id " + UUID.randomUUID().toString,
      None, Some("22222222222222"), Some(r2_date), "prenom 2", "nom 2", "email 2", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

    // CONTACT_COURRIER moins vieux que 21j
    val r2_e1_date = createDate("2019-09-24");
    val r2_e1 = Event(Some(r2_e1_uuid), Some(r2_uuid), fakeUserUuid, Some(r2_e1_date), PRO, CONTACT_COURRIER,
      None, Some("test"))

    for {
      _ <- userRepository.create(User(fakeUserUuid, "test", "", None, None, None, None, Admin))
      _ <- userRepository.create(userWithoutEmail)
      _ <- userRepository.create(userEmail)
      _ <- reportRepository.create(r1)
      _ <- eventRepository.createEvent(r1_e1)
      _ <- reportRepository.create(r2)
      _ <- eventRepository.createEvent(r2_e1)

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

  }

  //def cleanAfterTest() = SecuredAction(WithPermission(UserPermission.createEvent)).async { implicit request =>

    /*
    Supprimer en SQL pur (plus facile)

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
