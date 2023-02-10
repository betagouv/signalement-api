package actors

import akka.actor.typed.Behavior
import akka.actor.typed.DispatcherSelector
import akka.actor.typed.scaladsl.Behaviors
import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter

import java.io.InputStream
import java.io.OutputStream
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future

object HtmlConverterActor {
  case class ConvertCommand(
      htmlStream: InputStream,
      outputStream: OutputStream,
      converterProperties: ConverterProperties
  )

  def create(): Behavior[ConvertCommand] =
    Behaviors.setup { context =>
      implicit val ec: ExecutionContextExecutor =
        context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))

      Behaviors.receiveMessage { command =>
        Future {
          HtmlConverter.convertToPdf(
            command.htmlStream,
            command.outputStream,
            command.converterProperties
          )
        }.onComplete(_ => command.outputStream.close())
        Behaviors.same
      }
    }
}
