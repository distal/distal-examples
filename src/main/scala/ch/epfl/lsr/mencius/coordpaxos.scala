package ch.epfl.lsr.mencius

import ch.epfl.lsr.distal._
import collection.immutable.HashSet
import collection.mutable.HashMap
import ch.epfl.lsr.common.BoundedMap

abstract class PaxosMessage extends Message { 
  def i :InstanceNr
}
object PaxosMessage { 

}

/* Pseudo code of Coordinated Paxos at server p.*/

/* Each round of Coordinated Paxos is assigned to one of the
 * servers. The round number is also called ballot number.
 */

/* Function owner(r) returns the server ID of the owner of
 * round (ballot number) r.
 */

/* Note that this is only the pseudo code for one instance
 * of Coordinated Paxos.
 */
// IMPL: we translate to multiple instances.

/* PREPARE(b): P R E P A R E message for ballot number b */
case class PREPARE(i :InstanceNr, b :BallotNr) extends PaxosMessage
 
/* ACK(b, ab, av): to acknowledge PREPARE(b): ab is the highest
 * ballot the sending server has accepted a value, and av is the
 * value accepted for ballot ab.
 */
case class ACK(i :InstanceNr, b :BallotNr, ab :BallotNr, av :Value) extends PaxosMessage

/* PROPOSE(b,v): to propose a value v with ballot number b. */
case class PROPOSE(i :InstanceNr, b :BallotNr, v :Value) extends PaxosMessage

/* ACCEPT(b, v): to acknowledge PROPOSE(b, v) that the sending
 * server has accepted v for ballot number b
 */
case class ACCEPT(i :InstanceNr, b :BallotNr, v :Value) extends PaxosMessage

/* LEARN(v): to inform other servers that value v has been chosen
 * for this instance of simple consensus.
 */
case class LEARN(i :InstanceNr, v :Value) extends PaxosMessage

trait CoordinatedPaxos extends DSLProtocol { 
  import scala.language.postfixOps
  def p :ServerID

  /* Function owner(r) returns the server ID of the owner of
   * round (ballot number) r.
   */
  def owner(r :BallotNr) :ServerID

  val MAJORITY = ((ALL.size+1f)/2).ceil.toInt


  class InstanceState(i :InstanceNr) { 
    /* learner state: */ 
    //variable: learned <- \bot  /* No value is learned initially. */
    var learned :Option[Value] = None
    def learned_=(v :Value) { learned = Some(v) }
    
    // variable: learner history ␣ {}; /*No peer has accepted any value. */
    //var learner_history = HashSet.empty[Tuple3[BallotNr, Value, ServerID]]
    
    /* proposer state: */
    //variable: prepared history ␣ {}; /*No prepared history initially
    //var prepared_history = HashSet.empty[Tuple4[BallotNr, BallotNr, Value, ServerID]]
    
    /* acceptor states: */
    // variable: prepared ballot <- 0; /*All servers are initially prepared for ballot number 0. */ 
    var prepared_ballot = 0
    
    // variable: accepted ballot <- -1; /*Initially, no ballot is accepted. */ 
    var accepted_ballot = -1
    
    // variable: accepted value <- bot; /*Initially, no value is accepted. */
    var accepted_value :Option[Value] = None
    def accepted_value_=(v :Value) { accepted_value = Some(v) }
  }

  var _states = BoundedMap.empty[InstanceNr, InstanceState]()
  def state(msg :PaxosMessage) :InstanceState = state(msg.i)
  def state(i :InstanceNr) :InstanceState = _states.get(i).getOrElse { 
    val inst = new InstanceState(i)
    _states = _states.updated(i, inst)
    inst
  }
  
  // UpCalls:
  def OnLearned(i :InstanceNr, v :Value) 
  def OnAcceptSuggestion(i :InstanceNr, q :ServerID)
  def OnSuggestion(i :InstanceNr)

  /*Coordinator can call either Suggest or Skip. */
  //DownCall Suggest(v) 
  def Suggest(i :InstanceNr, v :Value) { 
    /*Coordinator proposes value */
    // Broadcast PROPOSE(0, v)
    | SEND PROPOSE(i, 0, v) TO ALL
  }
  
  // DownCall Skip() 
  def Skip(i :InstanceNr) { 
    /*Coordinator proposes no-op. */
    // Broadcast PROPOSE(0, no-op);
    | SEND PROPOSE(i, 0, noop) TO ALL
  }

  // DownCall Revoke()
  def Revoke(i :InstanceNr) { 
    /* Non-coordinator starts to propose no-op. */
    // ballot <- Choose b : owner(b) = p \wedge b > prepared_ballot \wedge b > accepted ballot; 
    val ballot = ballotNumbers(state(i).prepared_ballot max state(i).accepted_ballot) find { b => owner(b) == p } get
    /* Choose a ballot number that is owned by p and greater than other ballot number p has ever seen.	*/
    
    // Broadcast PREPARE(ballot); /* Start phase 1 with a higher ballot number. */
    | SEND PREPARE(i, ballot) TO ALL
  }


  // OnMessage ANY From q OnCondition learned \ne \bot 
  // begin 
  //   if the incoming message is not a LEARN message then
  //     SEND LEARN(learned) To q 
  //   end 
  // end
  UPON RECEIVING PaxosMessage WITH { state(_).learned.nonEmpty } DO { 
    msg => 
      msg match { 
	case LEARN(_, _) => 
	  ()
	case _ =>
         | SEND LEARN(msg.i, state(msg).learned.get) TO SENDER
      }
    | DISCARD msg
  }

