package ch.epfl.lsr.paxos

import ch.epfl.lsr.distal._
import ch.epfl.lsr.protocol.ProtocolLocation

import ch.epfl.lsr.server._

object PaxosServer { 
  val replicas = DSLProtocol.getAll(classOf[Server])
  val leader = replicas.head
}

// PAXOS
class PaxosServer(ID:String) extends Legislator(ID, new MemoryLedger(), DSLProtocol.idForLocation(PaxosServer.leader)) with OrderingServer
{ 
  val clientHandler = DSLProtocol.locationForId(classOf[Server], ID)

  def decided(d :Decree) = {
    OnOrdered(d.v)
  }
  
  def requestOrdering(v :RequestBatch) { 
    proposeRequest(v)
  }

}

class Server(val ID:String) extends ClientHandler { 
  val orderingService = DSLProtocol.locationForId(classOf[PaxosServer], ID)
}

