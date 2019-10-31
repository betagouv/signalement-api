package utils

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import java.util.UUID
import models._
import repositories._


trait FixtureGenerator[T] {
  def build(): T
  def save(o: T): Future[T]
  def buildAndSave: Future[T] = save(build)
}

/*
object Generator {
  val userGenerator = new FixtureGenerator[User] {
    def build = {
      User(UUID.randomUUID(), "admin@signalconso.beta.gouv.fr", "password", None, Some("Prénom"), Some("Nom"), Some("admin@signalconso.beta.gouv.fr"), UserRoles.Admin)
    }
  }
}*/