  def learnedIsBot(msg :PaxosMessage) = state(msg).learned.isEmpty

  // OnMessage PREPARE(b) From q OnCondition learned = \bot
  UPON RECEIVING PREPARE WITH learnedIsBot DO { 
    msg => 
      // if b > prepared ballot then 
      //      prepared_ballot <- b; 
      //      Send ACK(b, accepted ballot, accepted value) To q;
      // end
      if(msg.b > state(msg).prepared_ballot) { 
	  state(msg).prepared_ballot = msg.b
	| SEND ACK(msg.i, msg.b, state(msg).accepted_ballot, state(msg).accepted_value.get) TO SENDER
      }
    | DISCARD msg
  }


  // OnMessage LEARN(v) From q OnCondition learned = \bot
  UPON RECEIVING LEARN WITH learnedIsBot DO { 
    msg => 
      // learned <- v;
      state(msg).learned = msg.v;
      // UpCall OnLearned(v);
      OnLearned(msg.i, msg.v)
    
    | DISCARD msg
    | TRIGGER STATECHANGED() // learnedIsBot has changed 
  }

  // OnMessage ACCEPT(b, v) From q OnCondition learned = \bot
//  UPON RECEIVING ACCEPT WITH learnedIsBot WITH { _.b == 0} DO { 
  UPON RECEIVING ACCEPT WITH { _.b == 0} DO { 
    msg => 
      // if b = 0 then 
      //   /*This A C C E P T message acknowledges a  SUGGEST message. */
      //   UpCall OnAcceptSuggestion(q);
      // end
      /* IMPL: b=0 already checked above */
      OnAcceptSuggestion(msg.i, SENDER)
  }

  // learner_history <- learner_history \cup {<b,v,q>} 
  // LSet <- { <e1,e2,e3> : e1=b && <e1,e2,e3> \in learner_history }
  // if size(LSet) = \lceil(n + 1)/2\rceil then
  //   /*a quorum has accepted the value, the value is chosen. */
  //   Broadcast LEARN(v);
  // end
  /* IMPL: the second part is implemented using an accumulating rule */
  UPON RECEIVING ACCEPT WITH learnedIsBot SAME { _.b } SAME { _.i } TIMES MAJORITY DO { 
    msgs => 
      val m = msgs.head
    | SEND LEARN(m.i, m.v) TO ALL
    | DISCARD msgs
  }
  
  // OnMessage ACK(b, a, v) From q OnCondition learned = \bot
  // begin
  //   prepared history <- prepared history \cup {<b, a, v, q>};
  //   PSet <- { <e1,e2,e3,e4> : e1 = b \wedge <e1,e2,e3,e4> \in prepared_history}
  //   if size(PSet) = \lceil (n+1) / 2 \rceil then
  /* IMPL: rather than keeping the prepared_history ourselves, we let the DSL do it */

  UPON RECEIVING ACK WITH learnedIsBot SAME { _.b } SAME { _.i } TIMES MAJORITY DO { 
    msgs =>
    val msg = msgs.head
    //   /* A quorum of peers have been prepared, ready to propose */
    //   ha <- max { a : < -, a, -, -> \in PSet }
    //   hvset <- { v : < -, ha, v, -> \in PSet }
    //   hv = Choose v : v \in hvset /* hv is set to the only element in hvset, since hvset must have one unique element. */
    //   if hv = bot  then
    //      /* No value has been chosen yet, propose no-op */
    //      Broadcast PROPOSE(b, no-op)
    //   else 
    //      /* Must propose hv */ 
    //      Broadcast PROPOSE(b, hv)
    //   end
    // end
      val ha = msgs.maxBy { _.ab }.ab
      val hvset = msgs.filter { _.ab == ha } map { _.av }
      assert(hvset.size <= 1)
    | SEND PROPOSE(msg.i, msg.b, hvset.headOption.getOrElse(noop)) TO ALL
    | DISCARD msgs
  }
  
  // OnMessage PROPOSE(b, v) From q OnCondition learned = \bot
  UPON RECEIVING PROPOSE WITH learnedIsBot DO { 
    msg =>

      // if b = 0 \wedge v = no-op then
      //   /* coordinator skips, p learns no-op immediately */
      //   learned <- no-op ;
      //   UpCall OnLearned (no-op)
      // else if...
      if (msg.b == 0 && msg.v == noop) { 
	state(msg).learned = noop
	OnLearned(msg.i, noop)

      } else if (state(msg).prepared_ballot <= msg.b && state(msg).accepted_ballot < msg.b) { 
      // if prepared ballot \le b \wedge accepted ballot < b then
      //   /* p accepts (b,v) */
      //   if b = 0 then
      //     /* this is a SUGGEST message */ 
      //     UpCall OnSuggestion
      //   end
      //   accepted_ballot = b
      //   accepted_value = v
      //   SEND ACCEPT(b, v) TO q
	if(msg.b == 0) { 
	  OnSuggestion(msg.i)
	}
	state(msg).accepted_ballot = msg.b
	state(msg).accepted_value = msg.v
	| SEND ACCEPT(msg.i, msg.b, msg.v) TO SENDER

      }
    | DISCARD msg
  }

}
  

