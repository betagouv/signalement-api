package tasks.company

import org.apache.pekko.stream.Materializer
import models.company.CompanySync
import org.scalacheck.Gen
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable
import utils.AppSpec
import utils.Fixtures
import utils.TaskRepositoryMock
import utils.TestApp

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.Future

class CompanyUpdateTaskSpec(implicit ee: ExecutionEnv)
    extends mutable.Specification
    with AppSpec
    with Mockito
    with FutureMatchers {

  val (app, components)          = TestApp.buildApp()
  implicit val mat: Materializer = app.materializer

  val taskRepositoryMock = new TaskRepositoryMock()

  "CompanyUpdateTask" should {
    sequential
    "correctly update a company" in {
      val serviceMock = mock[CompanySyncServiceInterface]
      val task = new CompanyUpdateTask(
        app.actorSystem,
        components.companyRepository,
        serviceMock,
        components.companySyncRepository,
        components.applicationConfiguration.task,
        taskRepositoryMock
      )
      val company = Fixtures.genCompany.sample.get
      val newName = Gen.alphaNumStr.sample.get
      // Truncated to MILLIS because PG does not handle nanos
      val now                 = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
      val companySync         = CompanySync(UUID.randomUUID(), now.minus(1, ChronoUnit.DAYS))
      val expectedCompany     = company.copy(name = newName)
      val expectedCompanySync = companySync.copy(lastUpdated = now)
      val companySearchResult = CompanySearchResult(
        siret = company.siret,
        name = Some(newName),
        brand = company.brand,
        commercialName = company.commercialName,
        establishmentCommercialName = company.establishmentCommercialName,
        isHeadOffice = company.isHeadOffice,
        address = company.address,
        activityCode = company.activityCode,
        activityLabel = None,
        isOpen = company.isOpen,
        isPublic = company.isPublic,
        lastUpdated = Some(now)
      )

      serviceMock.syncCompanies(
        Seq(company),
      ) returns Future
        .successful(
          List(companySearchResult)
        )

      for {
        _                  <- components.companyRepository.create(company)
        _                  <- components.companySyncRepository.create(companySync)
        _                  <- task.runTask()
        updatedCompany     <- components.companyRepository.get(company.id)
        updatedCompanySync <- components.companySyncRepository.get(companySync.id)
        _                  <- components.companyRepository.delete(company.id)
        _                  <- components.companySyncRepository.delete(companySync.id)
      } yield (updatedCompany shouldEqual Some(expectedCompany)) and (updatedCompanySync shouldEqual Some(
        expectedCompanySync
      ))
    }

    "correctly update a company the first time (no sync time already saved)" in {
      val serviceMock = mock[CompanySyncServiceInterface]
      val task = new CompanyUpdateTask(
        app.actorSystem,
        components.companyRepository,
        serviceMock,
        components.companySyncRepository,
        components.applicationConfiguration.task,
        taskRepositoryMock
      )
      val company = Fixtures.genCompany.sample.get
      val newName = Gen.alphaNumStr.sample.get
      // Truncated to MILLIS because PG does not handle nanos
      val now             = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
      val expectedCompany = company.copy(name = newName)
      val companySearchResult = CompanySearchResult(
        siret = company.siret,
        name = Some(newName),
        brand = company.brand,
        commercialName = company.commercialName,
        establishmentCommercialName = company.establishmentCommercialName,
        isHeadOffice = company.isHeadOffice,
        address = company.address,
        activityCode = company.activityCode,
        activityLabel = None,
        isOpen = company.isOpen,
        isPublic = company.isPublic,
        lastUpdated = Some(now)
      )

      serviceMock.syncCompanies(
        Seq(company)
      ) returns Future
        .successful(
          List(companySearchResult)
        )

      for {
        _                  <- components.companyRepository.create(company)
        _                  <- task.runTask()
        updatedCompany     <- components.companyRepository.get(company.id)
        createdCompanySync <- components.companySyncRepository.list().map(_.head)
        _                  <- components.companyRepository.delete(company.id)
        _                  <- components.companySyncRepository.delete(createdCompanySync.id)
      } yield (updatedCompany shouldEqual Some(expectedCompany)) and (createdCompanySync.lastUpdated shouldEqual now)
    }

    "handle empty names" in {
      val serviceMock = mock[CompanySyncServiceInterface]
      val task = new CompanyUpdateTask(
        app.actorSystem,
        components.companyRepository,
        serviceMock,
        components.companySyncRepository,
        components.applicationConfiguration.task,
        taskRepositoryMock
      )
      val company = Fixtures.genCompany.sample.get
      // Truncated to MILLIS because PG does not handle nanos
      val now                 = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
      val companySync         = CompanySync(UUID.randomUUID(), now.minus(1, ChronoUnit.DAYS))
      val expectedCompany     = company.copy(name = "")
      val expectedCompanySync = companySync.copy(lastUpdated = now)
      val companySearchResult = CompanySearchResult(
        siret = company.siret,
        name = None,
        brand = company.brand,
        commercialName = company.commercialName,
        establishmentCommercialName = company.establishmentCommercialName,
        isHeadOffice = company.isHeadOffice,
        address = company.address,
        activityCode = company.activityCode,
        activityLabel = None,
        isOpen = company.isOpen,
        isPublic = company.isPublic,
        lastUpdated = Some(now)
      )

      serviceMock.syncCompanies(
        Seq(company)
      ) returns Future
        .successful(
          List(companySearchResult)
        )

      for {
        _                  <- components.companyRepository.create(company)
        _                  <- components.companySyncRepository.create(companySync)
        _                  <- task.runTask()
        updatedCompany     <- components.companyRepository.get(company.id)
        updatedCompanySync <- components.companySyncRepository.get(companySync.id)
        _                  <- components.companyRepository.delete(company.id)
        _                  <- components.companySyncRepository.delete(companySync.id)
      } yield (updatedCompany shouldEqual Some(expectedCompany)) and (updatedCompanySync shouldEqual Some(
        expectedCompanySync
      ))
    }

    "do nothing if the service returns an empty list" in {
      val serviceMock = mock[CompanySyncServiceInterface]
      val task = new CompanyUpdateTask(
        app.actorSystem,
        components.companyRepository,
        serviceMock,
        components.companySyncRepository,
        components.applicationConfiguration.task,
        taskRepositoryMock
      )
      val company = Fixtures.genCompany.sample.get
      // Truncated to MILLIS because PG does not handle nanos
      val now         = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
      val companySync = CompanySync(UUID.randomUUID(), now.plus(1, ChronoUnit.DAYS))

      serviceMock.syncCompanies(
        org.mockito.ArgumentMatchers.eq(Seq(company))
      ) returns Future
        .successful(
          List.empty
        )

      for {
        _                  <- components.companyRepository.create(company)
        _                  <- components.companySyncRepository.create(companySync)
        _                  <- task.runTask()
        updatedCompany     <- components.companyRepository.get(company.id)
        updatedCompanySync <- components.companySyncRepository.get(companySync.id)
        _                  <- components.companyRepository.delete(company.id)
        _                  <- components.companySyncRepository.delete(companySync.id)
      } yield (updatedCompany shouldEqual Some(company)) and (updatedCompanySync shouldEqual Some(companySync))
    }
  }

}
