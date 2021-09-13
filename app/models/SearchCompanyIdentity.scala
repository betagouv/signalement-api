package models

import java.util.UUID
import scala.util.Try

trait SearchCompanyIdentity {
  val value: String
}

case class SearchCompanyIdentityRCS(value: String) extends SearchCompanyIdentity
case class SearchCompanyIdentitySiret(value: String) extends SearchCompanyIdentity
case class SearchCompanyIdentitySiren(value: String) extends SearchCompanyIdentity
case class SearchCompanyIdentityName(value: String) extends SearchCompanyIdentity
case class SearchCompanyIdentityId(value: String) extends SearchCompanyIdentity

object SearchCompanyIdentity {
  def fromString(identity: String): SearchCompanyIdentity = {
    val trimmedIdentity = identity.replaceAll("\\s", "")
    trimmedIdentity match {
      case q if q.matches("[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}") => SearchCompanyIdentityRCS(q.toLowerCase())
      case q if q.matches("[0-9]{14}")                     => SearchCompanyIdentitySiret(q)
      case q if q.matches("[0-9]{9}")                      => SearchCompanyIdentitySiren(q)
      case q if Try(UUID.fromString(q)).isSuccess          => SearchCompanyIdentityId(q)
      case q                                               => SearchCompanyIdentityName(identity)
    }
  }
}
