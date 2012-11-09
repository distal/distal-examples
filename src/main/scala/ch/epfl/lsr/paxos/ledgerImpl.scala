package ch.epfl.lsr.paxos

import ch.epfl.lsr.distal._
import ch.epfl.lsr.protocol.ProtocolLocation
import collection.Set
import collection.immutable.TreeMap
import ch.epfl.lsr.common.BoundedMap

class MemoryLedger() extends Ledger { 
  var haveAllWithLessThan :DecreeNr = 0
  
  var previousDecisions = BoundedMap.empty[DecreeNr,Decree]()
  
  def missingUpTo(n :DecreeNr) :Set[DecreeNr] = 
    (haveAllWithLessThan to n).filterNot(previousDecisions.contains).toSet

  def alreadyInLedgerGreaterThan(n :DecreeNr) :Set[Decree] = 
    previousDecisions.filterKeys(_ > n).values.toSet
  
  def note(decrees :Traversable[Decree]) { 
    decrees.foreach(note)
  }

  def note(decree :Decree) { 
    previousDecisions = previousDecisions.updated(decree.n, decree)
    if(decree.n == haveAllWithLessThan + 1) incHaveAll
  }


  def incHaveAll { 
    while(previousDecisions.contains(haveAllWithLessThan+1))
      haveAllWithLessThan = haveAllWithLessThan + 1
  }

  def getDecision(n :DecreeNr) : Option[Decree] = 
    previousDecisions.get(n)

  def alreadyDecided(n :DecreeNr) :Boolean = { 
    (n < haveAllWithLessThan) || previousDecisions.get(n).nonEmpty
  }
}
