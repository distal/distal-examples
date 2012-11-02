package ch.epfl.lsr.paxos

import ch.epfl.lsr.distal._
import ch.epfl.lsr.protocol.ProtocolLocation
import collection.Set

import java.util.concurrent.TimeUnit._
import ch.epfl.lsr.performance._

object Dictator { 
  lazy val ID = "1"
} 

class DictatorBasedLegislator(ID :String) extends FasterLegislator(ID, new MemoryLedger(100), Dictator.ID) { 
  var lastRequest = -1

  val times = new Array[Long](1000)

  val STARTUPDELAY = 15
  val windowSZ = 10

  val stats = new SimpleSummaryStats { 
    val getIdentifier = ID
    val discardFor = 30
    val collectFor = 10000 // get result done on exit.
  }


  UPON RECEIVING START DO { 
    msg =>
      val bean = ThreadMonitor.getBean

      | AFTER STARTUPDELAY(SECONDS) DO { 
	startRuling
      }
      | AFTER 120(SECONDS) DO { 
	println("LastRequest = "+lastRequest)
	println("STATS: "+stats.report)
	println("AVG duration"+(times.sum/times.size))
	bean.printReport
	| AFTER 10(SECONDS) DO { 
	  System.exit(0)
	}
      }
      | DISCARD msg
  }

  val nextRuling = { 
    Array.fill[Byte](1300) { 1 }
  }

  def startRuling { 
    if(IamPresident) { 
      println(windowSZ)
      0 to windowSZ foreach { 
	i => 
	  proposeDecree(Decree(ledger.nextDecreeNr,nextRuling))
      }
    }
  }

  def decided(d :Decree) { 
    //println("decided: "+d.v._1)
    if(IamPresident) { 
      val num = ledger.nextDecreeNr

      proposeDecree(Decree(num,nextRuling))

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
