package orchestrators.proconnect

import play.api.Logger

import scala.concurrent.ExecutionContext

class ProConnectOrchestrator(
    proConnectClient: ProConnectClient
)(implicit
    val executionContext: ExecutionContext
) {
  val logger: Logger = Logger(this.getClass)

  def login(code: String, state: String) = {
    println(s"------------------ (code,state,id_token) = ${(code, state)} ------------------")
    for {
      token <- proConnectClient.getToken(code)
//      jwtRaw <- proConnectClient.userInfo(token)
//      _ = println(s"------------------ jwtRaw = ${jwtRaw} ------------------")
    } yield token.id_token
  }

}
