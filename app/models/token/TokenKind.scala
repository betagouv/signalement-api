package models.token

import enumeratum.EnumEntry.UpperSnakecase
import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed trait TokenKind extends EnumEntry with UpperSnakecase

sealed trait AdminOrDgccrfTokenKind extends TokenKind

object TokenKind extends PlayEnum[TokenKind] {

  val values = findValues

  case object CompanyInit          extends TokenKind
  case object CompanyFollowUp      extends TokenKind
  case object CompanyJoin          extends TokenKind
  case object ValidateEmail        extends TokenKind
  case object UpdateEmail          extends TokenKind
  case object DGCCRFAccount        extends AdminOrDgccrfTokenKind
  case object DGALAccount          extends AdminOrDgccrfTokenKind
  case object SuperAdminAccount    extends AdminOrDgccrfTokenKind
  case object AdminAccount         extends AdminOrDgccrfTokenKind
  case object ReadOnlyAdminAccount extends AdminOrDgccrfTokenKind
}
