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

import java.net.URL

import android.content.Context
import android.util.Log
import android.webkit.WebSettings
import android.widget.Toast
import com.google.firebase.auth.{FacebookAuthProvider, GithubAuthProvider, GoogleAuthProvider}
import org.json.{JSONArray, JSONObject}
import org.xwalk.core.XWalkUIClient.LoadStatus
import org.xwalk.core.{XWalkPreferences, XWalkUIClient, XWalkView}

import scala.concurrent.{Future, Promise}
import scala.util.Try
import com.firebase.ui.auth.R
import scala.collection.JavaConverters._

/**
  * Created by keyskull on 2017/2/2.
  */
class AuthOnJavascript(context: Context) {
  //  AuthOnJavascript.authOnJavascript = Some(this)
  val TAG = "AuthOnJs"
  XWalkPreferences.setValue("enable-javascript", true)
  val webView = new XWalkView(context)
  val webSetting = webView.getSettings()
  webSetting.setSupportMultipleWindows(false)
  webSetting.setJavaScriptEnabled(true)
  //    webSetting.setDatabaseEnabled(true)
  webSetting.setCacheMode(WebSettings.LOAD_NO_CACHE)
  webView.setUIClient(new XWalkUIClient(webView) {
    override def onPageLoadStopped(view: XWalkView, url: String, status: LoadStatus): Unit = {
      super.onPageLoadStopped(view, url, status)
      Log.d("crosswlak", "====== onPageLoadStopped")
      Log.d("crosswlak", "====== Domain " + new URL(context.getResources.getString(R.string.firebase_database_url)).getHost.split('.').toList.toString())
      val appName: String = new URL(context.getResources.getString(R.string.firebase_database_url)).getHost.split('.').headOption.getOrElse("")
      webView.evaluateJavascript(
        s"""
           |var apiKey = "${context.getResources.getString(R.string.google_api_key)}"
           |var config = {
           |apiKey: "${context.getResources.getString(R.string.google_api_key)}",
           |authDomain: "${appName + ".firebaseapp.com"}",
           |databaseURL: "${context.getResources.getString(R.string.firebase_database_url)}",
           |storageBucket: "${appName + ".appspot.com"}",
           |messagingSenderId: "${context.getResources.getString(R.string.gcm_defaultSenderId)}"};
           |firebase.initializeApp(config);
           |var errorCode;
           |var errorMessage;
           |JavaCallback.debugMassage("yooooooooooo"+JSON.stringify(config))
           |firebase.auth().onAuthStateChanged(function(user) {
           |JavaCallback.onAuthStateChanged(JSON.stringify(user));
           |})""".stripMargin, null)
    }
  })
  webView.loadUrl(webView.getContext.getResources.getString(R.string.js_domain))

  private final class PromiseVar[T](var apply: Promise[T])

  private lazy val signInPromise = new PromiseVar(Promise[Unit]())
  private lazy val fetchProvidersForEmailPromise = new PromiseVar(Promise[java.util.List[String]]())
  private lazy val getUserInfoPromise = new PromiseVar(Promise[Option[UserInfo]]())
  private lazy val registerUserPromise = new PromiseVar(Promise[Unit])
  private lazy val sendPasswordResetEmailPromise = new PromiseVar(Promise[Unit])

  def initCallback(jsCallback: JsCallback = new JsCallback) = webView.addJavascriptInterface(jsCallback, "JavaCallback")

  def getUserInfo: Future[Option[UserInfo]] = {
    getUserInfoPromise.apply = Promise[Option[UserInfo]]()
    this.webView.evaluateJavascript(
      """var user = firebase.auth().currentUser;
        |var json= null;
        |if(user)json ={ 'uid': user.uid,
        |'displayName': user.displayName,
        |'photoURL' : user.photoURL,
        |'email': user.email,
        |'emailVerified': user.emailVerified,
        |'isAnonymous' : user.isAnonymous,
        |'providerId' : user.providerId };
        |JavaCallback.getUserInfo(JSON.stringify(json));""".stripMargin, null)
    getUserInfoPromise.apply.future
  }


  def signOut = {
    webView.evaluateJavascript(
      s"""firebase.auth().signOut().then(function() {
          |  // Sign-out successful.
          |}, function(error) {
          |  // An error happened.
          |  JavaCallback.errorMassage(error.code,error.message);
          |});""".stripMargin, null)
  }


  def fetchProvidersForEmail(email: String): Future[java.util.List[String]] = {
    fetchProvidersForEmailPromise.apply = Promise[java.util.List[String]]()
    webView.evaluateJavascript(
      s"""firebase.auth().fetchProvidersForEmail("$email").then(function(a){
          |  // Sign-out successful.
          |  JavaCallback.fetchProvidersForEmailCallback(JSON.stringify(a));
          |}, function(error) {
          |  // An error happened.
          |  JavaCallback.errorMassage(error.code,error.message);
          |})""".stripMargin, null)
    fetchProvidersForEmailPromise.apply.future
  }

  def createUserWithEmailAndPassword(email: String, name: String, password: String): Future[Unit] = {
    registerUserPromise.apply = Promise[Unit]()
    webView.evaluateJavascript(
      s"""firebase.auth().createUserWithEmailAndPassword("$email", "$password").then(function(user){
          | user.updateProfile({displayName:"$name"});
          | JavaCallback.registerSuccess(user);
          | }, function(error) {
          | // An error happened.
          | JavaCallback.errorMassage(error.code,error.message);
          | })""".stripMargin, null)
    registerUserPromise.apply.future
  }

