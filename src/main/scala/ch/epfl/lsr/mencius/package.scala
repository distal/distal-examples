package ch.epfl.lsr 

import ch.epfl.lsr.common.RequestID
import ch.epfl.lsr.server.RequestBatch

package object mencius { 
  type InstanceNr = Int
  type BallotNr = Int
  type Value = RequestBatch
  
  // object noop extends MenciusValue(RequestID("no-op",-1), Array[ClientRequest]())
  val noop :Value = null
  val bot = None

  // instance numbers larger than i
  def instanceNumbers(i :InstanceNr = -1) :Stream[InstanceNr] = 
    Stream.cons(i+1, instanceNumbers(i+1))
  
  def ballotNumbers(b :BallotNr = 0) :Stream[BallotNr] = 
    Stream.cons(b+1, ballotNumbers(b+1))
  
  
  type ServerID = String



}
