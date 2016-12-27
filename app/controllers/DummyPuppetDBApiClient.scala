package controllers

import play.api.libs.json.Json
import play.api.libs.json.Reads._ 
import play.api.libs.functional.syntax._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import services.PuppetDBApiClient
import services.PuppetResourceModel._
import play.api.libs.json.Reads

class DummyPuppetDBApiClient (implicit exec: ExecutionContext) extends PuppetDBApiClient {
  
  override def getIpAddresses(url: String): Future[List[IpAddress]] = {
    getFutureJson[List[IpAddress]]("/facts.json")
  }

  override def getResources(url: String): Future[List[Resource]] = {
    getFutureJson[List[Resource]]("/resources.json")
  }
  
  def getFutureJson[T](file: String)(implicit fjs: Reads[T]): Future[T] = {
    val json = getClass.getResourceAsStream(file)
    val x = Json.parse(json).as[T]
    Future { x }
  }
  
}