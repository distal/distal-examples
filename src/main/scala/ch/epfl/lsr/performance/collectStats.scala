package ch.epfl.lsr.performance

import ch.epfl.lsr.distal._
import ch.epfl.lsr.common.CONSTANTS
import java.util.concurrent.TimeUnit._

trait CollectStats extends DSLProtocol {
  def statsName = "commits"

  private val stats = new SimpleSummaryStats {
    val getIdentifier = ID
    val discardFor = CONSTANTS.Discard
    val collectFor = 10000 // report will be triggered "manually"
  }

  def recordEvent(seqNo :Int) {
    stats.recordEvent(seqNo)
  }

  UPON RECEIVING START DO {
    msg =>

      //val bean = ThreadMonitor.getBean

    | AFTER CONSTANTS.Duration DO {
      //bean.printReport
      println("\n"+statsName+"STATS: "+stats.report)
      | AFTER 10(SECONDS) DO {
        System exit 0
      }
    }

    | DISCARD msg
  }




}
