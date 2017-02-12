package me.keyskull.android.auth

import android.app.Application
import android.content.Context

/**
  * Created by keyskull on 2017/2/11.
  */
class AuthLauncher extends Application{
  AuthLauncher.authLauncher = Some(this)
  override def onCreate(): Unit = {
    super.onCreate()
    if (!checkGooglePlayServicesAvailable(this))
      AuthOnJavascript.getAuthOnJavascript.initCallback()
  }

}

object AuthLauncher{
  private var authLauncher: Option[AuthLauncher] = None

  def getAuthLauncher: Context = authLauncher match {
    case Some(s) => s
    case None => { s: AuthLauncher =>
      authLauncher = Some(s); s
    }.apply(new AuthLauncher)
  }
}