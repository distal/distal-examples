package ch.epfl.lsr.paxos

import ch.epfl.lsr.distal._
import ch.epfl.lsr.netty.protocol.ProtocolLocation
import collection.Set

import java.util.concurrent.TimeUnit._


case class PaxosRequest(id: RequestID, crs: Array[ClientRequest]) extends Message 

case class Ordered(pr :Seq[PaxosRequest]) extends Message

case class EXIT() extends Message

object CONSTANTS extends ImplicitDurations { 
  val ClientCount = 500
  val ClientRequestPayload = 30
  val WSZ = 10
  val BATCHTIMEOUT = 10(MILLISECONDS)
  val BSZ = { 
    val ClientRequestSizeOverhead = 20 // or so (depending on string length, and seqno)
    1500/(ClientRequestSizeOverhead+ClientRequestPayload)
  }
}


// PAXOS
class PaxosServer(ID:String) extends Legislator(ID, new MemoryLedger(1000), Dictator.ID) { 
  val server = DSLProtocol.locationForId(classOf[Server], ID)
  var executedUntil :Long = -1

  def decided(d :Decree) = {
    // println("decided "+d)
    val bound = ledger.haveAllWithLessThan 
    val prs :Seq[PaxosRequest] = { 
      (executedUntil + 1) to bound
    } map { 
      ledger.getDecision(_).get // shouldn't be empty
    } map { 
      _.v.asInstanceOf[PaxosRequest]
    }
      
    executedUntil = bound
    
    | SEND Ordered(prs) TO server
  }

  UPON RECEIVING START DO { 
    msg =>

      println("PAXOS started")

    | AFTER 180(SECONDS) DO { 
      System.exit(0)
    }

    | DISCARD msg
  }

  UPON RECEIVING EXIT DO { 
    msg => 
      println("AllLessThan "+ ledger.haveAllWithLessThan)
      System exit 0
  }
}

// CLIENT-HANDLER
class Server(val ID:String) extends DSLProtocol with NumberedRequestIDs { 
  self =>

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

	//println("BATCH of size "+count)
	version += 1
	buffer = Nil
	count = 0

	rv.toArray
      }
    }
    

    println("SERVER created")

  val paxos = DSLProtocol.locationForId(classOf[PaxosServer], ID)
  //val executor = new Executor(ID+".exe", LOCATION+"/exec")
  
  val toPropose  = new collection.mutable.Queue[Array[ClientRequest]]()
  val toBeDecided  = new collection.mutable.HashSet[RequestID]()
  val clientLocations = new collection.mutable.HashMap[String,ProtocolLocation]()
  val batcher = new Batcher
  
  UPON RECEIVING ClientRequest DO { 
    msg =>
    clientLocations.update(msg.id.ID, SENDER)

    batcher += msg
    
    if(batcher.isFull) { 
      if(toBeDecided.size < CONSTANTS.WSZ)
	propose(batcher.get)
      else 
	toPropose.enqueue(batcher.get)
    }
    
    | DISCARD msg
  }

  def propose(reqs :Array[ClientRequest]) = { 
    //println("proposing "+req.id+" "+(new java.util.Date))
    val pr = PaxosRequest(nextReqId, reqs)
    toBeDecided += pr.id
    | SEND Request(pr) TO paxos
  }

  UPON RECEIVING Ordered DO { 
    msg =>
      msg.pr.foreach { 
	if(toPropose.nonEmpty) {  // send new one
	  propose(toPropose.dequeue)
	}
	execute(_)
      }

    | DISCARD msg    
  }

  def execute(pr :PaxosRequest) = { 
    
    toBeDecided -= pr.id

    pr.crs foreach { 
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
      println("SERVER started")
    | DISCARD msg
  }

  UPON RECEIVING EXIT DO { 
    msg => 
      | SEND msg TO paxos
  }

}
