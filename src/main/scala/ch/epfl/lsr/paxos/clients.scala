package ch.epfl.lsr.paxos

import ch.epfl.lsr.distal._
import ch.epfl.lsr.netty.protocol.ProtocolLocation
import collection.Set

import ch.epfl.lsr.performance.{ SimpleSummaryStats, ThreadMonitor }
import java.util.concurrent.TimeUnit._


case class RequestID(ID :String, seqno :Long)

case class ClientResponse(id :RequestID, value :Array[Byte]) extends Message 
case class ClientRequest(id :RequestID,  value :Array[Byte]) extends Message

case class RequestReport() extends Message
case class Report(ID :String, report :String) extends Message


trait NumberedRequestIDs { 
  def ID :String

  var seqno = -1

  private def nextSeqNo = { 
      seqno = seqno + 1
      seqno 
  }

  def nextReqId = RequestID(ID, nextSeqNo)
  def isPrevId(rid :RequestID) = { 
    (rid.ID == ID) && (rid.seqno == seqno)
  }
}

class ClientStats(ID :String) extends SimpleSummaryStats { 
  val getIdentifier = ID
  val discardFor = CONSTANTS.Discard
  val collectFor = 10000 // report will be triggered "manually"
}

class Client(val ID :String, override val LOCATION :ProtocolLocation, val SZ :Int) extends DSLProtocol with NumberedRequestIDs { 
  val stats = new ClientStats(ID)

  val value = Array.fill[Byte](SZ) { 1 }
  var leaderId = Dictator.ID // TODO leaderchange
  def leader = DSLProtocol.locationForId(classOf[Server], leaderId)
  
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

class ClientStarter(val ID :String) extends DSLProtocol { 
  // TODO from config
  val count = CONSTANTS.ClientCount
  val SZ = CONSTANTS.ClientRequestPayload
  val replicas = DSLProtocol.getAll(classOf[Server])
  

  val clients = (1 to count).map { 
    i => 
      new Client(ID+"."+i, LOCATION/i.toString, SZ)
  } 

  UPON RECEIVING START DO { 
    m => 
//      val bean = ThreadMonitor.getBean

      | AFTER 6(SECONDS) DO { 
	clients.foreach{ _.start }
      }
      | AFTER CONSTANTS.Duration DO { 

	//bean.printReport

	| SEND RequestReport() TO clients
      }
      | DISCARD m
  }

  UPON RECEIVING Report TIMES count DO { 
    msgs => 

      println(msgs.toSeq.sortBy(msg => (msg.ID.drop(2)).toInt).map(_.report).mkString("\nSTATS: "))

    // | SEND EXIT() TO replicas
    
    | AFTER 1(SECONDS) DO { 
      System exit 0
    }
  }

}
