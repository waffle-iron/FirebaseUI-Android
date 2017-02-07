package me.keyskull.android.auth

case class UserInfo(uid:String, displayName:String,
                    photoURL:String, email:String,
                    authSupport:AuthSupport,
                    emailVerified:Boolean,
                    isAnonymous:Boolean,
                    providerId:String
                   ){
  def getDisplayName = displayName
  def getUid = uid
  def getPhotoUrl =photoURL
  def getEmail =email
}
