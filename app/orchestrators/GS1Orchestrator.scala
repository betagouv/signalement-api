package orchestrators

import actors.GS1AuthTokenActor
import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout
import models.gs1.{GS1APIProduct, OAuthAccessToken}
import repositories.gs1.GS1RepositoryInterface
import services.GS1ServiceInterface

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class GS1Orchestrator(gs1AuthTokenActor: ActorRef[GS1AuthTokenActor.Command], gs1Service: GS1ServiceInterface,
                      gs1Repository: GS1RepositoryInterface)(implicit
                                                             val executionContext: ExecutionContext,
                                                             timeout: Timeout,
                                                             scheduler: Scheduler
) {

  private def getToken(
      request: ActorRef[GS1AuthTokenActor.Reply] => GS1AuthTokenActor.Command
  ): Future[OAuthAccessToken] =
    gs1AuthTokenActor.ask[GS1AuthTokenActor.Reply](request).flatMap {
      case GS1AuthTokenActor.GotToken(token)   => Future.successful(token)
      case GS1AuthTokenActor.TokenError(error) => Future.failed(error)
    }

  def get_(gtin: String) =
    for {
      accessToken <- getToken(GS1AuthTokenActor.GetToken.apply)
      firstTry    <- gs1Service.getProductByGTIN(accessToken, gtin)

      result <- firstTry match {
        case Right(value) => Future.successful(value)
        case Left(_) =>
          for {
            renewedAccessToken <- getToken(GS1AuthTokenActor.RenewToken.apply)
            secondTry          <- gs1Service.getProductByGTIN(renewedAccessToken, gtin)
            r <- secondTry match {
              case Right(value) => Future.successful(value)
              case Left(_)      => Future.failed(new Exception("Unexpected"))
            }
          } yield r
      }

    } yield result

  def get(gtin: String) = {
    for {
      existingProduct <- gs1Repository.get(gtin)
      product <- existingProduct match {
        case Some(_) => Future.successful(existingProduct)
        case None =>
          for {
            test <- get_(gtin)
            _ <- test match {
              case Some(a) =>
                gs1Repository.create(GS1APIProduct.toDomain(a))
              case None => Future.successful(())
            }
          } yield test.map(GS1APIProduct.toDomain)
      }
    } yield product
  }
}
