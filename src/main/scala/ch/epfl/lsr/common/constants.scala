package ch.epfl.lsr.common

import java.util.concurrent.TimeUnit._
import ch.epfl.lsr.distal.{ ImplicitDurations, Duration }

object CONSTANTS extends ImplicitDurations { 
  val Duration = 240(SECONDS)
  val Discard = 60
  val ClientCount = 300
  val ClientRequestPayload = 20
  val WSZ = 10
  val BATCHTIMEOUT = 200(MILLISECONDS)
  val MAX_MSG_SIZE = 1350
  val BSZ = { 
    val ClientRequestSizeOverhead = 12 // or so (depending on string length, and seqno)
    1 max (MAX_MSG_SIZE/(ClientRequestSizeOverhead+ClientRequestPayload)) 
  }
  println("WSZ=%d BSZ=%d SZ=%d #cl=%d".format(WSZ, BSZ, ClientRequestPayload, ClientCount))
}
