package ch.epfl.lsr.paxos

import ch.epfl.lsr.distal._
import ch.epfl.lsr.netty.network.ProtocolLocation
import collection.Set

import ch.epfl.lsr.performance.{ SimpleSummaryStats, ThreadMonitor }
import java.util.concurrent.TimeUnit._

import ch.epfl.lsr.common.CONSTANTS
import ch.epfl.lsr.client._

class ClientStarter(val ID :String) extends DSLProtocol { 

  val count = CONSTANTS.ClientCount
  val SZ = CONSTANTS.ClientRequestPayload
  override def LOCATION = super.LOCATION.asInstanceOf[ProtocolLocation]  
  val intID = ID.toInt
  
  val clients = (1 to count).map { 
    i => 
      new Client((i*ALL.size+intID).toString, LOCATION/i.toString, SZ, PaxosServer.leader)
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

      println(msgs.toSeq.sortBy(msg => msg.ID.toInt).map(_.report).mkString("\nSTATS: "))

    // | SEND EXIT() TO replicas
    
    | AFTER 1(SECONDS) DO { 
      System exit 0
    }
  }

}
