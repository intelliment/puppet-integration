package services

import play.api.libs.json.Reads
import play.api.libs.json.JsPath
import play.api.libs.functional.syntax._

/**
 * Model for service response
 */
object PuppetResourceModel {
  
  case class Provider(node: String, ipAddress: String, service: String, ports: List[String], source: List[String], tags: List[String], action: String)
  case class Consumer(node: String, ipAddress: String, service: List[String], dest: List[String], tags: List[String], action: String)
  case class PuppetResources(providers: List[Provider], consumers: List[Consumer])
  
   // Model for json response and ipAddress
  case class Resource(title: String, tags: List[String], node: String, resourceType: String, parameters: Parameters)
  case class Parameters(ports: Option[List[String]], service: Option[List[String]], source: Option[List[String]], destination: Option[List[String]], action: Option[String])
  case class IpAddress(node: String, ipAddress: String)
  
  implicit val parameterReads: Reads[Parameters] = (
    (JsPath \ "ports").readNullable[List[String]] and
    (JsPath \ "service").readNullable[List[String]] and
    (JsPath \ "source").readNullable[List[String]] and 
    (JsPath \ "destination").readNullable[List[String]] and 
    (JsPath \ "action").readNullable[String])(Parameters.apply _)
  
  implicit val resourceReads: Reads[Resource] = (
    (JsPath \ "title").read[String] and
    (JsPath \ "tags").read[List[String]] and
    (JsPath \ "certname").read[String] and
    (JsPath \ "type").read[String] and
    (JsPath \ "parameters").read[Parameters])(Resource.apply _)
  
  implicit val ipAddressReads: Reads[IpAddress] = (
    (JsPath \ "certname").read[String] and
    (JsPath \ "value").read[String])(IpAddress.apply _)
}