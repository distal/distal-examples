package ch.epfl.lsr.paxos

import ch.epfl.lsr.distal._
import ch.epfl.lsr.protocol.ProtocolLocation

import ch.epfl.lsr.server._

object PaxosServer {
  val replicas = DSLProtocol.getAll(classOf[Server])
  val leader = replicas.head
}

object FasterServer {
  val replicas = DSLProtocol.getAll(classOf[FasterServer])
  val leader = replicas.head
}

object ServerWithAck {
  val replicas = DSLProtocol.getAll(classOf[ServerWithAck])
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

class Server(val ID:String) extends ClientHandler(classOf[PaxosServer])



// Faster
class FasterPaxosServer(ID:String) extends FasterLegislator(ID, new MemoryLedger(), DSLProtocol.idForLocation(FasterServer.leader)) with OrderingServer
{
  val clientHandler = DSLProtocol.locationForId(classOf[FasterServer], ID)

  def decided(d :Decree) = {
    OnOrdered(d.v)
  }

  def requestOrdering(v :RequestBatch) {
    proposeRequest(v)
  }

}

class FasterServer(val ID:String) extends ClientHandler(classOf[FasterPaxosServer])

// send Ack instead of full Accept
class FasterPaxosWithAckServer(ID :String) extends AckingLegislator(ID, new MemoryLedger(), DSLProtocol.idForLocation(ServerWithAck.leader)) with OrderingServer
{
  val clientHandler = DSLProtocol.locationForId(classOf[ServerWithAck], ID)

  def decided(d :Decree) = {
    OnOrdered(d.v)
  }

  def requestOrdering(v :RequestBatch) {
    proposeRequest(v)
  }
}


class ServerWithAck(val ID:String) extends ClientHandler(classOf[FasterPaxosWithAckServer])
