package utils

object MapUtils {

  def fillMissingKeys[A, B](map: Map[A, B], allExpectedKeys: List[A], defaultValue: B) =
    (map.keys ++ allExpectedKeys).toList.distinct.map { key =>
      key -> map.getOrElse(key, defaultValue)
    }.toMap

}
