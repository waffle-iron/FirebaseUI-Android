/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
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