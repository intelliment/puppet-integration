package services

import scala.concurrent.Future
import javax.inject.Inject
import play.api.libs.ws.WSClient
import scala.concurrent.ExecutionContext
import play.api.Logger
import play.api.libs.ws.WSResponse
import play.api.libs.json.JsObject
import play.api.libs.ws.WSRequest

class Pagination (ws: WSClient, token: String)
  (implicit exec: ExecutionContext) {
  
  case class Page(data: List[JsObject], next: Option[String])
  
  val logger: Logger = Logger(this.getClass())
  
  def fetchPage(url: String): Future[Page] = {
    val response: Future[WSResponse] = get(url)
    val futureData = response map { _.json.\("data").as[List[JsObject]] }
    val futureNext = response map { _.json.\("pagination").\("next").asOpt[String] }
    for {
      data <- futureData
      next <- futureNext
    } yield Page(data, next)
  }
  
  def fetch(urlPage: String): Future[List[Page]] = fetchAll(urlPage, List.empty)
  
  def fetchAll(urlPage: String, collected: List[Page]): Future[List[Page]] = {
    val a = for {
      page <- fetchPage(urlPage)
    } yield {
        page.next.fold {
           // No next page, return all collected up to this moment
          Future.successful(page :: collected)
        }{ nextPage =>
           // one more page to visit
          fetchAll(nextPage, page :: collected)
        }
    }
    // Future[Future[List[Page]]] => Future[List[Page]]]
    a.flatMap { x => x }
  }
  
  private def get(url: String): Future[WSResponse] = {
    logger.info(s"GET $url")
    ws.url(url).withHeaders("Authorization" -> s"Bearer $token").get()
  }

}