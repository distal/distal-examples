package ch.epfl.lsr.mencius

import ch.epfl.lsr.distal._
import ch.epfl.lsr.netty.protocol.ProtocolLocation
import collection.Set
import ch.epfl.lsr.performance.{ SimpleSummaryStats, ThreadMonitor }
import java.util.concurrent.TimeUnit._

import ch.epfl.lsr.common._
import ch.epfl.lsr.client._

case class MenciusValue(id: RequestID, crs: Array[ClientRequest]) extends Message 
case class Ordered(mv :MenciusValue) extends Message

class MenciusServer(ID :String) extends RSM(ID) { 
  val alpha :InstanceNr = 20
  val beta :InstanceNr  = 20
  val tau :Duration     = 5(MILLISECONDS)

  println("ALL: "+ALL.mkString)

  val server = DSLProtocol.locationForId(classOf[Server], ID)
  var executedUntil :Long = -1
  val stats = new SimpleSummaryStats { 
    val getIdentifier = ID
    val discardFor = CONSTANTS.Discard
    val collectFor = 10000 // report will be triggered "manually"
  }

  def OnCommit(v: Value) { 
    val mv = v.asInstanceOf[MenciusValue]

    | SEND Ordered(mv) TO server
    
    stats.recordEvent(mv.id.seqno.toInt)
  }

  UPON RECEIVING START DO { 
    msg =>

      println("MENCIUS started")

    //val bean = ThreadMonitor.getBean

    | AFTER CONSTANTS.Duration DO { 
      //bean.printReport
      println("STATS: "+stats.report)
      | AFTER 1(SECONDS) DO { 
	System exit 0
      }
    }

    | DISCARD msg
  }



  UPON RECEIVING MenciusValue DO { 
    mv =>
      OnClientRequest(mv)       
 
    | DISCARD mv
  }
}

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

	// println("BATCH of size "+count)
	version += 1
	buffer = Nil
	count = 0

	rv.toArray
      }
    }
    
  val mencius = DSLProtocol.locationForId(classOf[MenciusServer], ID)

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
    | SEND MenciusValue(nextReqId, reqs) TO mencius
  }

  UPON RECEIVING Ordered DO { 
    msg =>
      execute(msg.mv)

    | DISCARD msg    
  }

  def execute(mv :MenciusValue) = { 
    mv.crs foreach { 
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
}
