package ch.epfl.lsr.server


import ch.epfl.lsr.distal._
import ch.epfl.lsr.protocol.ProtocolLocation
import collection.Set
import ch.epfl.lsr.performance.{ SimpleSummaryStats, ThreadMonitor }
import java.util.concurrent.TimeUnit._

import ch.epfl.lsr.client._
import ch.epfl.lsr.common._

case class RequestBatch(id: RequestID, crs: Any) extends Message 
case class OrderingRequest(v :RequestBatch) extends Message
case class Ordered(v :RequestBatch) extends Message


trait OrderingServer extends DSLProtocol { 

  val clientHandler :ProtocolLocation

  var executedUntil :Long = -1
  val stats = new SimpleSummaryStats { 
    val getIdentifier = ID
    val discardFor = CONSTANTS.Discard
    val collectFor = 10000 // report will be triggered "manually"
  }
  
  val toBeDecided   = new collection.mutable.HashSet[RequestID]()
  val toBeProposed  = new collection.mutable.Queue[RequestBatch]()

  def requestOrdering(v :RequestBatch)
  
  def OnOrdered(mv: RequestBatch) { 

    toBeDecided -= mv.id

    if(toBeProposed.nonEmpty) {  // propose next one.
      requestOrdering(toBeProposed.dequeue)
    }
    
    | SEND Ordered(mv) TO clientHandler
    
    stats.recordEvent(mv.id.seqno.toInt)
  }


  UPON RECEIVING START DO { 
    msg =>

      println("ORDERING SERVER started")

    val bean = ThreadMonitor.getBean

    | AFTER CONSTANTS.Duration DO { 
      bean.printReport
      println("\nSTATS(commmits): "+stats.report)
      | AFTER 1(SECONDS) DO { 
	System exit 0
      }
    }

    | DISCARD msg
  }


  UPON RECEIVING OrderingRequest DO { 
    or =>
      
      if(toBeDecided.size < (CONSTANTS.WSZ)) { 
	toBeDecided += or.v.id
	requestOrdering(or.v)       
      } else { 
	toBeProposed += or.v
      }

    | DISCARD or
  }
}


trait ClientHandler extends DSLProtocol with NumberedRequestIDs { 
  self =>

    val orderingService :ProtocolLocation


    class Batcher { 
      var buffer :List[ClientRequest] = Nil
      var count :Int = 0
      var version = 0

      def +=(r :ClientRequest) { 
	count += 1
	buffer = r :: buffer
	if(count == 1) { 
	  val current = version // closure over current value of version
	  | AFTER CONSTANTS.BATCHTIMEOUT DO { 
	    try { 
	      propose(this.verifyAndGet(current))
	    } catch { 
	      case t :Throwable => ()
	    }
	  }
	}
      }

      def isFull :Boolean = { 
	count >= CONSTANTS.BSZ
      }

      def verifyAndGet(assumedVersion :Int = -1) :Array[ClientRequest] = {
	// AFTER code above will not propose if proposed in the meantime.
	assume(assumedVersion == -1 || version == assumedVersion) 
	
	get
      }

      def get = { 
	val rv = buffer

	// println("BATCH of size "+count)
	version += 1
	buffer = Nil
	count = 0

	rv.toArray
      }
    }
    
  val clientLocations = new collection.mutable.HashMap[String,ProtocolLocation]()
  val batcher = new Batcher
  
  UPON RECEIVING ClientRequest DO { 
    msg =>
    clientLocations.update(msg.id.ID, SENDER)

    batcher += msg
    
    if(batcher.isFull) { 
      propose(batcher.get)
    }
    
    | DISCARD msg
  }

  def propose(reqs :Array[ClientRequest]) = { 
    val pr = RequestBatch(nextReqId, reqs)
    //println("propose "+pr.id)
    | SEND OrderingRequest(pr) TO orderingService
  }

  UPON RECEIVING Ordered DO { 
    msg =>
      execute(msg.v)
  
    | DISCARD msg    
  }

  def execute(pr :RequestBatch) = { 
    //println("execute "+pr.id)
    
    pr.crs.asInstanceOf[Array[ClientRequest]] foreach { 
      cr => 
	val returnValue = cr.value 	// TODO do something more usefull with value
	clientLocations.get(cr.id.ID) foreach { 
	  protoloc => 
	    | SEND ClientResponse(cr.id, returnValue) TO protoloc
	}
    }
  }

  UPON RECEIVING START DO { 
    msg =>
      //println("CLIENT HANDLER started")
    | DISCARD msg
  }

//  UPON RECEIVING EXIT DO { 
//    msg => 
//      | SEND msg TO paxos
//  }

}


