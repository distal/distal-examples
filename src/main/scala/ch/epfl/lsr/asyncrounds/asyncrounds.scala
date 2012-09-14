package ch.epfl.lsr.asyncrounds

import ch.epfl.lsr.distal._
import java.util.concurrent.TimeUnit.SECONDS

//import com.typesafe.config._

//import ch.epfl.lsr.netty.config._
//import ch.epfl.lsr.netty.network.Network
import ch.epfl.lsr.netty.protocol._

case class Round(i :Int, sender:String) extends Message


class AsyncRounds(id :String) extends DSLProtocol(id) { 
  var round = 0
  val n = ALL.size
  def f = 1
  
  UPON RECEIVING Round WITH { _.i == round } TIMES (n-f) DO { 
    msgs => 
      round += 1
      println("starting round "+round+" "+msgs.map{ _.sender })
    | SEND Round(round,id) TO ALL
  }

  UPON RECEIVING Round SAME { _.i } TIMES n DO { 
    msgs => 
      println("got all "+round+" "+msgs.map{ _.sender})

  }

  UPON RECEIVING START DO { s => 
    | AFTER 5(SECONDS) DO { 
      | SEND Round(round,id) TO ALL
    }
    | AFTER 20(SECONDS) DO { 
      println("Round "+round+" "+new java.util.Date)
      System exit 0
    }
  }

}

