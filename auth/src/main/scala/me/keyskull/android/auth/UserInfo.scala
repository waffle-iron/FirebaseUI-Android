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
