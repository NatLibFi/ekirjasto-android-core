package org.nypl.simplified.ui.accounts.ekirjastopasskey.datamodels

data class PasskeyAuth(
  val success: Boolean,
  val token: String,
  val exp: Long,
  val error: Exception? = null
){
  companion object{
    fun Ok(token: String, exp: Long): PasskeyAuth{
      return PasskeyAuth(true,token,exp)
    }
    fun Fail(exception: Exception): PasskeyAuth{
      return PasskeyAuth(false, "",-1,exception)
    }
  }
}
