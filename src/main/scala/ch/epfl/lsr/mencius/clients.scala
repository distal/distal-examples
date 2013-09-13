package ch.epfl.lsr.mencius

import ch.epfl.lsr.distal._
import ch.epfl.lsr.protocol.{ ProtocolLocation => PL }
import ch.epfl.lsr.netty.network.ProtocolLocation
import collection.Set

import ch.epfl.lsr.performance.{ SimpleSummaryStats, ThreadMonitor }
import java.util.concurrent.TimeUnit._

import ch.epfl.lsr.common.Random.{ nextElement => randomElement }
import ch.epfl.lsr.client._
import ch.epfl.lsr.common.CONSTANTS
import ch.epfl.lsr.server._

class MultiLeaderClientStarter(ID :String) extends ClientStarter(ID) {
  val replicas  = DSLProtocol.getAll(classOf[Server]).toIndexedSeq
  val clients = (1 to count).map {
    i =>
      val id = (i*ALL.size+intID).toString
      assert(id.toInt > 0)
    new Client(id, LOCATION/i.toString, SZ, randomElement(replicas))
    //new Client(id, LOCATION/i.toString, SZ, replicas.head)
  }

}

class SingleLeaderClientStarter(ID :String) extends ClientStarter(ID) {
  val replicas  = DSLProtocol.getAll(classOf[Server]).toIndexedSeq
  val clients = (1 to count).map {
    i =>
      val id = (i*ALL.size+intID).toString
      assert(id.toInt > 0)
    //new Client(id, LOCATION/i.toString, SZ, randomElement(replicas))
    new Client(id, LOCATION/i.toString, SZ, replicas.head)
  }
}
abstract class ClientStarter(val ID :String) extends DSLProtocol {
  // TODO from config
  val count = CONSTANTS.ClientCount / ALL.size
  val SZ = CONSTANTS.ClientRequestPayload
  val replicas :IndexedSeq[PL]
  override def LOCATION = super.LOCATION.asInstanceOf[ProtocolLocation]
  val intID = ID.toInt

  val clients :Seq[Client]

  UPON RECEIVING START DO {
    m =>
//      val bean = ThreadMonitor.getBean

      | AFTER 7(SECONDS) DO {
        clients.foreach{ _.start }
        Thread.sleep(3000/count)
      }

      | AFTER CONSTANTS.Duration DO {
        //bean.printReport
        println ("requesting reports")
        | SEND RequestReport() TO clients
      }
      | DISCARD m
  }

  var reportsReceived = 0
  UPON RECEIVING Report DO {
    msg =>
    reportsReceived += 1
    //println("report #"+reportsReceived)
    println("\nSTATS: "+msg.report)

    | DISCARD msg

    if(reportsReceived == count) {
      println("exiting");
      | AFTER 1(SECONDS) DO {
        System exit 0
      }
    }
  }

  // UPON RECEIVING Report TIMES count DO {
  //   msgs =>
  //
  //    println(msgs.toSeq.sortBy(msg => msg.ID.toInt).map(_.report).mkString("\nSTATS: ", "\nSTATS: ", "\n"))
  //   System.out.flush()
  //   // | SEND EXIT() TO replicas
  //   | AFTER 1(SECONDS) DO {
  //     System exit 0
  //   }
  // }

}
