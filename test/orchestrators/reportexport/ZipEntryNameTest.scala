package orchestrators.reportexport

import org.specs2.mutable.Specification

class ZipEntryNameTest extends Specification {

  "ZipEntryName.safeString" should {

    "remove unsafe filename characters" in {
      val unsafe = "\\/:*?\"<>|"
      val result = ZipEntryName.safeString(unsafe)

      val withoutSalt = result.dropRight(4)
      withoutSalt mustEqual ""
    }

    "remove line breaks from the input" in {
      val input  = "GAUTIER\nGARANX\r\nName"
      val result = ZipEntryName.safeString(input)

      val withoutSalt = result.dropRight(4)
      withoutSalt mustEqual "GAUTIER GARANX Name"
    }

    "remove tabs from the input" in {
      val input  = "GAU\tTIER\tGARANX"
      val result = ZipEntryName.safeString(input)

      val withoutSalt = result.dropRight(4)
      withoutSalt mustEqual "GAU TIER GARANX"
    }

    "normalize multiple spaces into a single space" in {
      val input  = "GAUTIER    GARANX     Name"
      val result = ZipEntryName.safeString(input)

      val withoutSalt = result.dropRight(4)
      withoutSalt mustEqual "GAUTIER GARANX Name"
    }

    "trim leading and trailing spaces" in {
      val input  = "     GAUTIER GARANX    "
      val result = ZipEntryName.safeString(input)

      val withoutSalt = result.dropRight(4)
      withoutSalt mustEqual "GAUTIER GARANX"
    }

    "preserve safe characters" in {
      val input  = "GAUTIER_GARANX-2025"
      val result = ZipEntryName.safeString(input)

      val withoutSalt = result.dropRight(4)
      withoutSalt mustEqual "GAUTIER_GARANX-2025"
    }

  }
}
