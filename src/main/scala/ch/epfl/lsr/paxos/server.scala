package ch.epfl.lsr.paxos

import ch.epfl.lsr.distal._
import ch.epfl.lsr.netty.protocol.ProtocolLocation
import collection.Set

import java.util.concurrent.TimeUnit._

case class PaxosRequest(cr: ClientRequest) extends Message

case class Ordered(pr :Seq[PaxosRequest]) extends Message

case class EXIT() extends Message

object CONSTANTS { 
  val ClientRequestPayload = 30
  val WSZ = 10
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

    println("SERVER created")

  val paxos = DSLProtocol.locationForId(classOf[PaxosServer], ID)
  //val executor = new Executor(ID+".exe", LOCATION+"/exec")
  
  val toPropose  = new collection.mutable.Queue[ClientRequest]()
  val toBeDecided  = new collection.mutable.HashSet[RequestID]()
  val clientLocations = new collection.mutable.HashMap[String,ProtocolLocation]()
  
  UPON RECEIVING ClientRequest DO { 
    msg =>
      //println("req "+msg.id+" 2bDecided:"+toBeDecided.size)
      clientLocations.update(msg.id.ID, SENDER)
      
      if(toBeDecided.size < CONSTANTS.WSZ)
	propose(msg)
      else 
	toPropose.enqueue(msg)
    
    | DISCARD msg
  }

  def propose(req :ClientRequest) = { 
    //println("proposing "+req.id+" "+(new java.util.Date))
      toBeDecided += req.id
    | SEND Request(PaxosRequest(req)) TO paxos
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
    val returnValue = pr.cr.value 
    toBeDecided -= pr.cr.id
    clientLocations.get(pr.cr.id.ID) map { 
      protoloc => 
	//println(pr.cr.id+" => "+protoloc+" "+(new java.util.Date))
	| SEND ClientResponse(pr.cr.id, returnValue) TO protoloc
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
