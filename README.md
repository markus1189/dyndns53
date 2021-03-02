# Dyndns53 - AWS implementation of dyndns with Route53

This project implements an http endpoint which can be used with dyndns
compatible routers to update a domain in route53.

The project uses
  - sbt multi-module build for the 2 lambdas + aws cdk
  - CDK with Scala for the Cloudformation Stack
  - apigatewayv2 Lambda Authorizer checking the basic auth headaer
  - secret in AWS SSM for the expected password
  - scala lambda to update the hosted zone's record from query parameter
