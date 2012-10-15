package ch.epfl.lsr 

import ch.epfl.lsr.netty.protocol.ProtocolLocation

package object paxos { 
  type BallotNr = Long
  type DecreeNr = Long

  type DecreeValue = AnyRef

  type Paper[T] = collection.mutable.Buffer[T]

  type LegislatorName = String

}


