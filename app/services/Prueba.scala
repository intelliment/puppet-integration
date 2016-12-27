package services

import play.api.libs.json._

object Prueba {
  def main(args: Array[String]): Unit = {
    val json = JsObject(Seq(
      "description" -> JsString("Created from Puppet"),
      "source" -> JsString("xxxxx"),
      "destination" -> JsString("yyyyy"),
      "action" -> JsString("allow")))

    println(json)
    println(json + ("id", JsString("lskajflskñadsdlkñfjsdlkñfs")) )
  }
}