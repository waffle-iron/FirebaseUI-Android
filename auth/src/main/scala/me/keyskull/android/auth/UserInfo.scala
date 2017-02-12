package me.keyskull.android.auth

case class UserInfo(uid:String, displayName:String,
                    photoURL:String, email:String,
                    supportMethod:SupportMethod,
                    emailVerified:Boolean,
                    isAnonymous:Boolean,
                    providerId:String
                   ){
  import scala.collection.JavaConverters._
  def getDisplayName = displayName
  def getUid = uid
  def getPhotoUrl = photoURL
  def getEmail =email
  def getProviderId = providerId
  def getProviders:java.util.List[java.lang.String] =supportMethod match {
    case GoogleServiceSupport => firebaseAuth.getCurrentUser.getProviders
    case WebSupport => AuthOnJavascript.getProviders.asJava
    case _ =>new java.util.ArrayList()
  }
}
