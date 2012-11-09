package ch.epfl.lsr.common

import java.util.concurrent.TimeUnit._
import ch.epfl.lsr.distal.{ ImplicitDurations, Duration }

object CONSTANTS extends ImplicitDurations { 
  val Duration = 240(SECONDS)
  val Discard = 60
  val ClientCount = 400
  val ClientRequestPayload = 20
  val WSZ = 10
  val BATCHTIMEOUT = 10(MILLISECONDS)
  val BSZ = { 
    val ClientRequestSizeOverhead = 20 // or so (depending on string length, and seqno)
    1 max (1300/(ClientRequestSizeOverhead+ClientRequestPayload)) 
  }
  println("WSZ=%d BSZ=%d SZ=%d #cl=%d".format(WSZ, BSZ, ClientRequestPayload, ClientCount))
}
