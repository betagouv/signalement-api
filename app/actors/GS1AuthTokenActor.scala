package actors

import actors.GS1AuthTokenActor.FetchToken
import actors.GS1AuthTokenActor.FetchTokenFailed
import actors.GS1AuthTokenActor.FetchTokenSuccess
import actors.GS1AuthTokenActor.GetToken
import actors.GS1AuthTokenActor.GotToken
import actors.GS1AuthTokenActor.RenewToken
import actors.GS1AuthTokenActor.TokenError
import akka.actor.Scheduler
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl._
import akka.pattern.retry
import services.GS1ServiceInterface

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import akka.actor.typed.scaladsl.adapter._
import models.barcode.gs1.OAuthAccessToken
import play.api.Logger

object GS1AuthTokenActor {
  sealed trait Command
  case class GetToken(replyTo: ActorRef[Reply])                                           extends Command
  case class RenewToken(replyTo: ActorRef[Reply])                                         extends Command
  private case class FetchToken(replyTo: ActorRef[Reply])                                 extends Command
  private case class FetchTokenSuccess(replyTo: ActorRef[Reply], token: OAuthAccessToken) extends Command
  private case class FetchTokenFailed(error: Throwable, replyTo: ActorRef[Reply])         extends Command

  sealed trait Reply
  case class GotToken(token: OAuthAccessToken) extends Reply
  case class TokenError(error: Throwable)      extends Reply

  def apply(gS1Service: GS1ServiceInterface): Behavior[Command] =
    Behaviors.withStash(100) { buffer =>
      Behaviors.setup[Command] { context =>
        new GS1AuthTokenActor(context, buffer, gS1Service).noToken()
      }
    }
}

class GS1AuthTokenActor(
    context: ActorContext[GS1AuthTokenActor.Command],
    buffer: StashBuffer[GS1AuthTokenActor.Command],
    gS1Service: GS1ServiceInterface
) {

  val logger: Logger = Logger(this.getClass)

  import context.executionContext
  implicit private val scheduler: Scheduler = context.system.scheduler.toClassic

  private def noToken(): Behaviors.Receive[GS1AuthTokenActor.Command] =
    Behaviors.receiveMessagePartial { case GetToken(replyTo) =>
      logger.debug("Requesting a token while no token in cache, switch to fetching state")
      context.self ! FetchToken(replyTo)
      fetchToken()

    }

  private def fetchToken(): Behaviors.Receive[GS1AuthTokenActor.Command] = Behaviors.receiveMessage {
    case FetchToken(replyTo) =>
      logger.debug("Fetching a fresh new token")
      val authenticateWithRetry = retry(() => gS1Service.authenticate(), 2, 500.milliseconds)
      context.pipeToSelf(authenticateWithRetry) {
        case Success(value) => FetchTokenSuccess(replyTo, value)
        case Failure(error) => FetchTokenFailed(error, replyTo)
      }
      fetchedToken()
    case other =>
      buffer.stash(other): Unit
      Behaviors.same
  }

  private def fetchedToken(): Behaviors.Receive[GS1AuthTokenActor.Command] = Behaviors.receiveMessage {
    case FetchTokenSuccess(replyTo, value) =>
      logger.debug("Successfully fetched token")
      replyTo ! GotToken(value)
      buffer.unstashAll(hasToken(value))
    case FetchTokenFailed(error, replyTo) =>
      logger.debug(s"Fail to fetch token", error)
      replyTo ! TokenError(error)
      buffer.unstashAll(noToken())
    case other =>
      buffer.stash(other): Unit
      Behaviors.same
  }

  private def hasToken(token: OAuthAccessToken): Behaviors.Receive[GS1AuthTokenActor.Command] =
    Behaviors.receiveMessagePartial {
      case GetToken(replyTo) =>
        logger.debug("Token requested")
        replyTo ! GotToken(token)
        Behaviors.same
      case RenewToken(replyTo) =>
        logger.debug("Renew token requested, switch to fetching state")
        context.self ! FetchToken(replyTo)
        fetchToken()
    }

}
