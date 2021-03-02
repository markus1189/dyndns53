package me.markushauck

import software.amazon.awscdk.core.{Construct, Duration, Stack, StackProps}
import software.amazon.awscdk.services.apigatewayv2._
import software.amazon.awscdk.services.apigatewayv2.integrations.LambdaProxyIntegration
import software.amazon.awscdk.services.iam.{
  Effect,
  PolicyStatement,
  ServicePrincipal
}
import software.amazon.awscdk.services.lambda._
import software.amazon.awscdk.services.secretsmanager.{
  Secret,
  SecretStringGenerator
}

import java.util.Collections

class LambdaStack(scope: Construct, id: String, props: StackProps)
    extends Stack(scope, id, props) {

  private val region = props.getEnv.getRegion
  private val account = props.getEnv.getAccount

  val basicAuthSecret: Secret = Secret.Builder
    .create(this, "LambdaAuthorizerSecret")
    .secretName("BasicAuthPassword")
    .generateSecretString(
      SecretStringGenerator
        .builder()
        .passwordLength(50)
        .excludePunctuation(true)
        .build()
    )
    .build()

  private val authorizerFn: IFunction = Function.Builder
    .create(this, "Dyndns53AuthorizerLambda")
    .runtime(Runtime.JAVA_11)
    .timeout(Duration.seconds(10))
    .memorySize(256)
    .handler("me.markushauck.authorizer.LambdaAuthorizer::handleRequest")
    .code(Code.fromAsset("authorizer/target/scala-2.13/lambda-authorizer.jar"))
    .build()

  authorizerFn.addToRolePolicy(
    PolicyStatement.Builder
      .create()
      .sid("BasicAuthPasswordAccessFromAuthorizer")
      .effect(Effect.ALLOW)
      .actions(Collections.singletonList("secretsmanager:GetSecretValue"))
      .resources(Collections.singletonList(basicAuthSecret.getSecretFullArn))
      .build()
  )

  private val dnsupdaterFn: IFunction = Function.Builder
    .create(this, "Dyndns53DnsUpdaterLambda")
    .runtime(Runtime.JAVA_11)
    .timeout(Duration.seconds(45))
    .memorySize(256)
    .handler("me.markushauck.dnsupdater.DnsUpdater::handleRequest")
    .code(Code.fromAsset("dnsupdater/target/scala-2.13/dns-updater.jar"))
    .build()

  dnsupdaterFn.addToRolePolicy(
    PolicyStatement.Builder
      .create()
      .sid("DnsUpdaterAccessToRoute53")
      .effect(Effect.ALLOW)
      .actions(Collections.singletonList("route53:ChangeResourceRecordSets"))
      .resources(
        Collections
          .singletonList("arn:aws:route53:::hostedzone/Z074347725WBFBDYXOJM7")
      )
      .build()
  )

  private val httpApi: HttpApi =
    HttpApi.Builder.create(this, "DynDns53Api").apiName("dyndns53-api").build()

  // Currently not in cdk structures, see https://github.com/aws/aws-cdk/issues/10534#lambda-authorizers
  private val myCustomAuthorizer: CfnAuthorizer = CfnAuthorizer.Builder
    .create(this, "dyndns-53-authorizer")
    .apiId(httpApi.getHttpApiId)
    .authorizerPayloadFormatVersion("2.0")
    .authorizerResultTtlInSeconds(Duration.seconds(30).toSeconds)
    .authorizerType("REQUEST")
    .enableSimpleResponses(true)
    .authorizerUri(
      s"arn:aws:apigateway:$region:lambda:path/2015-03-31/functions/${authorizerFn.getFunctionArn}/invocations"
    )
    .identitySource(Collections.singletonList("$request.header.authorization"))
    .name("query-string-authorizer")
    .build()

  private val lambdaProxyIntegration: LambdaProxyIntegration =
    LambdaProxyIntegration.Builder
      .create()
      .handler(dnsupdaterFn)
      .payloadFormatVersion(PayloadFormatVersion.VERSION_2_0)
      .build()

  private val route = HttpRoute.Builder
    .create(this, "dyndns-53-update-ip-route")
    .httpApi(httpApi)
    .integration(lambdaProxyIntegration)
    .routeKey(HttpRouteKey.`with`("/ip", HttpMethod.GET))
    .build()

  private val cfnRoute = route.getNode.getDefaultChild.asInstanceOf[CfnRoute]
  cfnRoute.setAuthorizationType("CUSTOM")
  cfnRoute.setAuthorizerId(myCustomAuthorizer.getRef)

  authorizerFn.addPermission(
    "dyndns-53-apigateway-permission-custom",
    Permission
      .builder()
      .action("lambda:InvokeFunction")
      .principal(
        ServicePrincipal.Builder.create("apigateway.amazonaws.com").build()
      )
      .sourceArn(
        s"arn:aws:execute-api:$region:$account:${httpApi.getHttpApiId}/authorizers/${myCustomAuthorizer.getRef}"
      )
      .build()
  )
}
