package services

import services.PuppetResourceModel._
import services.RequirementModel._
import javax.inject.Singleton
import javax.inject.Inject
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSRequest
import scala.concurrent.Future
import play.api.libs.ws.WSResponse
import scala.concurrent.ExecutionContext
import play.api.libs.json._
import play.api.Configuration
import java.math.BigInteger
import java.net.InetAddress
import play.api.Logger

/**
 * Services to interact with Intelliment
 */
trait RequirementService {
  
  /**
   * Return a pair with existing requirements and new requirements
   */
  def generateRequirements(scenarioId: String, resources: PuppetResources): Future[RequirementResponse]
  
  /**
   * Creates the requirements in Intelliment
   */
  def applyRequirements(scenarioId: String, requirements: List[Requirement]): Future[String]
  
  /**
   * Creates the requirements in Intelliment
   */
  def removeRequirements(scenarioId: String, requirements: List[String]): Future[String]
  
}

@Singleton
class RestRequirementService @Inject() (api: IntellimentApiClient) 
  (implicit exec: ExecutionContext) 
  extends RequirementService {
  
  val logger: Logger = Logger(this.getClass())
  
  val IpCidr = ("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.)" +
                   "{3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])" +
                   "(\\/(\\d|[1-2]\\d|3[0-2]))?$").r
  /**
   * Implementation with Intelliment API
   */
  override def generateRequirements(scenarioId: String, resources: PuppetResources): Future[RequirementResponse] = {
    
    logger.info(s"Generate requirements for scenario $scenarioId")
    
    val networks = api.getNetworks(scenarioId)
    val generated = for {
      nets <- networks
    } yield for {
      provider <- resources.providers
      consumer <- resources.consumers
      if(!provider.node.equals(consumer.node))
    } yield toRequirement(scenarioId, provider, consumer, nets)
    
    val generatedFlatten = generated.flatMap { l => Future.sequence(l) } map { _.flatten }
    
    // group in pairs of (requirement -> id (or empty string if new))
    val listOfPairs = for {
      gen <- generatedFlatten
      nets <- networks
    } yield for {
      req <- gen
    } yield existingAndNew(scenarioId, req, getInternetId(nets))
    
    val listOfFuture = listOfPairs.flatMap { x => Future.sequence(x) }
    /////
    
    Thread.sleep(5000)
    
    /////
    val issues = api.getIssues(scenarioId)
    val grouped = for {
      list <- listOfFuture
      issueMap <- issues
    } yield for {
      req <- list
    } yield (toIssuedReq(issueMap, req.existing), req.others)
    
    
    val existing = grouped.map(l => l.map(p => p._1)).map { i => i.flatten }
    val others = grouped.map(l => l.map(p => p._2)).map { i => i.flatten }
    
    for {
      e <- existing
      o <- others
    } yield (e.distinct, o.distinct)
    
  }
  
  /**
   * Return the internet id, it means the network without Ip address
   */
  private def getInternetId(networks: List[Network]): Option[String] = {
    val ids = for {
      net <- networks
      if net.ipAddress.isEmpty
    } yield net.id
    
    ids.headOption
  }
  
  private def toIssuedReq(issues: Map[String, List[Issue]], 
      reqs: List[Requirement]): List[IssuedRequirement] = {
    
    for {
      req <- reqs
    } yield IssuedRequirement(
        req.id,
        statusFor(issues.getOrElse(req.id.get, List.empty)), 
        req.source, 
        req.destination, 
        req.services,
        req.tags,
        req.action)
  }
  
  private def statusFor(issues: List[Issue]): Status = {
    val grouped = issues.groupBy { st => st.status }
    val errors = grouped.getOrElse("ERROR", List.empty)
    
    if (!errors.isEmpty) {
      val messages = errors.foldLeft("")((msg, st) => msg + st.message + ". ")
      Status("error", Some(messages))
    } else {
      Status("ok", Some("This requirement has no issues"))
    }
  }
  
  case class ExistingAndNew(existing: List[Requirement], others: List[Requirement])
  
  /**
   * Return the list of the requirements in Intelliment with the same source/destination
   */
  private def existingAndNew(scenarioId: String, requirement: Requirement, internetId: Option[String]): Future[ExistingAndNew] = {
    
    val source: Option[String] = requirement.source.ip match {
      case Some(ip) => Some("sourceIp=" + ip)
      case _ => if (internetId.isDefined) Some("sourceId=" + internetId.get) else None
    }
    
    val dest: Option[String] = requirement.destination.ip match {
      case Some(ip) => Some("destinationIp=" + ip)
      case _ => if (internetId.isDefined) Some("destinationId=" + internetId.get) else None
    }
    
    if (source.isDefined && dest.isDefined && !requirement.tags.isEmpty) {
      val sValue = source.get
      val dValue = dest.get
      val existing = api.getRequirements(scenarioId, Some(s"$sValue&$dValue"))
      
      /**
       * Check services, tags and actions
       */
      def sameParameters(req: Requirement, existing: List[Requirement]): Boolean = {
        val ports = req.services.flatMap { x => x.ports }
        val list = existing.filter { r => r.action.equals(req.action) && containsPorts(r, ports) }
        !list.isEmpty
      }
      
      def containsPorts(req: Requirement, ports: List[String]): Boolean = {
        !req.services.filter(s => !s.ports.intersect(ports).isEmpty).isEmpty
      }
      
//      existing map {
//        list => if (sameParameters(requirement, list)) ExistingAndNew(list, List.empty) else ExistingAndNew(list, List(requirement))
//      }
      
      existing map {
        list => ExistingAndNew(list, List(requirement))
      }
      
    } else {
      Future { ExistingAndNew(List.empty, List.empty) }
    }
  }
  
  /**
   * Implementation with Intelliment API
   */
  override def applyRequirements(scenarioId: String, requirements: List[Requirement]): Future[String] = {
    
    logger.info(s"Applying requirements for scenario $scenarioId")
    
    val requests = for {
      req <- requirements
    } yield api.createRequirement(scenarioId, req.source, req.destination, req.tags, req.services)
    
    val requestList = Future.sequence(requests)
    return requestList map {
      list => 
        if(list.contains("error")) 
          "Error! Application of requirements are fail"
        else 
          "Success! Requirements are successfully applied"
    }
  }
  
  /**
   * Implementation with Intelliment API
   */
  override def removeRequirements(scenarioId: String, requirements: List[String]): Future[String] = {
    
    logger.info(s"Removing requirements for scenario $scenarioId")
    logger.debug(s"Requirements to remove: $requirements")
    
    val requests = for {
      id <- requirements
    } yield api.removeRequirement(scenarioId, id)
    
    val requestList = Future.sequence(requests)
    return requestList map {
      list => 
        if(list.contains("error")) 
          "Error! Application of requirements are fail"
        else 
          "Success! Requirements are successfully applied"
    }
  }
  
  private def toRequirement(scenarioId: String, provider: Provider, consumer: Consumer, networks: List[Network]): 
    Future[List[Requirement]] =  {

    def isApp(tag: String) = tag.startsWith("app:") && !tag.startsWith("app::")
    
    val pNode = provider.node
    val pIp = provider.ipAddress
    val pTags = provider.tags
    val pService = provider.service
    val pPorts = provider.ports
    val cNode = consumer.node
    val cIp = consumer.ipAddress
    val cTags = consumer.tags
    val cService = consumer.service
    val cDest = consumer.dest
    val tags = (provider.tags.filter { isApp } ++ consumer.tags.filter { isApp }).distinct
    val action = provider.action // FIXME: action should be the same for consumer and provider

    def reqWithServer = Future {
      List(
        Requirement(
            None,
            Endpoint(None, cNode, Some(cIp)), 
            Endpoint(None,pNode, Some(pIp)), 
            List(Service(pService, pPorts)),
            tags,
            action
            ))
    }
    
    def getIpBitsAndMask(ip: String): (String, Int) = {
      val array = ip.split('/')
      val mask = if (array.size > 1) array(1).toInt else 32
      
      def ipToBits(a: List[String]): String = a match {
        case Nil => ""
        case head :: Nil => toBits(head)
        case head :: xs => toBits(head) + ipToBits(xs)
      }
      
      def toBits(part: String): String = {
        val bin = Integer.toBinaryString(Integer.parseInt(part))
        val length = 8 - bin.length()
        "0" * length ++ bin
      }
      
      val parts = array(0).split("\\.").toList
      (ipToBits(parts), mask)
    }
    
    def isInSameNetwork(netIp: Option[String], subnetIp: String) = netIp match {
      case None => true
      case Some(networkIp) => {
        val networkIpMask = getIpBitsAndMask(networkIp)
        val netBits = networkIpMask._1
        val netMask = networkIpMask._2
        val subnetIpMask = getIpBitsAndMask(subnetIp)
        val subBits = subnetIpMask._1
        val subMask = subnetIpMask._2
        
        // a subnet is contained in a network if: 
        // a) network mask is lower than subnet mask
        // AND
        // b) the prefix (from 0 to network mask) for network ip is equals to prefix for subnet ip
        (netMask < subMask) && (netBits.substring(0, netMask).equals(subBits.substring(0, netMask)))
      }
    }
   
    def requirementFromAll: Future[List[Requirement]] = {
      val req = for {
        net <- networks
        if !isInSameNetwork(net.ipAddress, pIp)
      } yield Requirement(
          None,
          Endpoint(Some(net.id), net.name, net.ipAddress), 
          Endpoint(None,pNode, Some(pIp)), 
          List(Service(pService, pPorts)),
          tags,
          action)
      
      Future { req }
    }
    
    def requirementFromNetwork(netName: String): Future[List[Requirement]] = {
      for {
        net <- api.getNetworkByName(scenarioId, netName)
      } yield net match {
        case None => List.empty
        case Some(n) => List(Requirement(
            None,
            Endpoint(Some(n.id), n.name, n.ipAddress), 
            Endpoint(None, pNode, Some(pIp)), 
            List(Service(pService, pPorts)),
            tags,
            action))
      } 
    }
    
    // Create requirements according to the source type
    val reqs = for {
      source <- provider.source
    } yield source match {
      case "all" => requirementFromAll
      case "all-consumers" if cService.contains(pService) && pTags.intersect(cDest).size > 0 => reqWithServer
      case IpCidr(ip) => Future { List(
          Requirement(
              None,
              Endpoint(None, ip, Some(ip)), 
              Endpoint(None,pNode, Some(pIp)), 
              List(Service(pService, pPorts)),
              tags,
              action)) }
      case role if cService.contains(pService) && cTags.contains(role) => reqWithServer
      case name => requirementFromNetwork(name)
    }
    
    Future.sequence(reqs).map { _.flatten }
  }
  
}
