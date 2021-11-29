package models

import org.specs2.mutable.Specification
//import play.api.libs.json.Json

import scala.util.Try

class UserRoleTest extends Specification {

  "UserRole" should {

    "get value from string name" in {

      UserRole.withName("DGCCRF") shouldEqual UserRole.DGCCRF
      UserRole.withName("Admin") shouldEqual UserRole.Admin
      UserRole.withName("Professionnel") shouldEqual UserRole.Professionnel
      Try(UserRole.withName("XXXXXXXXXX")).isFailure shouldEqual true
    }

//    "parse json as expected" in {
//
//      val DGCCRFJson = Json.obj("role" -> "DGCCRF")
//      val AdminJson = Json.obj("role" -> "Admin")
//      val ProfessionnelJson = Json.obj("role" -> "Professionnel")
//      val unknownJson = Json.obj("role" -> "XXXXXX")
//
//      DGCCRFJson.as[UserRole] shouldEqual UserRole.DGCCRF
//      AdminJson.as[UserRole] shouldEqual UserRole.Admin
//      ProfessionnelJson.as[UserRole] shouldEqual UserRole.Professionnel
//      Try(unknownJson.as[UserRole]).isFailure shouldEqual true
//    }
//
//    "write json as expected" in {
//
//      Json.toJson[UserRole](UserRole.DGCCRF) shouldEqual Json.obj(
//        "name" -> "DGCCRF",
//        "permissions" -> Json.toJson(
//          Seq(
//            UserPermission.listReports,
//            UserPermission.createReportAction,
//            UserPermission.subscribeReports
//          )
//        )
//      )
//
//      Json.toJson[UserRole](UserRole.Admin) shouldEqual Json.obj(
//        "name" -> "Admin",
//        "permissions" -> Json.toJson(
//          UserPermission.values.toSeq
//        )
//      )
//
//      Json.toJson[UserRole](UserRole.Professionnel) shouldEqual Json.obj(
//        "name" -> "Professionnel",
//        "permissions" -> Json.toJson(
//          Seq(
//            UserPermission.listReports,
//            UserPermission.createReportAction
//          )
//        )
//      )
//
//    }

  }

}
