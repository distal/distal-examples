package ch.epfl.lsr 

package object paxos { 
  type BallotNr = Long
  type DecreeNr = Long

  type DecreeValue = ch.epfl.lsr.server.RequestBatch

  type Paper[T] = collection.mutable.Buffer[T]

  type LegislatorName = String

}


