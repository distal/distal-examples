import ch.epfl.lsr.distal._

//import com.typesafe.config._

//import ch.epfl.lsr.netty.config._
//import ch.epfl.lsr.netty.network.Network
import ch.epfl.lsr.netty.protocol._

import java.util.concurrent.TimeUnit.SECONDS

import java.util.Date

case class Timeout(i :Int) extends Message
abstract class SeqMessage extends Message { 
  val i :Int
}
case class Foo(x :String) extends Message
case class Done(i :Int) extends SeqMessage
case class Print(i :Int, m :Any) extends SeqMessage
case class Drei() { 
  override def toString = "3"
}

class Container(seq :Seq[Any]) { 
  override def toString = ""+seq.length
}

/*

import ch.epfl.lsr.distal._
case class Done(i :Int) extends Message

class SendingProtocol extends DSLProtocol("sender") { 

UPON RECEIVING Done FROM "any" TIMES 1 WITH { _.i == 1} DO 

}

*/

/* ****************************************************** */
// Sender part
/* ****************************************************** */
class SendingProtocol extends DSLProtocol("sender") { 
  
  var interestingStuff :Seq[Any] = Seq(1, "2", Drei, new Container(List(1,2,3,4))) 
  var i = 0

  def doIt() { 
    if(interestingStuff.nonEmpty) { 
      Thread.sleep(100)
      println("sending "+interestingStuff.head)
      | SEND Print(i, interestingStuff.head) TO "printer"
      interestingStuff = interestingStuff.tail
      i = i + 1
    }
  }

  UPON RECEIVING Done FROM "printer" WITH { _.i == i} TIMES 1 DO { 
    ds => 
      println("Done "+ds.head.i) 
      doIt()
      | AFTER 2(SECONDS) DO { 
	| SEND Timeout(i) TO this
      }
  }

  UPON RECEIVING Timeout WITH { _.i == i } DO { 
    t => 
      this.shutdown
      println("done " + new Date)
      System exit 0
  }

  UPON RECEIVING Done FROM "sender" DO { 
    d => 
      if(i==0)
	doIt()
      else
	println("Ignoring own Done "+d.i) 
  }

  UPON RECEIVING START DO { s =>
    println("started "+ new Date)
    | SEND Foo("0") TO "sender" 
    | SEND Done(0) TO "sender"     
  }

  | AFTER 3(SECONDS) DO { 
    | SEND Timeout(0) TO this
  }
  
}


object SenderMain { 

  def main(args :Array[String]) = { 
    new SendingProtocol().start

    Thread sleep 5000
    println("done " + new Date)
    System exit 0
  }
}



/* ****************************************************** */
// Printer part
/* ****************************************************** */

class PrintingProtocol extends DSLProtocol("printer") { 
  var i = 0

  UPON RECEIVING Print WITH { _.i == i } WITH { _.m.toString.length > 0 } FROM "sender" DO { 
    m =>
      println("Printing:"+m.m)
      //println("sending "+Done(i)+" to "+sender)
      //Thread.sleep(100)
    i = i + 1
    | SEND Done(i) TO sender
  } 

  UPON RECEIVING START DO { 
    s => 
      println("started")
      | AFTER 15(SECONDS) DO { 
	println("done " + new Date)
	System exit 0
      }
  }
}


object PrinterMain { 

  def main(args :Array[String]) = { 
    Thread sleep 500

    new PrintingProtocol().start

    Thread sleep 5000
    println("done " + new Date)
    System exit 0
  }
}
