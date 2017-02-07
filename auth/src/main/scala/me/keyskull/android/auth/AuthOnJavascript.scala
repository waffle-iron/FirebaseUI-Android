package me.keyskull.android.auth

import android.app.Activity
import android.util.Log
import android.webkit.{ValueCallback, WebSettings}
import android.widget.Toast
import com.google.firebase.auth.{FacebookAuthProvider, GithubAuthProvider, GoogleAuthProvider}
import org.json.{JSONArray, JSONObject}
import org.xwalk.core.XWalkUIClient.LoadStatus
import org.xwalk.core.{ XWalkUIClient, XWalkView}

import scala.concurrent.{Future, Promise}
import scala.util.Try
import com.firebase.ui.auth.R
import com.google.android.gms.tasks.OnCompleteListener

/**
  * Created by keyskull on 2017/2/2.
  */
class AuthOnJavascript(activity: Activity) {
  AuthOnJavascript.authOnJavascript = Some(this)
  val TAG = "AuthOnJs"
  val webView = new XWalkView(activity)
  //  XWalkPreferences.setValue("enable-javascript", true)
  val webSetting = webView.getSettings()
  webSetting.setSupportMultipleWindows(false)
  webSetting.setJavaScriptEnabled(true)
  //    webSetting.setDatabaseEnabled(true)
  webSetting.setCacheMode(WebSettings.LOAD_NO_CACHE)
  webView.setUIClient(new XWalkUIClient(webView) {
    override def onPageLoadStopped(view: XWalkView, url: String, status: LoadStatus): Unit = {
      super.onPageLoadStopped(view, url, status)
      Log.d("crosswlak", "====== onPageLoadStopped ")
      webView.evaluateJavascript(
        s"""firebase.auth().onAuthStateChanged(function(user) {
            |JavaCallback.onAuthStateChanged(JSON.stringify(user));
            |})""".stripMargin, null)
    }
  })
  webView.reload(XWalkView.RELOAD_IGNORE_CACHE)
  webView.loadUrl(webView.getContext.getResources.getString(R.string.js_domain))


  private var onCompleteListener:Option[OnCompleteListener[_]] = None
  def addOnCompleteListener(val1 :OnCompleteListener[_])= onCompleteListener = Some(val1)

  def initCallback(jsCallback: JsCallback = new JsCallback) = webView.addJavascriptInterface(jsCallback, "JavaCallback")

  def getUserInfo: Future[Option[UserInfo]] = {
    val promise = Promise[Option[UserInfo]]
    webView.evaluateJavascript(
      """var user = firebase.auth().currentUser;
        |var json= null;
        |if(user)json ={ 'uid': user.uid,
        |'displayName': user.displayName,
        |'photoURL' : user.photoURL,
        |'email': user.email,
        |'emailVerified': user.emailVerified,
        |'isAnonymous' : user.isAnonymous,
        |'providerId' : user.providerId };
        |json;""".stripMargin, new ValueCallback[String]() {
        override def onReceiveValue(value: String): Unit = {
          Log.d(TAG, "getUserInfo =" + value)
          Try(new JSONObject(value)).map { json =>
            Log.d(TAG, json.toString)
            Try {
              UserInfo(
                uid = json.getString("uid"),
                displayName = json.getString("displayName"),
                photoURL = json.getString("photoURL"),
                email = json.getString("email"), authSupport = WebSupport,
                emailVerified = json.getBoolean("emailVerified"),
                isAnonymous = json.getBoolean("isAnonymous"),
                providerId = json.getString("providerId")
              )
            } map (j => promise.success(Some(j))) recover { case ex =>
              Log.e(TAG, "getUserInfo error =" + ex.getMessage)
              promise.success(None)
            }
          } recover { case ex =>
            Log.e(TAG, "getUserInfo error =" + ex.getMessage)
            promise.success(None)
          }
        }
      })
    promise.future
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

  private val fetchProvidersForEmailPromise = Promise[List[String]]

  def fetchProvidersForEmail(email: String): Future[List[String]] = {
    webView.evaluateJavascript(
      s"""firebase.auth().fetchProvidersForEmail("$email").then(function(a){
          |  JavaCallback.fetchProvidersForEmailCallback(JSON.stringify(a));
          |  // Sign-out successful.
          |}, function(error) {
          |  // An error happened.
          |  JavaCallback.errorMassage(error.code,error.message);
          |})""".stripMargin, null)
    fetchProvidersForEmailPromise.future
  }

  def loginWithPassword(email: String, password: String) = {
    webView.evaluateJavascript(
      s"""firebase.auth().signInWithEmailAndPassword("$email", "$password").catch(function(error) {
          |JavaCallback.errorMassage(error.code,error.message);
          |})""".stripMargin, null)
  }

  def signInWithCredential(provider: String, token: String) = {
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
  }

  class JsCallback {
    val TAG = AuthOnJavascript.this.TAG + "Callback"

    @org.xwalk.core.JavascriptInterface
    def fetchProvidersForEmailCallback(message: String): Unit = {
      Log.i(TAG, "provider list: =" + message)
      (Try(new JSONArray(message)) map (json =>
        fetchProvidersForEmailPromise.success((for (i <- 1 to json.length()) yield json.getString(i - 1))
          .toList)) recover {
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
//      onCompleteListener map (_)
    }
  }

}

object AuthOnJavascript {
  private var authOnJavascript: Option[AuthOnJavascript] = None

  def getAuthOnJavascript = authOnJavascript


}