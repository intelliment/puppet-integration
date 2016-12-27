package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.ws.WS
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import akka.actor.ActorSystem
import services.PuppetResource
import services.PuppetResourceModel.Provider
import services.PuppetResourceModel.Consumer
import services.RequirementService
import scala.collection.mutable.ListBuffer
import services.RequirementModel.Requirement
import services.RequirementModel.RequirementResponse
import services.RequirementModel.Endpoint
import services.RequirementModel.Service
import services.IntellimentApiClient

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class AngularController @Inject() (puppetResource: PuppetResource, 
    requirementService: RequirementService, api: IntellimentApiClient, actorSystem: ActorSystem) 
    (implicit exec: ExecutionContext) 
    extends Controller {
   
  implicit val requirementResponseFormat = new Writes[RequirementResponse] {
    def writes(reqs: RequirementResponse) = Json.obj(
      "existingRequirements" -> reqs._1,
      "newRequirements" -> reqs._2)
  }

  /**
   * Return the index page
   */
  def index = Action {
    Ok(views.html.index("Intelliment - Puppet : Proof of Concept"))
  }
  
  /**
   * Return the scenario list
   */
  def getScenarios = Action.async {
    val scenarios = api.getScenarios
    scenarios map { scenario => Ok(Json.toJson(scenario)) }
  }
  
  /**
   * Return the existing and new requirements
   */
  def requirements(id: String, url: String) = Action.async {
    val requirements = generateReqs(id, url)
    requirements map { resp => Ok(Json.toJson(resp)) }
  }
  
  /**
   * Create the requirements 
   */
  def apply(id: String, url: String) = Action.async { request =>
    val json = request.body.asJson.map { x => toRequirementList(x.as[List[JsValue]]) }
    val created = requirementService.applyRequirements(id, json.getOrElse(List.empty))
    
    val x = created.map( x => {
        generateReqs(id, url).map{ resp => Ok(Json.toJson(resp)) }
      }
    )
    
    x.flatMap { i => i }
    
  }
  
  /**
   * Remove the requirements 
   */
  def remove(id: String, url: String) = Action.async { request =>
    val list = request.body.asJson.map{ x => x.as[List[String]] }
    val deleted = list.map(l => requirementService.removeRequirements(id, l))
    
    val result = deleted.map( s => generateReqs(id, url).map{ resp => Ok(Json.toJson(resp)) } )
    
    result match {
      case Some(x) => x
      case _ => Future { Ok("error") }
    }
  }
  
  private def generateReqs(id: String, url: String): Future[RequirementResponse] = {
    val resources = puppetResource.getPuppetResources(url)
    resources flatMap {
      res => requirementService.generateRequirements(id, res)
    }
  }
  
  private def toRequirementList(list: List[JsValue]): List[Requirement] = {
    for {
      el <- list
    } yield Requirement(
        None,
        Endpoint(None, el.\("sourceName").as[String], el.\("sourceIp").asOpt[String]), 
        Endpoint(None, el.\("destinationName").as[String], el.\("destinationIp").asOpt[String]),
        el.\("services").as[List[Service]],
        el.\("tags").as[List[String]],
        el.\("action").as[String]
        )
  }
  
}
