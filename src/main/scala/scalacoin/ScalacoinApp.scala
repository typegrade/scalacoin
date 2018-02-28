package scalacoin

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import com.typesafe.config.ConfigFactory

import scalacoin.blockchain._
import scalacoin.blockchain.Blockchain.Implicits._
import scalacoin.network.BlockchainNodeActor
import scalacoin.network.BlockchainNodeActor.{GetBlockchain, GetLastBlock, GetPeers, MineBlock, CurrentBlockchain, LastBlock, ResolvePeer, Peers}

object ScalacoinApp extends FailFastCirceSupport {
  implicit val system: ActorSystem = ActorSystem("scalacoin-actor-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val exectutionContext: ExecutionContext = system.dispatcher

  implicit val timeout: Timeout = 5.seconds

  val blockchainNode = system.actorOf(BlockchainNodeActor.props, "blockchainNode")

  val config = ConfigFactory.load()
  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")

  def main(args: Array[String]) {
    val bindingFuture = Http().bindAndHandle(routes, interface, port)

    println(s"Scalacoin node is online at http://$interface:$port\nPress RETURN to stop...")
    StdIn.readLine()

    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }

  private def routes = {
    get {
      path("blockchain") {
        val chain: Future[CurrentBlockchain] = (blockchainNode ? GetBlockchain).mapTo[CurrentBlockchain]
        complete { chain }
      } ~
      path("lastBlock") {
        val block: Future[LastBlock] = (blockchainNode ? GetLastBlock).mapTo[LastBlock]
        complete { block }
      } ~
      path("peers") {
        val peers: Future[Peers] = (blockchainNode ? GetPeers).mapTo[Peers]
        complete { peers }
      }
    } ~
    post {
      path("mineBlock") {
        entity(as[String]) { data =>
          blockchainNode ! MineBlock(data)
          complete((StatusCodes.Created, "Block mined successfully."))
        }
      } ~
      path("addPeer") {
        entity(as[String]) { data =>
          blockchainNode ! ResolvePeer(data)
          complete((StatusCodes.Accepted, "Peer added successfully."))
        }
      }
    }
  }
}