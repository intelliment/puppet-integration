package controllers

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import services.IntellimentApiClient
import services.RequirementModel.Issue
import services.RequirementModel.Network
import services.RequirementModel.Endpoint
import services.RequirementModel.Service
import scala.util.Random
import java.util.UUID
import services.RequirementModel.Requirement
import play.api.libs.json.Reads
import play.api.libs.json.Json
import play.api.libs.json.Reads._ 
import play.api.libs.functional.syntax._
import play.api.libs.json.JsObject

class DummyIntellimentApiClient (implicit exec: ExecutionContext) extends IntellimentApiClient {
  
  def id = UUID.randomUUID().toString()
  val networks = List(
        Network(id, "Admins",	Some("10.3.42.0/24")),
        Network(id, "Users",	Some("10.2.42.0/24")),
        Network(id, "Internet", None)
    )
  
  def getScenarios: Future[List[JsObject]] = ???  
    
  def getIssues(scenarioId: String): Future[Map[String, List[Issue]]] = Future { Map.empty }
  
  def getNetworks(scenarioId: String): Future[List[Network]] = Future { networks }
  
  def getNetworkByName(scenarioId: String, name: String): Future[Option[Network]] = {
    val network = networks.filter { net => net.name.equals(name) }.headOption
    Future { network }
  }
  
  def getRequirements(scenarioId: String, query: Option[String]): Future[List[Requirement]] =
    getFutureJson[List[Requirement]]("/requirements.json")
  
  def createRequirement(scenarioId: String, source: Endpoint, 
      destination: Endpoint, tags: List[String], services: List[Service]): Future[String] = Future { "ok" }
  
  def removeRequirement(scenarioId: String, id: String): Future[String] = Future { "ok" } 
  
  def getFutureJson[T](file: String)(implicit fjs: Reads[T]): Future[T] = {
    val json = getClass.getResourceAsStream(file)
    val x = Json.parse(json).as[T]
    Future { x }
  }
  
}