  def loginWithPassword(email: String, password: String) = {
    signInPromise.apply = Promise[Unit]()
    webView.evaluateJavascript(
      s"""firebase.auth().signInWithEmailAndPassword("$email", "$password").then(function(a){
          |JavaCallback.loginSuccess(a);
          |}, function(error) {
          |JavaCallback.errorMassage(error.code,error.message);
          |})""".stripMargin, null)
    signInPromise.apply.future
  }

  def signInWithCredential(provider: String, token: String) = {
    signInPromise.apply = Promise[Unit]()
    val credential = provider match {
      case FacebookAuthProvider.PROVIDER_ID =>
        s"""firebase.auth.FacebookAuthProvider.credential("$token")"""
      case GoogleAuthProvider.PROVIDER_ID =>
        s"""firebase.auth.GoogleAuthProvider.credential("$token")"""
      case GithubAuthProvider.PROVIDER_ID =>
        s"""firebase.auth.GithubAuthProvider.credential("$token")"""
      case _ => ""
    }
    webView.evaluateJavascript(
      s"""firebase.auth().signInWithCredential($credential).then(function(a){
          |JavaCallback.loginSuccess(a);
          |}, function(error) {
          |  // Handle Errors here.
          |  JavaCallback.errorMassage(error.code,error.message);
          |  // The email of the user's account used.
          |  var email = error.email;
          |  // The firebase.auth.AuthCredential type that was used.
          |  var credential = error.credential;
          |});
          |""".stripMargin, null)
    signInPromise.apply.future
  }

  def sendPasswordResetEmail(email: String) = {
    sendPasswordResetEmailPromise.apply = Promise[Unit]()
    webView.evaluateJavascript(
      s"""firebase.auth().sendPasswordResetEmail("$email").then(function(a){
          |JavaCallback.loginSuccess(a);
          |}, function(error) {
          |JavaCallback.errorMassage(error.code,error.message);
          |})""".stripMargin, null)
    sendPasswordResetEmailPromise.apply.future
  }


  class JsCallback {
    val TAG = AuthOnJavascript.this.TAG + "Callback"

    @org.xwalk.core.JavascriptInterface
    def sendPasswordResetEmailSuccess(value:String)={
      sendPasswordResetEmailPromise.apply.success()
    }

    @org.xwalk.core.JavascriptInterface
    def loginSuccess(value:String) ={
      signInPromise.apply.success()
    }

    @org.xwalk.core.JavascriptInterface
    def registerSuccess(value: String) = {
      Log.d(TAG, "registerSuccess =" + value)
      registerUserPromise.apply.success()
    }

    @org.xwalk.core.JavascriptInterface
    def getUserInfo(value: String): Unit = {
      Log.d(TAG, "getUserInfo =" + value)
      if (value != "null") Try {
        val json = new JSONObject(value)
        UserInfo(
          uid = json.getString("uid"),
          displayName = json.getString("displayName"),
          photoURL = json.getString("photoURL"),
          email = json.getString("email"), supportMethod = WebSupport,
          emailVerified = json.getBoolean("emailVerified"),
          isAnonymous = json.getBoolean("isAnonymous"),
          providerId = json.getString("providerId"))
      } map (j => getUserInfoPromise.apply.success(Some(j))) recover { case ex =>
        Log.e(TAG, "getUserInfo error =" + ex.getMessage)
        getUserInfoPromise.apply.success(None)
      }
      else getUserInfoPromise.apply.success(None)
    }

    @org.xwalk.core.JavascriptInterface
    def fetchProvidersForEmailCallback(message: String): Unit = {
      Log.i(TAG, "provider list: =" + message)
      (Try(new JSONArray(message)) map (json =>
        fetchProvidersForEmailPromise.apply.success((for (i <- 1 to json.length()) yield json.getString(i - 1))
          .toList.asJava)) recover {
        case ex =>
          fetchProvidersForEmailPromise.apply.failure(ex)
          Log.e(TAG, ex.getMessage);
          throw ex
      }).toOption
    }

    @org.xwalk.core.JavascriptInterface
    def debugMassage(message: String): Unit = {
      Log.d("JsCallback", "debugMassage =" + message)
    }

    @org.xwalk.core.JavascriptInterface
    def errorMassage(errorCode: String, errorMessage: String): Unit = {
      Log.i(TAG, "onReceiveValue errorCode=" + errorCode)
      Log.i(TAG, "onReceiveValue errorMessage=" + errorMessage)
      if (errorCode != "null") Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show
    }

    @org.xwalk.core.JavascriptInterface
    def onAuthStateChanged(userInfo: String): Unit = {
      Log.i(TAG, "user: =" + userInfo)
      if (userInfo == "null") AuthOnJavascript.providers = List[String]()
      else Try {
          val providerData = new JSONObject(userInfo).getJSONArray("providerData")
          AuthOnJavascript.providers = (for (i <- 0 to providerData.length() - 1)
            yield providerData.getJSONObject(i).getString("providerId")).toList
        } recover {
          case ex => Log.d("JsCallback", ex.getMessage)
            throw ex
        }
    }
  }

}

object AuthOnJavascript {
  private lazy val authOnJavascript: AuthOnJavascript = new AuthOnJavascript(AuthLauncher.getAuthLauncher)
  private var providers = List[String]()

  def getProviders = providers

  def getAuthOnJavascript: AuthOnJavascript = authOnJavascript
}