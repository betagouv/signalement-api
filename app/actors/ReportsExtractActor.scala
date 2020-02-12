package actors

import akka.actor._
import play.api.Logger

import models.User
import repositories.ReportFilter

object ReportsExtractActor {
  def props = Props[ReportsExtractActor]

  case class ExtractRequest(requestedBy: User, filters: ReportFilter)
}

class ReportsExtractActor extends Actor {
  import ReportsExtractActor._
  val logger: Logger = Logger(this.getClass)
  override def preStart() = {
    logger.debug("Starting")
  }
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    logger.error(s"Restarting due to [${reason.getMessage}] when processing [${message.getOrElse("")}]")
  }
  override def receive = {
    case ExtractRequest(requestedBy: User, filters: ReportFilter) =>
      logger.debug(s"Hello, ${requestedBy.firstName}")
    case _ => logger.error("Could not handle request")
  }
}

