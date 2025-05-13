package tasks.company

import config.SignalConsoConfiguration
import config.TaskConfiguration
import models.company.AccessLevel.ADMIN
import models.company.AccessLevel.MEMBER
import models.company.AccessLevel.NONE
import models.company.Company
import org.apache.pekko.actor.ActorSystem
import repositories.company.CompanyRepositoryInterface
import repositories.company.accessinheritancemigration.CompanyAccessInheritanceMigration
import repositories.company.accessinheritancemigration.CompanyAccessInheritanceMigrationRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.FrequentTaskSettings

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class CompanyAccessInheritanceMigrationTask(
    actorSystem: ActorSystem,
    companyRepository: CompanyRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    companyAccessInheritanceMigrationRepository: CompanyAccessInheritanceMigrationRepositoryInterface,
    taskConfiguration: TaskConfiguration,
    signalConsoConfiguration: SignalConsoConfiguration,
    taskRepository: TaskRepositoryInterface
)(implicit
    executionContext: ExecutionContext
) extends ScheduledTask(
      16,
      "company_access_inheritance_migration_task",
      taskRepository,
      actorSystem,
      taskConfiguration
    ) {

  override val taskSettings = FrequentTaskSettings(interval = 20.minutes)

  val isDryRun = signalConsoConfiguration.accessesMigrationTaskIsDryRun

  override def runTask(): Future[Unit] =
    for {
      companies <- companyRepository.fetchCompaniesNotYetProcessedForAccessInheritanceMigration(limit = 1000)
      _ = logger.info(s"company_access_inheritance_migration_task working on ${companies.length} companies")
      _ <- Future.sequence(companies.map {
        case company if company.isHeadOffice =>
          logger.info(s"company_access_inheritance_migration_task : ${company.siret} is a head office, nothing to do")
          markAsProcessed(company)
        case subsidiary =>
          for {
            maybeHeadOffice <- findHeadOffice(subsidiary)
            _ <- maybeHeadOffice match {
              case None =>
                logger.info(
                  s"company_access_inheritance_migration_task : ${subsidiary.siret} has not head office! cannot do anything"
                )
                markAsProcessed(subsidiary)
              case Some(headOffice) =>
                logger.info(
                  s"company_access_inheritance_migration_task : ${subsidiary.siret} will inherit access from ${headOffice.siret}"
                )
                for {
                  headOfficeAccesses <- companyAccessRepository.fetchUsersWithLevelExcludingNone(List(headOffice.id))
                  subsidiaryAccesses <- companyAccessRepository.fetchUsersWithLevel(List(subsidiary.id))
                  accessesToSetOnSubsidiary = headOfficeAccesses.flatMap { case (user, levelOnHeadOffice) =>
                    val levelOnSubsidiary = subsidiaryAccesses.find(_._1.id == user.id).map(_._2)
                    val maybeLevelToSet = levelOnHeadOffice match {
                      case ADMIN =>
                        levelOnSubsidiary match {
                          case Some(NONE) | None => Some(ADMIN)
                          case Some(_) =>
                            None // can stay admin or downgrade to member. We let the member override ! this is consistent with the former inheritance algorithm
                        }
                      case MEMBER =>
                        levelOnSubsidiary match {
                          case Some(NONE) | None => Some(MEMBER)
                          case Some(_)           => None
                        }
                      case NONE | _ => None
                    }
                    maybeLevelToSet.map(user -> _)
                  }
                  _ = logger.info(
                    s"company_access_inheritance_migration_task : ${subsidiary.siret} has ${subsidiaryAccesses.length} direct accesses, ${headOfficeAccesses.length} accesses on the head office, so now we have to add ${accessesToSetOnSubsidiary.length} direct accesses"
                  )
                  _ <- Future.sequence(accessesToSetOnSubsidiary.map { case (user, level) =>
                    logger.info(
                      s"company_access_inheritance_migration_task : we should give ${user.email} ${level.value} access on ${subsidiary.siret} (from head office ${headOffice.siret})"
                    )
                    if (isDryRun) Future.unit
                    else companyAccessRepository.createAccess(subsidiary.id, user.id, level)
                  })
                  _ <- markAsProcessed(subsidiary)
                } yield ()
            }
          } yield ()
      })
    } yield ()

  private def findHeadOffice(company: Company): Future[Option[Company]] =
    for {
      headOffices <- companyRepository.findHeadOffices(List(company.siren), openOnly = false)
      maybeHeadOffice = headOffices match {
        case Nil       => None
        case List(c)   => Some(c)
        case companies =>
          // multiple_head_offices error. we are considering that last created company is the head office.
          Some(companies.maxBy(_.creationDate.toEpochSecond))
      }
    } yield maybeHeadOffice
  private def markAsProcessed(company: Company): Future[Unit] =
    for {
      _ <- companyAccessInheritanceMigrationRepository.createOrUpdate(
        CompanyAccessInheritanceMigration(company.id, OffsetDateTime.now())
      )
    } yield ()

}
