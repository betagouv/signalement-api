package controllers

import java.util.UUID

import actors.ReportedPhonesExtractActor
import actors.ReportedPhonesExtractActor.RawFilters
import akka.actor.ActorRef
import akka.pattern.ask
import cats.data.OptionT
import cats.implicits._
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.ReportedPhoneCompanyFormat._
import models._
import play.api.Logger
import play.api.libs.json.{JsError, Json}
import repositories.{CompanyRepository, ReportRepository, ReportedPhoneRepository}
import utils.DateUtils
import utils.silhouette.auth.{AuthEnv, WithRole}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportedPhoneController @Inject()(
  val reportedPhoneRepository: ReportedPhoneRepository,
  val reportRepository: ReportRepository,
  val companyRepository: CompanyRepository,
  @Named("reported-phones-extract-actor") reportedPhonesExtractActor: ActorRef,
  val silhouette: Silhouette[AuthEnv]
)(implicit ec: ExecutionContext) extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def fetchWithCompanies() = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      reportedPhones <- reportedPhoneRepository.list
    } yield {
      Ok(Json.toJson(reportedPhones))
    }
  }

  def fetchUnregisteredPhones(q: Option[String], start: Option[String], end: Option[String]) = SecuredAction(WithRole(UserRoles.Admin, UserRoles.DGCCRF)).async { implicit request =>
    reportRepository.getPhoneReportsWithoutCompany(DateUtils.parseDate(start), DateUtils.parseDate(end))
      .map(reports => Ok(Json.toJson(
        reports
          .groupBy(_.phone)
          .collect { case (Some(phone), reports) if q.map(phone.contains(_)).getOrElse(true) => (phone, reports.length) }
          .map{ case(phone, count) => Json.obj("phone" -> phone, "count" -> count)}
      )))
  }

  def extractUnregisteredPhones(q: Option[String], start: Option[String], end: Option[String]) = SecuredAction(WithRole(UserRoles.Admin, UserRoles.DGCCRF)).async { implicit request =>
    logger.debug(s"Requesting reportedPhones for user ${request.identity.email}")
    reportedPhonesExtractActor ? ReportedPhonesExtractActor.ExtractRequest(request.identity, RawFilters(q, start, end))
    Future(Ok)
  }

  def update(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body.validate[ReportedPhoneUpdate].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      reportedPhoneUpdate => {
        (for {
          reportedPhone <- OptionT(reportedPhoneRepository.find(uuid))
          _ <- OptionT.liftF(
            if (reportedPhoneUpdate.status.contains(ReportedPhoneStatus.VALIDATED)) unvalidateOtherReportedPhones(reportedPhone)
            else Future.successful(Unit)
          )
          updatedReportedPhone <- OptionT.liftF(reportedPhoneRepository.update(reportedPhoneUpdate.mergeIn(reportedPhone)))
          company <- OptionT(companyRepository.fetchCompany(reportedPhone.companyId))
        } yield (updatedReportedPhone, company)).value.map {
          case None => NotFound
          case Some(result) => Ok(Json.toJson(result))
        }
      }
    )
  }

  private[this] def unvalidateOtherReportedPhones(updatedReportedPhone: ReportedPhone) = {
    for {
      reportedPhonesWithSamePhone <- reportedPhoneRepository.searchCompaniesByPhone(updatedReportedPhone.phone).map(reportedPhones => reportedPhones
        .map(_._1)
        .filter(_.id != updatedReportedPhone.id)
        .filter(_.status == ReportedPhoneStatus.VALIDATED)
      )
      unvalidatedReportedPhones <- Future.sequence(reportedPhonesWithSamePhone.map(reportedPhone => reportedPhoneRepository.update(reportedPhone.copy(status = ReportedPhoneStatus.PENDING))))
    } yield {
      unvalidatedReportedPhones
    }
  }

  def updateCompany(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body.validate[ReportedPhoneUpdateCompany].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      reportedPhoneUpdate => {
        val newCompanyFuture = companyRepository.getOrCreate(reportedPhoneUpdate.companySiret, Company(
          siret = reportedPhoneUpdate.companySiret,
          name = reportedPhoneUpdate.companyName,
          address = reportedPhoneUpdate.companyAddress,
          postalCode = reportedPhoneUpdate.companyPostalCode,
          activityCode = reportedPhoneUpdate.companyActivityCode,
        ))
        (for {
          reportedPhone <- OptionT(reportedPhoneRepository.find(uuid))
          otherAssociatedCompaniesIds <- OptionT.liftF(reportedPhoneRepository.searchCompaniesByPhone(reportedPhone.phone).map(_.map(_._2.siret)))
          newCompany <- OptionT.liftF(newCompanyFuture)
          result <- OptionT.liftF(if (otherAssociatedCompaniesIds.contains(reportedPhoneUpdate.companySiret)) {
            Future.successful(Conflict)
          } else {
            reportedPhoneRepository
              .update(reportedPhone.copy(companyId = newCompany.id, status = ReportedPhoneStatus.VALIDATED))
              .map(updated => Ok(Json.toJson((updated, newCompany))))
          })
        } yield result).value.map {
          case None => NotFound
          case Some(result) => result
        }
      })
  }

  def create() = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    request.body.validate[ReportedPhoneCreate].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      reportedPhoneCreate =>
        for {
          company <- companyRepository.getOrCreate(reportedPhoneCreate.companySiret, Company(
            siret = reportedPhoneCreate.companySiret,
            name = reportedPhoneCreate.companyName,
            address = reportedPhoneCreate.companyAddress,
            postalCode = reportedPhoneCreate.companyPostalCode,
            activityCode = reportedPhoneCreate.companyActivityCode,
          ))
          reportedPhone <- reportedPhoneRepository.create(ReportedPhone(
            phone = reportedPhoneCreate.phone,
            status = ReportedPhoneStatus.VALIDATED,
            companyId = company.id,
          ))
        } yield {
          Ok(Json.toJson(reportedPhone, company))
        }
    )
  }

  def remove(uuid: UUID) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      _ <- reportedPhoneRepository.delete(uuid)
    } yield Ok
  }
}
