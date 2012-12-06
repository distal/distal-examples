package ch.epfl.lsr.common

import java.util.concurrent.TimeUnit._
import ch.epfl.lsr.distal.{ ImplicitDurations, Duration }

object ConstantProperties extends java.util.Properties { 
  try { 
    val url = new java.net.URL(java.lang.System.getProperty("lsr.constants.file"))
    load(url.openStream)
  } catch { 
    case t :Throwable =>
      println("lsr.constant.file not set? "+t)
  }
  def apply(key :String) = getProperty(key)
  def apply(key :String, default :String) = getProperty(key, default)

}


object CONSTANTS extends ImplicitDurations { 
  val Duration = 240(SECONDS)
  val Discard = 60
  val ClientCount = ConstantProperties("client.count", "90").toInt
  val ClientRequestPayload = ConstantProperties("payload.size", "20").toInt
  val WSZ = 10
  val BATCHTIMEOUT = 5(MILLISECONDS)
  val MAX_MSG_SIZE = 1350
  val BSZ = { 
    val ClientRequestSizeOverhead = 12 // or so (depending on string length, and seqno)
    1 max (MAX_MSG_SIZE/(ClientRequestSizeOverhead+ClientRequestPayload)) 
  }
  println("WSZ=%d BSZ=%d SZ=%d #cl=%d".format(WSZ, BSZ, ClientRequestPayload, ClientCount))
}
