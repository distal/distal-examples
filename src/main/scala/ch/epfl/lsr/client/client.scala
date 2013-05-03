package ch.epfl.lsr.client

import ch.epfl.lsr.distal._
import ch.epfl.lsr.protocol.ProtocolLocation
import ch.epfl.lsr.performance.SimpleSummaryStats
import ch.epfl.lsr.common.CONSTANTS
import ch.epfl.lsr.common.{ RequestID, NumberedRequestIDs }

case class ClientResponse(id :RequestID, value :Array[Byte]) extends Message
case class ClientRequest(id :RequestID,  value :Array[Byte]) extends Message

case class RequestReport() extends Message
case class Report(ID :String, report :String) extends Message

class ClientStats(ID :String) extends SimpleSummaryStats {
  val getIdentifier = ID
  val discardFor = CONSTANTS.Discard
  val collectFor = 10000 // report will be triggered "manually"
}

class Client(val ID :String, override val LOCATION :ProtocolLocation, val SZ :Int, initialLeader :ProtocolLocation) extends DSLProtocol with NumberedRequestIDs {
  val stats = new ClientStats(ID)

  val value = Array.fill[Byte](SZ) { 1 }
  var leader = initialLeader

  // println("Client "+ID+" starting with leader "+leader)

  UPON RECEIVING START DO {
    msg =>
      | SEND ClientRequest(nextReqId, value) TO leader

    | DISCARD msg
  }

  UPON RECEIVING ClientResponse DO {
    msg =>
      assert(isPrevId(msg.id), "response: "+msg.id+" local"+seqno+"ID "+ID)

    | SEND ClientRequest(nextReqId, value) TO leader
      stats.recordEvent(seqno)

    | DISCARD msg
  }

  UPON RECEIVING RequestReport DO {
    msg =>
      | SEND Report(ID, stats.report) TO SENDER
	shutdown
      | DISCARD msg
  }
}
