package me.markushauck.authorizer

import com.amazonaws.services.lambda.runtime.events.SimpleIAMPolicyResponse
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest

import java.util
import java.util.{Base64, Collections}
import scala.jdk.CollectionConverters._

class LambdaAuthorizerRequest() {
  var headers: java.util.Map[String, String] = _
  def getHeaders: util.Map[String, String] = headers
  def setHeaders(hs: java.util.Map[String, String]): Unit = headers = hs
}

class LambdaAuthorizer
    extends RequestHandler[LambdaAuthorizerRequest, SimpleIAMPolicyResponse] {

  private[this] val client = SecretsManagerClient.builder().build()
  private[this] val expectedUser = "dyndns53"
  private[this] val expectedPass = retrievePassword()

  override def handleRequest(
      event: LambdaAuthorizerRequest,
      context: Context
  ): SimpleIAMPolicyResponse = {
    event.headers.asScala
      .find { case (k, _) => k.equalsIgnoreCase("authorization") }
      .map {
        case (_, authHeaderValue) =>
          val Array(user, pass) = new String(
            Base64.getDecoder.decode(authHeaderValue.stripPrefix("Basic "))
          ).split(':')

          new SimpleIAMPolicyResponse(
            isAuthorized(user, pass),
            Collections.emptyMap()
          )
      }
      .getOrElse {
        new SimpleIAMPolicyResponse(
          false,
          Collections.singletonMap("error", "no authorization header")
        )
      }
  }

  private[this] def isAuthorized(user: String, pass: String): Boolean =
    user == expectedUser && pass == expectedPass

  private[this] def retrievePassword(): String = {
    client
      .getSecretValue(
        GetSecretValueRequest.builder().secretId("BasicAuthPassword").build()
      )
      .secretString()
  }
}
