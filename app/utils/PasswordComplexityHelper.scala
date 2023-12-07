package utils

import controllers.error.AppError.PasswordNotComplexEnoughError

import java.text.Normalizer
import scala.concurrent.Future

object PasswordComplexityHelper {

  def validatePasswordComplexity(password: String): Future[Unit] =
    if (!isPasswordComplexEnough(password)) {
      Future.failed(PasswordNotComplexEnoughError)
    } else {
      Future.unit
    }

  // /!\ this logic is duplicated in the frontend
  def isPasswordComplexEnough(pwd: String): Boolean = {
    val chars = normalizeAccents(pwd).toList
    chars.exists(_.isLower) &&
    chars.exists(_.isUpper) &&
    chars.exists(_.isDigit) &&
    containsSpecialChars(chars) &&
    chars.length >= 12
  }

  private def normalizeAccents(s: String): String =
    // https://stackoverflow.com/questions/15190656/easy-way-to-remove-accents-from-a-unicode-string
    Normalizer
      .normalize(s, Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}", "")

  private def containsSpecialChars(s: List[Char]): Boolean = {
    val list = """'-!"#$%&()*,./:;?@[]^_`{|}~+<=>"""
    list.toList.exists(c => s.contains(c))
  }

}
