package ch.epfl.lsr.ping

import ch.epfl.lsr.distal._
import ch.epfl.lsr.netty.network.ProtocolLocation
import collection.Set

import ch.epfl.lsr.performance.{ SimpleSummaryStats, ThreadMonitor }
import java.util.concurrent.TimeUnit._
import ch.epfl.lsr.util.execution.{ Timer => TimerImpl }

case class Ping(seq :Int) extends Message

class Server(val ID: String) extends DSLProtocol {

  UPON RECEIVING Ping DO {
    msg =>
    println("Ping@Server")
      | SEND msg TO SENDER
      | DISCARD msg
  }

  UPON RECEIVING START DO {
    s =>
      | DISCARD s
  }
}

class Client(val ID: String) extends DSLProtocol {
  var seq = 0
  val SERVER = DSLProtocol.getAll(classOf[Server]).head
  var t = 0l
  var sum = 0.0
  val skip = 10

  def now = System.nanoTime

  def delayedSend() {
    | AFTER 1(SECONDS) DO {
      t = now
      println("sending Ping@Client")
      | SEND Ping(seq) TO SERVER
    }
  }

  UPON RECEIVING START DO {
    s=>
    delayedSend()
    | AFTER 100(SECONDS) DO {
      println("PINGS: "+ (sum*1.0)/(seq-skip))
    }
    | DISCARD s
  }

  UPON RECEIVING Ping DO {
    ping =>
      seq = seq + 1
      delayedSend()

      if(seq >= skip) {
	println("PING: "+(now - t))
	sum = sum + (now - t)
      }

    | DISCARD ping
  }
}
