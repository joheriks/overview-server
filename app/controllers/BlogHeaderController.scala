package controllers

import controllers.auth.OptionallyAuthorizedAction
import controllers.auth.Authorities.anyUser

object BlogHeaderController extends Controller {
  def show() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    Ok(views.html.BlogHeader.show(None, None))
  }
}

