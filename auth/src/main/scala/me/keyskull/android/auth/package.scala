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
package me.keyskull.android

import android.app.Activity
import android.content.Context
import android.support.annotation.NonNull
import android.util.Log
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.firebase.ui.auth.util.{CredentialsApiHelper, GoogleApiClientTaskHelper, PlayServicesHelper}
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.{GoogleApiClient, Result, Status}
import com.google.android.gms.tasks.{Continuation, Task, TaskCompletionSource, Tasks}
import com.google.firebase.auth.FirebaseAuth

import scala.util.Try
import scala.concurrent._

/**
  * Created by keyskull on 2017/2/9.
  */
package object auth {
  private var haveGooglePlayServices_var: Boolean = false
  lazy val firebaseAuth = FirebaseAuth.getInstance

  def checkGooglePlayServicesAvailable(context: Context): Boolean = {
    haveGooglePlayServices_var = PlayServicesHelper.getGoogleApiAvailability
      .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    haveGooglePlayServices_var
  }

  def haveGooglePlayServices = haveGooglePlayServices_var

  def getUserInfo: Option[UserInfo] = {
    import scala.concurrent.duration._
    import ExecutionContext.Implicits.global
    Await.result({
      if (haveGooglePlayServices) Future.successful {
        val user = FirebaseAuth.getInstance().getCurrentUser
        //      Log.d("firebase", user.toString)
        (Try(UserInfo(
          uid = user.getUid, displayName = user.getDisplayName,
          photoURL = Try {
            user.getPhotoUrl.getPath
          }.getOrElse(""),
          email = user.getEmail, supportMethod = GoogleServiceSupport,
          emailVerified = user.isEmailVerified,
          isAnonymous = user.isAnonymous,
          providerId = user.getProviderId)) recover[UserInfo] {
          case ex => Log.d("firebase", ex.getMessage); throw ex
        }).toOption
      } else AuthOnJavascript.getAuthOnJavascript.getUserInfo
    } recover { case ex => Log.e("UserInfo", ex.toString); None }, 5.second)
  }

  def getUserInfoFuture: Future[Option[UserInfo]] = {
    if (haveGooglePlayServices) Future.successful {
      val user = FirebaseAuth.getInstance().getCurrentUser
      //      Log.d("firebase", user.toString)
      (Try(UserInfo(
        uid = user.getUid, displayName = user.getDisplayName,
        photoURL = Try {
          user.getPhotoUrl.getPath
        }.getOrElse(""),
        email = user.getEmail, supportMethod = GoogleServiceSupport,
        emailVerified = user.isEmailVerified,
        isAnonymous = user.isAnonymous,
        providerId = user.getProviderId)) recover[UserInfo] {
        case ex => Log.d("firebase", ex.getMessage); throw ex
      }).toOption
    } else AuthOnJavascript.getAuthOnJavascript.getUserInfo
  }

  def signOut(activity: Activity): Task[Void] = {
    // Facebook sign out
    if (FacebookSdk.isInitialized) LoginManager.getInstance.logOut()
    if (haveGooglePlayServices) {
      // Get helper for Google Sign In and Credentials API
      val taskHelper: GoogleApiClientTaskHelper = GoogleApiClientTaskHelper.getInstance(activity)
      taskHelper.getBuilder.addApi(Auth.CREDENTIALS_API).addApi(Auth.GOOGLE_SIGN_IN_API, GoogleSignInOptions.DEFAULT_SIGN_IN)

      // Get Credentials Helper
      val credentialsHelper: CredentialsApiHelper = CredentialsApiHelper.getInstance(taskHelper)

      // Disable credentials auto sign-in
      val disableCredentialsTask: Task[Status] = credentialsHelper.disableAutoSignIn

      // Google sign out
      val googleSignOutTask: Task[Void] = taskHelper.getConnectedGoogleApiClient.continueWith(new Continuation[GoogleApiClient, Void]() {
        @throws[Exception]
        def then(@NonNull task: Task[GoogleApiClient]): Void = {
          if (task.isSuccessful) Auth.GoogleSignInApi.signOut(task.getResult)
          null
        }
      })
      firebaseAuth.signOut()
      return Tasks.whenAll(disableCredentialsTask, googleSignOutTask)
    }
    else {
      val task = new TaskCompletionSource[Unit]()
      task.setResult({
        AuthOnJavascript.getAuthOnJavascript.signOut
      })
      Tasks.whenAll(task.getTask)
    }
  }



}
