package controllers

import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import play.api.i18n.I18nSupport
import play.api.mvc.InjectedController
import utils.silhouette.auth.AuthEnv

trait BaseController extends InjectedController {

  def silhouette: Silhouette[AuthEnv]

  def SecuredAction = silhouette.SecuredAction

  def UnsecuredAction = silhouette.UnsecuredAction

  def UserAwareAction = silhouette.UserAwareAction

  implicit def securedRequest2User[A](implicit req: SecuredRequest[AuthEnv, A]) = req.identity

  implicit def securedRequest2UserOpt[A](implicit req: SecuredRequest[AuthEnv, A]) = Some(req.identity)

  implicit def userAwareRequest2UserOpt[A](implicit req: UserAwareRequest[AuthEnv, A]) = req.identity

}
