package actors

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.DispatcherSelector
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.xhtmlrenderer.pdf.ITextRenderer
import play.api.Logger

import java.io.OutputStream
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

object HtmlConverterActor {
  sealed trait ConvertCommand
  case class Convert(
      htmlBody: String,
      outputStream: OutputStream,
      baseUrl: String
  ) extends ConvertCommand
  case class ConvertSuccess(outputStream: OutputStream) extends ConvertCommand
  case class ConvertFailed(outputStream: OutputStream)  extends ConvertCommand

  val logger: Logger = Logger(this.getClass)

  def create(): Behavior[ConvertCommand] =
    Behaviors.setup { context =>
      implicit val ec: ExecutionContext =
        context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))

      Behaviors.receiveMessage {
        case Convert(htmlBody, outputStream, baseUrl) =>
          logger.debug("Begin html conversion")
          val job = Future {
            val renderer = new ITextRenderer()
            renderer.setDocumentFromString(htmlBody, baseUrl)
            renderer.layout()
            renderer.createPDF(outputStream)
            renderer.finishPDF()
          }
          context.pipeToSelf(job) {
            case Success(_) => ConvertSuccess(outputStream)
            case Failure(_) => ConvertFailed(outputStream)
          }
          Behaviors.same

        case ConvertSuccess(outputStream) =>
          logger.debug("Convert succeeded")
          outputStream.close()
          Behaviors.same

        case ConvertFailed(outputStream) =>
          logger.debug("Convert failed")
          outputStream.close()
          Behaviors.same
      }
    }
}
