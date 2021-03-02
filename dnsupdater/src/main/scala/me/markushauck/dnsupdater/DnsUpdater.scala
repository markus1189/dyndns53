package me.markushauck.dnsupdater

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.route53.Route53Client
import software.amazon.awssdk.services.route53.model._

class DnsUpdaterRequest() {
  var queryStringParameters: java.util.Map[String, String] = _
  def getQueryStringParameters: java.util.Map[String, String] =
    queryStringParameters
  def setQueryStringParameters(params: java.util.Map[String, String]): Unit =
    queryStringParameters = params
}

class DnsUpdater extends RequestHandler[DnsUpdaterRequest, String] {
  def handleRequest(
                     event: DnsUpdaterRequest,
                     context: Context
  ): String = {
    Option(event.queryStringParameters.get("ip"))
      .map { newIp =>
      context.getLogger.log(s"Update ip to $newIp IN PROGRESS...")
        DnsUpdater.udpateDns(newIp)
        context.getLogger.log(s"Update ip to $newIp DONE!")
        s"Updated ip to $newIp"
      }
      .getOrElse("No ip given!")
  }

}

object DnsUpdater {
  def main(args: Array[String]): Unit = {
    udpateDns("1.2.3.4")
  }

  def udpateDns(newIp: String): ChangeResourceRecordSetsResponse = {
    val client = Route53Client.builder().region(Region.AWS_GLOBAL).build()

    client.changeResourceRecordSets(
      ChangeResourceRecordSetsRequest
        .builder()
        .hostedZoneId("Z074347725WBFBDYXOJM7") // TODO: config
        .changeBatch(
          ChangeBatch
            .builder()
            .changes(
              Change
                .builder()
                .action(ChangeAction.UPSERT)
                .resourceRecordSet(
                  ResourceRecordSet
                    .builder()
                    .name("ssh.markushauck.me")
                    .`type`(RRType.A)
                    .ttl(60)
                    .resourceRecords(
                      ResourceRecord.builder().value(newIp).build()
                    )
                    .build()
                )
                .build()
            )
            .build()
        )
        .build()
    )
  }

}