package services

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.collection.breakOut
import play.api.libs.ws.WSClient
import play.api.Configuration
import scala.concurrent.Future
import services.RequirementModel.Issue
import play.api.libs.ws.WSResponse
import play.api.libs.json.JsObject
import services.RequirementModel.Endpoint
import play.api.libs.json.JsString
import services.RequirementModel._
import play.api.libs.json.Json
import play.api.libs.ws.WSRequest
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.JsPath
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsLookupResult
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * Client for Intelliment API calls
 */
trait IntellimentApiClient {
  
  def getScenarios: Future[List[JsObject]]
 
  def getIssues(scenarioId: String): Future[Map[String, List[Issue]]] 
  
  def getNetworks(scenarioId: String): Future[List[Network]]
  
  def getNetworkByName(scenarioId: String, name: String): Future[Option[Network]]
  
  def getRequirements(scenarioId: String, query: Option[String]): Future[List[Requirement]]
  
  def createRequirement(scenarioId: String, source: Endpoint, destination: Endpoint, tags: List[String], services: List[Service]): Future[String]
  
  def removeRequirement(scenarioId: String, id: String): Future[String]
  
  def getServices(scenarioId: String): Future[Map[String, String]]
}

class WSIntellimentApiClient @Inject() (ws: WSClient, conf: Configuration)
  (implicit exec: ExecutionContext)
  extends IntellimentApiClient {
  
  val logger: Logger = Logger(this.getClass())
  
  // Constants
  val IntellimentServer = conf.underlying.getString("intelliment.server")
  val ApiToken = conf.underlying.getString("intelliment.token")
  val BaseUri = s"$IntellimentServer/api/v1/policy-automation/scenarios"
  
  override def getScenarios: Future[List[JsObject]] = {
    val url = s"$BaseUri?limit=100"
    val response: Future[WSResponse] = get(url)
    response map { scenarios => scenarios.json.\("data").as[List[JsObject]] }
  }
  
  override def getIssues(scenarioId: String): Future[Map[String, List[Issue]]] = {
    val url = s"$BaseUri/$scenarioId/issues?limit=100"
    val response: Future[WSResponse] = get(url)
    val list = response map { issues => { issues.json.\("data").as[List[Issue]]} }
    
    list map { x => x.groupBy { issue => issue.elementId } }
  }
  
  override def getNetworks(scenarioId: String): Future[List[Network]] = {
    val url = s"$BaseUri/$scenarioId/objects?types=zone,internet&limit=100"
    val response: Future[WSResponse] = get(url)
    response map { _.json.\("data").as[List[Network]] }
  }
  
  override def getNetworkByName(scenarioId: String, name: String): Future[Option[Network]] = {
    val url = s"$BaseUri/$scenarioId/objects?types=zone,internet&limit=100"
    val response: Future[WSResponse] = get(s"$url&name=$name")
    response map { _.json.\("data").as[List[Network]] } map { _.headOption }
  }
  
  override def getRequirements(scenarioId: String, query: Option[String]): Future[List[Requirement]] = {
    val reqUrl = s"$BaseUri/$scenarioId/requirements?expand=source,destination,services"
    val url = query match {
      case None => reqUrl
      case Some(q) => reqUrl + "&" + q
    }
    
     val response: Future[WSResponse] = get(url)
     val reqs = response map { _.json.\("data").as[List[RequirementApi]] }
     reqs map { r => toReq(r) }
  }
  
  override def removeRequirement(scenarioId: String, id: String): Future[String] = {
    val url = s"$BaseUri/$scenarioId/requirements"
    val response: Future[WSResponse] = delete(url + "/" + id)
    response flatMap( x_ => Future { "success" })
  }
  
  override def createRequirement(scenarioId: String, source: Endpoint, destination: Endpoint, tags: List[String], services: List[Service]): Future[String] = {
    // FIXME: Changes this block to avoid await
    val futureServiceBinding = getServiceMap(scenarioId)
    val serviceBinding = Await.result(futureServiceBinding, Duration.Inf)
    
    val servicesJson = for {
      service <- services
      port <- service.ports
    } yield JsObject(Seq(
          "id" -> JsString(serviceBinding(port))
        ))
    
    val config: JsValue = JsArray(Seq(
      JsObject(Seq(
        "enabled" -> JsBoolean(true),
        "type" -> JsString("custom"),
        "services" -> Json.toJson(servicesJson)
      ))))
    
    val tagJson: JsValue = Json.toJson(tags)
      
    val request = for {
      srcOpt <- endpointJson(scenarioId, source)
      dstOpt <- endpointJson(scenarioId, destination)
    } yield for {
      src <- srcOpt
      dst <- dstOpt
    } yield JsObject(Seq(
      "description" -> JsString("Created from Puppet"),
      "source" -> src,
      "destination" -> dst,
      "configuration" -> config,
      "tags" -> tagJson,
      "action" -> JsString("allow")
      ))
    
    val reqUrl = s"$BaseUri/$scenarioId/requirements"
    request.flatMap {
      case Some(req) => {
        post(reqUrl, req) flatMap(response => response.status match {
          case 200 => Future { "success" }
          case 422 => update(reqUrl, req, response) 
          case _ => Future { "error" }
        })  
      }
      case None => Future { "error" }
    }
    
  }
  
  override def getServices(scenarioId: String): Future[Map[String, String]] = getServiceMap(scenarioId)
  
  private def endpointJson(scenarioId: String, endpoint: Endpoint): Future[Option[JsObject]] = endpoint match {
    case Endpoint(None, _, Some(ip)) => 
      Future { 
        Some(JsObject(Seq("type" -> JsString("ip"), "value" -> JsString(ip)))) 
      }
    case Endpoint(None, name, None) => {
      getIdByName(scenarioId, name) map {
        case Some(n) => Some(JsObject(Seq("type" -> JsString("id"), "value" -> JsString(n))))
        case None => None
        }
      }
  }
  
  private def getIdByName(scenarioId: String, name: String): Future[Option[String]] = {
    val url = s"$BaseUri/$scenarioId/objects?name="
    val response: Future[WSResponse] = get(url + name)
    
    val objOpt = response map { _.json.\("data").as[List[JsObject]] } map { _.headOption }
    objOpt flatMap { 
      case Some(obj) => Future { obj.\("id").asOpt[String] }
      case None => Future { None }
    }
    
  }
  
  private def getServiceMap(scenarioId: String): Future[Map[String, String]] = {
    val url = s"$BaseUri/$scenarioId/objects?limit=100&types=tcp_service,udp_service,icmp_service,service"
    
    val pagination = new Pagination(ws, ApiToken)
    val futureListPages = pagination.fetch(url)
    
    val x = for {
      listPages <- futureListPages
    } yield for {
      page <- listPages
    } yield toMap(page.data)
    
    x map { _.flatten.toMap }
  }
  
  private def toMap(services: List[JsObject]): Map[String, String] = {
    def name(srvType: String, param: JsLookupResult): String = {
      val value = param.as[String]
      if (value == null || value.equals("")) return srvType
      value + "/" + srvType
    }
    
    def categorize(srv: JsObject): String = {
      srv.\("type").as[String] match {
        case "tcp_service" => name("tcp", srv.\("destinationPorts"))
        case "udp_service" => name("udp", srv.\("destinationPorts"))
        case "icmp_service" => name("icmp", srv.\("typeCode"))
        case _ => name("ip", srv.\("protocol"))
      }
    }
    
    return Map(services map { s => (categorize(s), s.\("id").as[String])} : _*)
  }
  
  private def update(url: String, request: JsObject, response: WSResponse): Future[String] = {
      val msg = response.json.\("message").as[String]
      val Pattern = "Requirement already exists. Conflict with: (.*)".r
      
      def update(id: String) = {
        put(s"$url/$id", request + ("id", JsString(id))) map(r => r.status match {
          case 200 => "success"
          case _ => "error"
        })
      }
      
      msg match {
        case Pattern(id) => update(id)
        case _ => Future { "error" }
      }
  }
  
  private def toReq(reqs: List[RequirementApi]): List[Requirement] = {
    for {
      r <- reqs
    } yield toReq(r)
  }
  
  private def toReq(r: RequirementApi): Requirement = Requirement(
        Some(r.id), 
        r.source, 
        r.destination, 
        r.configuration.map(_.services).flatten,
        r.tags,
        r.action)
  
  private def get(url: String): Future[WSResponse] = {
    logger.debug(s"GET $url")
    request(url).get()
  }
  
  private def delete(url: String): Future[WSResponse] = {
    logger.debug(s"DELETE $url")
    request(url).delete()
  }
  
  private def post(url: String, requestBody: JsObject): Future[WSResponse] = {
    logger.debug(s"POST $url")
    request(url).post(requestBody)
  }
  
  private def put(url: String, requestBody: JsObject): Future[WSResponse] = {
    logger.debug(s"PUT $url")
    request(url).put(requestBody)
  }
  
  private def request(url: String): WSRequest = {
    ws.url(url).withHeaders("Authorization" -> s"Bearer $ApiToken")
  }
}