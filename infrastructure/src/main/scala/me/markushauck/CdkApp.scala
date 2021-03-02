package me.markushauck

import software.amazon.awscdk.core.{App, Environment, StackProps}

import scala.util.chaining._

object CdkApp {
  def main(args: Array[String]) {
    new App()
      .tap { app =>
        val lambdas =
          new LambdaStack(
            app,
            "dyndns53-lambdas",
            StackProps
              .builder()
              .env(
                Environment
                  .builder()
                  .account(sys.env.getOrElse("CDK_DEFAULT_ACCOUNT", throw new IllegalArgumentException("No default account found")))
                  .region(sys.env.getOrElse("CDK_DEFAULT_REGION", throw new IllegalArgumentException("No default region found")))
                  .build()
              )
              .build()
          )
      }
      .synth()
  }
}
