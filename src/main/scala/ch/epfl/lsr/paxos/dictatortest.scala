package ch.epfl.lsr.paxos

import ch.epfl.lsr.distal._
import ch.epfl.lsr.netty.protocol.ProtocolLocation
import collection.Set

import java.util.concurrent.TimeUnit._

object Dictator { 
  lazy val ID = "1"
} 

class DictatorBasedLegislator(ID :String) extends Legislator(ID, new MemoryLedger(100), Dictator.ID) { 
  var lastRequest = -1

  val times = new Array[Long](1000)

  val STARTUPDELAY = 15
  val windowSZ = 15

  val stats = new ch.epfl.lsr.performance.SimpleSummaryStats { 
    val getIdentifier = ID
    val discardFor = 30
    val collectFor = 1000 // get result done on exit.
  }


  UPON RECEIVING START DO { 
    msg =>
      | AFTER STARTUPDELAY(SECONDS) DO { 
	startRuling
      }
      | AFTER 120(SECONDS) DO { 
	println("LastRequest = "+lastRequest)
	println("STATS: "+stats.report)
	println("AVG duration"+(times.sum/times.size))
	| AFTER 10(SECONDS) DO { 
	  System.exit(0)
	}
      }
      | DISCARD msg
  }

  def nextRuling = { 
    lastRequest = lastRequest + 1
    (lastRequest.toString.getBytes, Some(LOCATION))
  }

  def startRuling { 
    if(IamPresident) { 
      println(windowSZ)
      0 to windowSZ foreach { 
	i => 
	  propose(Decree(ledger.nextDecreeNr,nextRuling))
      }
    }
  }

  def decided(d :Decree) { 
    //println("decided: "+d.v._1)
    if(IamPresident) { 
      val num = ledger.nextDecreeNr

      propose(Decree(num,nextRuling))

      if(num>=1000 && num<2000)
	times(num.toInt-1000) = System.nanoTime

    }
    val num = d.n.toInt
    stats.recordEvent(num)

    if(num>=1000 && num<2000) { 
      times(num.toInt-1000) = System.nanoTime - times(num-1000)
    }

  }

}
