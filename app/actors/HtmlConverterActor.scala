package actors

import akka.actor.typed.Behavior
import akka.actor.typed.DispatcherSelector
import akka.actor.typed.scaladsl.Behaviors
import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import play.api.Logger

import java.io.InputStream
import java.io.OutputStream
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

object HtmlConverterActor {
  sealed trait ConvertCommand
  case class Convert(
      htmlStream: InputStream,
      outputStream: OutputStream,
      converterProperties: ConverterProperties
  ) extends ConvertCommand
  case class ConvertSuccess(outputStream: OutputStream) extends ConvertCommand
  case class ConvertFailed(outputStream: OutputStream)  extends ConvertCommand

  val logger: Logger = Logger(this.getClass)

  def create(): Behavior[ConvertCommand] =
    Behaviors.setup { context =>
      implicit val ec: ExecutionContext =
        context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))

      Behaviors.receiveMessage {
        case Convert(htmlStream, outputStream, converterProperties) =>
          logger.debug("Begin html conversion")
          val job = Future {
            HtmlConverter.convertToPdf(htmlStream, outputStream, converterProperties)
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
