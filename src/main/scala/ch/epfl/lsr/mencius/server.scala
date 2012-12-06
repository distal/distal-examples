package ch.epfl.lsr.mencius

import ch.epfl.lsr.distal._
import ch.epfl.lsr.protocol.ProtocolLocation
import java.util.concurrent.TimeUnit._

import ch.epfl.lsr.server._

class MenciusServer(ID :String) extends RSM(ID) with OrderingServer { 
  val alpha :InstanceNr = 3
  val beta :InstanceNr  = 4
  val tau :Duration     = 20(MILLISECONDS)

  println("ALL: "+ALL.mkString)

  val clientHandler = DSLProtocol.locationForId(classOf[Server], ID)

  def OnCommit(v: Value) { 
    OnOrdered(v)
  }
    
  def requestOrdering(v :Value) { 
    OnClientRequest(v)
  }

}

class Server(val ID:String) extends ClientHandler(classOf[MenciusServer]) { 
//  val orderingService = DSLProtocol.locationForId(classOf[MenciusServer], ID)
}
