package ch.epfl.lsr.mencius

import ch.epfl.lsr.distal._	

abstract class RSM(val ID :String) extends Mencius with CoordinatedPaxos { 
  val alpha :InstanceNr
  val beta :InstanceNr
  val tau :Duration

  def owner(b :BallotNr) :ServerID = ALL(b % ALL.size)
  
  val learned = new Learned { 
    def apply(i :InstanceNr) :Option[Value] = _states.get(i).flatMap{ _.learned } // using _states to avoid creation
    def update(i :InstanceNr, v :Value) :Unit = state(i).learned = v
  } 

  def OnCommit(v: Value) :Unit 
//  def OnClientRequest(v :Value) :Unit
}
