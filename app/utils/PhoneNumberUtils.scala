package utils

object PhoneNumberUtils {

  def sanitizeIncomingPhoneNumber(phone: String): String =
    phone
      // remove all spaces
      .replaceAll("\\s+", "")
      // remove all dots
      .replaceAll("\\.+", "")
      // Replace french indicator format +33X or 0033X by just the classic 0X
      .replaceFirst("^\\+33", "0")
      .replaceFirst("^0033", "0")

}
