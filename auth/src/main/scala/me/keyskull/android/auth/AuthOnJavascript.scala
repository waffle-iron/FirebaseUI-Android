package me.keyskull.android.auth

import java.net.URL

import android.content.Context
import android.util.Log
import android.webkit.{ValueCallback, WebSettings}
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

  class AllPromise {
    var signInPromies: Promise[Boolean] = Promise[Boolean]()
    var fetchProvidersForEmailPromise = Promise[java.util.List[String]]()
    var getUserInfoPromise = Promise[Option[UserInfo]]()
    var registerUserFuture = Promise[Unit]
  }
  private lazy val allPromise = new AllPromise
  import allPromise._


  def initCallback(jsCallback: JsCallback = new JsCallback) = webView.addJavascriptInterface(jsCallback, "JavaCallback")

  def getUserInfo: Future[Option[UserInfo]] = {
    allPromise.getUserInfoPromise = Promise[Option[UserInfo]]()
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
    getUserInfoPromise.future
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
    fetchProvidersForEmailPromise = Promise[java.util.List[String]]()
    webView.evaluateJavascript(
      s"""firebase.auth().fetchProvidersForEmail("$email").then(function(a){
          |  // Sign-out successful.
          |  JavaCallback.fetchProvidersForEmailCallback(JSON.stringify(a));
          |}, function(error) {
          |  // An error happened.
          |  JavaCallback.errorMassage(error.code,error.message);
          |})""".stripMargin, null)
    fetchProvidersForEmailPromise.future
  }

  def registerUser(email: String, name: String, password: String): Future[Unit] = {
    registerUserFuture = Promise[Unit]()
    webView.evaluateJavascript(
      s"""firebase.auth().createUserWithEmailAndPassword("$email", "$password").then(function(a){
          | // Sign-out successful.
          | firebase.User().updateProfile({displayName:"$name"})
          | JavaCallback.registerSuccess(a);
          | }, function(error) {
          | // An error happened.
          | JavaCallback.errorMassage(error.code,error.message);
          | })""".stripMargin, null)
    registerUserFuture.future
  }

  def loginWithPassword(email: String, password: String) = {
    signInPromies = Promise[Boolean]()
    webView.evaluateJavascript(
      s"""firebase.auth().signInWithEmailAndPassword("$email", "$password").catch(function(error) {
          |JavaCallback.errorMassage(error.code,error.message);
          |})""".stripMargin, null)
    signInPromies.future
  }

  def signInWithCredential(provider: String, token: String) = {
    signInPromies = Promise[Boolean]()
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
      s"""firebase.auth().signInWithCredential($credential).catch(function(error) {
          |  // Handle Errors here.
          |  JavaCallback.errorMassage(error.code,error.message);
          |  // The email of the user's account used.
          |  var email = error.email;
          |  // The firebase.auth.AuthCredential type that was used.
          |  var credential = error.credential;
          | });
          |""".stripMargin, null)
    signInPromies.future
  }

  class JsCallback {
    val TAG = AuthOnJavascript.this.TAG + "Callback"
    def registerSuccess(value:String) ={
        Log.d(TAG,"registerSuccess =" + value)
        registerUserFuture.success()
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
      } map (j => getUserInfoPromise.success(Some(j))) recover { case ex =>
        Log.e(TAG, "getUserInfo error =" + ex.getMessage)
        getUserInfoPromise.success(None)
      }
      else getUserInfoPromise.success(None)
    }

    @org.xwalk.core.JavascriptInterface
    def fetchProvidersForEmailCallback(message: String): Unit = {
      Log.i(TAG, "provider list: =" + message)
      (Try(new JSONArray(message)) map (json =>
        fetchProvidersForEmailPromise.success((for (i <- 1 to json.length()) yield json.getString(i - 1))
          .toList.asJava)) recover {
        case ex =>
          fetchProvidersForEmailPromise.failure(ex)
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
      if (errorCode != "null") Toast.makeText(webView.getContext, errorMessage, Toast.LENGTH_LONG).show
    }

    @org.xwalk.core.JavascriptInterface
    def onAuthStateChanged(userInfo: String): Unit = {
      Log.i(TAG, "user: =" + userInfo)
      if (userInfo == "null") AuthOnJavascript.providers = List[String]()
      else {
        signInPromies.success(true)
        Try {
          val providerData = new JSONObject(userInfo).getJSONArray("providerData")
          AuthOnJavascript.providers = (for (i <- 0 to providerData.length() - 1)
            yield providerData.getJSONObject(i).getString("providerId")).toList
        } recover {
          case ex => Log.d("JsCallback", ex.getMessage)
            throw ex
        }
      }

      //      onCompleteListener map (_)
    }
  }

}

object AuthOnJavascript {
  private lazy val authOnJavascript: AuthOnJavascript = new AuthOnJavascript(AuthLauncher.getAuthLauncher)
  private var providers = List[String]()

  def getProviders = providers

  def getAuthOnJavascript: AuthOnJavascript = authOnJavascript
}