ThisBuild / scalaVersion := "2.13.5"
ThisBuild / organization := "me.markushauck"

lazy val versions = new {
  val awsCdk = "1.91.0"
  val awsLambdaCore = "1.2.1"
  val awsLambdaEvents = "3.7.0"
  val awsSdk = "2.16.9"
}

lazy val commonAssemblySettings = List(assemblyMergeStrategy in assembly := {
  case PathList(ps@_*) if ps.last == "io.netty.versions.properties" => MergeStrategy.first
  case PathList(ps@_*) if ps.last == "module-info.class" => MergeStrategy.first
  case x => (assemblyMergeStrategy in assembly).value(x)
})

lazy val authorizer = (project in file("authorizer"))
  .settings(
    name := "LambdaAuthorizer",
    libraryDependencies ++= List(
      "com.amazonaws" % "aws-lambda-java-core" % versions.awsLambdaCore,
      "com.amazonaws" % "aws-lambda-java-events" % versions.awsLambdaEvents,
      "software.amazon.awssdk" % "secretsmanager" % versions.awsSdk
    ),
    assemblyJarName in assembly := "lambda-authorizer.jar"
  )
  .settings(commonAssemblySettings: _*)

lazy val dnsupdater = (project in file("dnsupdater"))
  .settings(
    name := "DnsUpdater",
    libraryDependencies ++= List(
      "com.amazonaws" % "aws-lambda-java-core" % versions.awsLambdaCore,
      "com.amazonaws" % "aws-lambda-java-events" % versions.awsLambdaEvents,
      "software.amazon.awssdk" % "route53" % versions.awsSdk
    ),
    assemblyJarName in assembly := "dns-updater.jar"
  )
  .settings(commonAssemblySettings: _*)

lazy val infrastructure = (project in file("infrastructure"))
  .settings(
    name := "Infrastructure",
    libraryDependencies ++= List(
      "software.amazon.awscdk" % "core" % versions.awsCdk,
      "software.amazon.awscdk" % "apigatewayv2" % versions.awsCdk,
      "software.amazon.awscdk" % "apigatewayv2-authorizers" % versions.awsCdk,
      "software.amazon.awscdk" % "apigatewayv2-integrations" % versions.awsCdk,
      "software.amazon.awscdk" % "lambda" % versions.awsCdk,
      "software.amazon.awscdk" % "secretsmanager" % versions.awsCdk
    ),
    (Compile / compile) := (Compile / compile)
      .dependsOn(authorizer / assembly)
      .dependsOn(dnsupdater / assembly)
      .value
  )
