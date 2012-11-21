package ch.epfl.lsr.oneway

import ch.epfl.lsr.distal._
import ch.epfl.lsr.netty.network.ProtocolLocation
import collection.Set

import ch.epfl.lsr.performance.{ SimpleSummaryStats, ThreadMonitor }
import java.util.concurrent.TimeUnit._
import ch.epfl.lsr.util.execution.{ Timer => TimerImpl }

case class Payload(bytes :Array[Byte]) extends Message
case class Ack(count :Int) extends Message

class Server(val ID: String) extends DSLProtocol { 
  var bytes = 0
  var msgs = 0
  var x = 0

  def now = System.nanoTime

  UPON RECEIVING Payload DO { 
    p =>
      msgs = msgs + 1
      bytes = bytes + p.bytes.length
    | DISCARD p
    if(msgs % 10 == 0)
      | SEND Ack(10) TO SENDER
  }

  def doMeasurement() { 
	val b = bytes
	val m = msgs
	val t = now

	| AFTER 1(SECONDS) DO { 
	  val time :Double = (now - t)*1.0/(SECONDS.toNanos(1))
	  val mps = (msgs - m) / time
	  val bps = (bytes - b) / time
	  
	  x = x + 1
	  println("BW: %d time %.3f %.3f %.3f".format(x, time, mps, bps))
	}
  }

  
  UPON RECEIVING START DO { 
    s => 
      val bean = ThreadMonitor.getBean

      | AFTER 8(SECONDS) DO doMeasurement 
      | AFTER 10(SECONDS) DO doMeasurement 
      | AFTER 12(SECONDS) DO doMeasurement 
      | AFTER 15(SECONDS) DO doMeasurement 
      | AFTER 35(SECONDS) DO doMeasurement 

      | AFTER 55(SECONDS) DO doMeasurement 
    
      | AFTER 60(SECONDS) DO { 
	bean.printReport
	
	println("DONE")
	| AFTER 15(SECONDS) DO { 
	  System.exit(0)
	}
      }
      
      | DISCARD s
  }
}

class Client(val ID: String) extends DSLProtocol { 
  val payloadSize =  1350
  val source = Array.fill[Byte](payloadSize){ 1 }
  val SERVER = DSLProtocol.getAll(classOf[Server]).head

  var toSend = 10000
  
  def doSend { 
    while(toSend > 0) { 
      | SEND Payload(source) TO SERVER
      toSend = toSend - 1
    }
  }

  UPON RECEIVING Ack DO { 
    ack => 
      toSend = toSend + ack.count
      doSend
    //println("ack")
    | DISCARD ack
  }


  UPON RECEIVING START DO { 
    s=> 
    val bean = ThreadMonitor.getBean

    | AFTER 10(SECONDS) DO { doSend }
    | AFTER 60(SECONDS) DO { 
      bean.printReport
      System.exit(0)
    }

    | DISCARD s

  }

}
