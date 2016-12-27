package services

import play.api.libs.json._
import play.api.libs.json.Reads._ 
import play.api.libs.functional.syntax._
import play.api.libs.ws.ning._
import play.api.libs.ws._

/**
 * Model for requirements
 */
object RequirementModel {
  
  /**
   * Pair including existing requirements and the new ones
   */
  type RequirementResponse = (Iterable[IssuedRequirement], Iterable[Requirement])
  
  val Protocols = Map(1 -> "icmp", 6 -> "tcp", 17 -> "udp")
  
  // Puppet-Intelliment Model
  case class Network(id: String, name: String, ipAddress: Option[String])
  case class ItlmService(id: String)
  case class ItlmRequirement(name: String, action: String, services: List[ItlmService])
  
  implicit val networkReads: Reads[Network] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "name").read[String] and
    (JsPath \ "ip").readNullable[String])(Network.apply _)
  
  // Intelliment Model
  case class Issue(status: String, elementId: String, message: String)
  case class Endpoint(id: Option[String], name: String, ip: Option[String])
  case class Service(name: String, ports: List[String])
  
  object Service {
    
    /**
     * Transform a json from Intelliment API to Service case class
     */
    def build(service: JsObject): Service = {
      val name = service.\("name").as[String]
      val protocol = service.\("protocol").as[Int]
      val dstPorts = service.\("destinationPorts").asOpt[String]
      
      val srv = Protocols.get(protocol).getOrElse(protocol.toString())
      val srvPorts = if (dstPorts.isDefined) dstPorts.get + "/" + srv else srv
      
      Service(name, List(srvPorts))
    }
    
  }
  
  case class Status(kind: String, message: Option[String])
  case class IssuedRequirement(id: Option[String], status: Status, source: Endpoint, destination: Endpoint, services: List[Service], tags: List[String], action: String)
  case class ConfigurationEntry(enabled: Boolean, confType: Option[String], services: List[Service])
  case class RequirementApi(id: String, source: Endpoint, destination: Endpoint, configuration: List[ConfigurationEntry], tags: List[String], action: String)
  case class Requirement(id: Option[String], source: Endpoint, destination: Endpoint, services: List[Service], tags: List[String], action: String)
  
  implicit val serviceFormat = Json.format[Service]
  
  implicit val issueReads: Reads[Issue] = (
    (JsPath \ "status").read[String] and
    (JsPath \ "element" \\ "id").read[String] and
    (JsPath \ "message").read[String])(Issue.apply _)
  
  implicit val endpointReads: Reads[Endpoint] = (
      (JsPath \ "id").readNullable[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "ip").readNullable[String])(Endpoint.apply _)
        
  implicit val requirementReads: Reads[Requirement] = (
    (JsPath \ "id").readNullable[String] and
    (JsPath \ "source").read[Endpoint] and
    (JsPath \ "destination").read[Endpoint] and
    (JsPath \ "services").readNullable[List[JsObject]].map { 
      x => x.getOrElse(List.empty).map { s => Service.build(s) } } and
    (JsPath \ "tags").read[List[String]] and
    (JsPath \ "action").read[String])(Requirement.apply _)  
  
  implicit val configurationReads: Reads[ConfigurationEntry] = (
      (JsPath \ "enabled").read[Boolean] and
      (JsPath \ "type").readNullable[String] and
      (JsPath \ "services").readNullable[List[JsObject]].map { 
      x => x.getOrElse(List.empty).map { s => Service.build(s) } })(ConfigurationEntry.apply _)
    
  implicit val requirementApiReads: Reads[RequirementApi] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "source").read[Endpoint] and
    (JsPath \ "destination").read[Endpoint] and
    (JsPath \ "configuration").read[List[ConfigurationEntry]] and
    (JsPath \ "tags").readNullable[List[String]].map { 
      x => x.getOrElse(List.empty)} and
    (JsPath \ "action").read[String])(RequirementApi.apply _) 
    
  implicit val requirementFormat = new Writes[Requirement] {
    def writes(req: Requirement) = Json.obj(
      "id" -> req.id,
      "sourceName" -> req.source.name,
      "sourceIp" -> req.source.ip,
      "destinationName" -> req.destination.name,
      "destinationIp" -> req.destination.ip,
      "services" -> req.services,
      "tags" -> req.tags,
      "action" -> req.action)
  }
  
  implicit val issuedRequirementFormat = new Writes[IssuedRequirement] {
    def writes(req: IssuedRequirement) = Json.obj(
      "id" -> req.id,
      "status" -> req.status.kind,
      "message" -> req.status.message,
      "sourceName" -> req.source.name,
      "sourceIp" -> req.source.ip,
      "destinationName" -> req.destination.name,
      "destinationIp" -> req.destination.ip,
      "services" -> req.services,
      "tags" -> req.tags,
      "action" -> req.action)
  }
  
}