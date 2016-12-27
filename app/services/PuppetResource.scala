package services

import play.api.libs.json._
import play.api.libs.json.Reads._ 
import javax.inject.Singleton
import javax.inject.Inject
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSRequest
import scala.concurrent.Future
import play.api.libs.ws.WSResponse
import scala.concurrent.ExecutionContext
import scala.util.Success
import scala.util.Failure
import services.PuppetResourceModel.PuppetResources
import services.PuppetResourceModel.Provider
import services.PuppetResourceModel.Consumer
import services.PuppetResourceModel.Resource
import services.PuppetResourceModel.IpAddress

/**
 * This service provides the Intelliment Resources defined in Puppet
 */
trait PuppetResource {
  
  /**
   * Retrieve the resources from a PuppetDB
   */
  def getPuppetResources(puppetDbUrl: String): Future[PuppetResources]
  
}

/**
 * This implementation provides the resources from PuppetDB by using
 * a REST call
 */
@Singleton
class RestPuppetResource @Inject() (api: PuppetDBApiClient)(implicit exec: ExecutionContext) extends PuppetResource {
  
  override def getPuppetResources(puppetDbUrl: String): Future[PuppetResources] = {
    val futureResources = api.getResources(puppetDbUrl)
    val futureIpAddresses = api.getIpAddresses(puppetDbUrl)
    
    return for {
      f1 <- futureResources
      f2 <- futureIpAddresses
    } yield createPuppetResources(f1, f2)
    
  }
  
  def createPuppetResources(resources: List[Resource], ips: List[IpAddress]): PuppetResources = {
    def isProvider(res: Resource): Boolean = { res.resourceType.equalsIgnoreCase("itlm::provides") }
    def isConsumer(res: Resource): Boolean = { res.resourceType.equalsIgnoreCase("itlm::consumes") }
    val ipsGrouped = ips groupBy { ipAddress => ipAddress.node }
    
    def getProviders(res: List[Resource]) = {
      res withFilter isProvider map {
        p => toProvider(p, ipsGrouped(p.node))
      }
    }
    
    def getConsumers(res: List[Resource]) = {
      res withFilter isConsumer map {
        c => toConsumer(c, ipsGrouped(c.node))
      }
    }
    
    PuppetResources(getProviders(resources), getConsumers(resources))
  }
  
  private def toProvider(provider: Resource, ipAddress: List[IpAddress]) : Provider = {
    val node = provider.node
    val service = provider.title
    val ports = provider.parameters.ports.getOrElse(List("all ports"))
    val source = provider.parameters.source.getOrElse(List("all-consumers"))
    val action = provider.parameters.action.getOrElse("allow")
    val ip = if (ipAddress.isEmpty) "" else ipAddress.head.ipAddress
    
    Provider(node, ip, service, ports, source, provider.tags, action)
  }
  
  private def toConsumer(consumer: Resource, ipAddress: List[IpAddress]) : Consumer = {
    val node = consumer.node
    val service = consumer.parameters.service.getOrElse(List("undefined"))
    val dest = consumer.parameters.destination.getOrElse(List("undefined"))
    val action = consumer.parameters.action.getOrElse("allow")
    val ip = if (ipAddress.isEmpty) "" else ipAddress.head.ipAddress
    
    Consumer(node, ip, service, dest, consumer.tags, action)
  }
  
  
}