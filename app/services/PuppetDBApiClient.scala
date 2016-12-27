package services

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.libs.ws.WSClient
import scala.concurrent.Future
import services.RequirementModel.Issue
import play.api.libs.ws.WSResponse
import play.api.libs.json.JsObject
import services.RequirementModel.Endpoint
import play.api.libs.json.JsString
import services.RequirementModel.Service
import services.RequirementModel.Network
import play.api.libs.json.Json
import play.api.libs.ws.WSRequest
import services.PuppetResourceModel.Resource
import services.PuppetResourceModel.IpAddress
import play.api.Logger

/**
 * Client for PuppetDB API calls
 */
trait PuppetDBApiClient {
  
  /**
   * Return the list of resources in a PuppetDB
   */
  def getResources(url: String) : Future[List[Resource]]
  
  /**
   * Return the IP addresses for nodes in a PuppetDB
   */
  def getIpAddresses(url: String) : Future[List[IpAddress]]
  
}

class WSPuppetDBApiClient @Inject() (ws: WSClient)
  (implicit exec: ExecutionContext)
  extends PuppetDBApiClient {
  
  val logger: Logger = Logger(this.getClass())
  
  /**
   * Request body to retrieve all intelliment resources
   */
  val ItlmResourcesQuery = Json.obj(
      "query" -> """["=", "tags", "itlm"]"""
      )
      
  /**  
   * Request body to retrieve all ip addresses for resources with tag itlm
   */
  val ItlmIpAddressQuery = Json.obj(
      "query" -> """
      ["and", ["=","name","ipaddress"], 
      ["in", "certname", ["extract", "certname", ["select_resources", ["=","tags","itlm"]]]]]}"""
      )
  
  override def getResources(url: String) : Future[List[Resource]] = {
    val resourcesUrl = s"$url/query/v4/resources";
    logger.info(s"GET $resourcesUrl")
    val response: Future[WSResponse] = ws.url(resourcesUrl).post(ItlmResourcesQuery)
    return response map { resources => resources.json.as[List[Resource]] }
  }
  
  override def getIpAddresses(url: String) : Future[List[IpAddress]] = {
    val factsUrl = s"$url/query/v4/facts";
    logger.info(s"GET $factsUrl")
    val response: Future[WSResponse] = ws.url(factsUrl).post(ItlmIpAddressQuery)
    return response map { ipAddresses => ipAddresses.json.as[List[IpAddress]] }
  }
  
}