package ch.epfl.lsr.asyncrounds

import ch.epfl.lsr.distal._
import java.util.concurrent.TimeUnit.SECONDS

import ch.epfl.lsr.performance._

import ch.epfl.lsr.netty.protocol._

import scala.collection.Set

case class Round(i :Int, sender:String, previousHO :Seq[String]) extends Message



// trait AbstractRoundsWithStats { 
//   val stats = new SimpleSummaryStats { 
//     def getIdentifier = ID
//     val discardFor = 60
//     val collectFor = 1800
//   }
//   private 
//   private var _round = 0
//   def round = _round
//   def round_=(r :Int) { 
//     _round = r
//     ho.log(ID, _round, _current)
//     _current = mutable.Set.empty
//   }
//   UPON RECEIVING Round WITH { _.i == round } DO { 
//     msg =>
//       _current += ...
//   }
// }

class AsyncRounds(val ID :String) extends DSLProtocol { 
  val stats = new SimpleSummaryStats { 
    def getIdentifier = ID
    val discardFor = 60
    val collectFor = 1800
  }
  var round = 0
  val n = ALL.size
  def f = n-((n/2)+1)
  println("n="+n+" f="+f+" ")

  val ho = new HOLogger(ID)
  //val allho = new AllHOLogger(ID, ALL.map(DSLProtocol.idForLocation(_)).toSet, f)

  // UPON RECEIVING Round WITH { _.i == round } DO { 
  //   msg =>
  //     println(" ROUNDMSG: "+ msg + " (round="+round+")")
  // }

  //val msgMap :collection.mutable.Map[Int,Round] = collection.mutable.Map.empty
  
  UPON RECEIVING Round WITH { _.i == round } TIMES (n-f-1) DO { 
    msgs => 
      round += 1
    | SEND Round(round,ID, msgs.map{ _.sender} ++ Seq(ID)) TO ALL_REMOTE
      stats.recordEvent(round)
      ho.log(ID, round, msgs.map{ _.sender } ++ Seq(ID))
    
    | DISCARD msgs
    | TRIGGER STATECHANGED()
  }

  // UPON RECEIVING Round WITH { _.i > round } DO { 
  //   msg =>
  //     println("ALERT "+ msg.i +" > "+round)
  // }

  // UPON RECEIVING Round SAME { _.i } TIMES n-1 DO { 
  //   msgs => 
  //     val round = msgs.head.i
  //   | DISCARD msgs
  //     msgMap.get(round) match { 
  // 	case Some(ownMessage) =>
  // 	  allho.log(msgs ++ Seq(ownMessage))
  // 	  msgMap -= round
  // 	case _ => ()
  //     }
  // }

  UPON RECEIVING Round WITH { _.i < round} DO { 
    msg =>
      | DISCARD msg
  }

  UPON RECEIVING START DO { 
    s => 
      println("START!")
      | DISCARD s
      | AFTER 1(SECONDS) DO { 
	| SEND Round(round,ID,null) TO ALL_REMOTE
	}
      | AFTER 120(SECONDS) DO { 
	ho.shutdown
	//allho.shutdown
	println("Round "+round+" "+new java.util.Date)
	println("STATS:"+stats.report)
	System exit 0
      }
  }

}

