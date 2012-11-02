package ch.epfl.lsr.mencius

import ch.epfl.lsr.distal._
import ch.epfl.lsr.protocol.ProtocolLocation
import collection.mutable.HashMap
import collection.immutable.Set

import ch.epfl.lsr.common._
import ch.epfl.lsr.performance._

trait Learned { 
  def apply(i :InstanceNr) :Option[Value]
  def update(i :InstanceNr, v :Value) :Unit
}

/*Pseudo code of Mencius at server p.	*/

/*
 * Mencius runs a series of Coordinated Paxos. It commits a value
 * learned in instance i once all instances smaller than i have been
 * learned and committed.  The following code handles duplication by
 * checking for duplications before committing.  Other techniques, such
 * as assuming idempotent requests, can also be used.
 * Mencius provides two APIs to its applications.  The applications downcalls
 * OnClientRequest to submit a request to the state machine.  When a
 *  value is chosen, Mencius upcalls OnCommit to notify the application.
 */

/* Note that besides the original arguments, all upcalls/downcalls
 * from/to Coordinated Paxos also have an additional argument i that
 * specifies the simple consensus instance number. 
 */ 

trait Mencius extends DSLProtocol with MenciusTimers { 
  val p :ServerID = ID
  def ALL_IDs :Array[ServerID] = ALL.map(DSLProtocol.idForLocation)
  import language.postfixOps

  val instancestats = new SimpleSummaryStats { 
    val getIdentifier = ID
    val discardFor = CONSTANTS.Discard
    val collectFor = 10000 // report will be triggered "manually"
  }

  UPON RECEIVING START DO { 
    msg =>
      | AFTER CONSTANTS.Duration DO { 
	println("STATS(instances): "+ instancestats.report)
      }
    | DISCARD msg
  }

  // UpCalls
  def OnCommit(v :Value) :Unit

  // DownCalls
  def Skip(q :InstanceNr) :Unit
  def Revoke(q :InstanceNr) :Unit
  def Suggest(q :InstanceNr, v :Value) :Unit

  // parameters
  def alpha :InstanceNr
  def beta :InstanceNr
  def tau :Duration

  /* Function owner(i) returns the coordinator of instance i */
  def owner(i :InstanceNr) :ServerID

  /* Function learned(i) returns a reference to the learned
   * variable of the ith consensus instance
   */
  val learned :Learned

  /* Mencius also uses n timers for Accelerator 1.*/

  // variable: proposed[ ]; 
  /* An array records the value that the coordinator initially suggested
   * to an instance. It maps an instance number to a value. Every key is
   * initially mapped to \bot.  
   */ 
  val proposed = HashMap.empty[Int, Value]

  // variable: expected \leftarrow 0; 
  /*The next instance number to commit a value, i.e., the smallest instance whose
   * value is not learned.
   */
  var expected :InstanceNr = 0
  
  // variable: index \leftarrow min{i : owner(i) = p}; 
  /* The next instance to suggest a value to.	*/
  var index :InstanceNr = instanceNumbers().find { i => owner(i) == p } get

  // variable: est_index[ ]; 
  /* An array records the estimated index of other servers.
   * It maps a server ID to an instance number.
   * Initially, est index[q] ← min{i : owner(i) = q}.
   */ 
  val est_index = new HashMap[ServerID, InstanceNr]{ 
    override def default(q :ServerID) = instanceNumbers().find { i => owner(i) == p} get
  }
    
  // variable: need to skip[ ]; 
  /* An array records the set of outstanding S K I P messages need
   * to be sent to a server. It maps a server ID to a set of instance
   * numbers. Every key is initially mapped to an empty set.
   */
  var need_to_skip = new HashMap[ServerID,Set[InstanceNr]] { 
    override def default(q :ServerID) = Set.empty[InstanceNr]
  }
  
  // DownCall OnClientRequest(v)
  def OnClientRequest(v :Value) { 
    instancestats.recordEvent(expected)

    println("OnClientRequest:"+v.id)

    /* By Optimization 2, S K I P messages piggybacked on S U G G E S T
     * messages. cancel the timers that were previously set for
     * Accelerator 1 and reset the records of the outstanding SKIPs.
     */
    ALL.foreach { q =>
      CancelTimer(q) /* Cancel the qth timer. */ 
      // need to skip[q] ← {}
      need_to_skip.remove(q) // IMPL: because emptyset is the default
    }

    // DownCall Suggest(index, v); 
    Suggest(index, v)
    /* Rule 1: p suggests v to instance index */
    // proposed[index] \leftarrow v
    proposed(index) = v
    // index \leftarrow min{i : owner(i)=p \wedge i > index 
    index = instanceNumbers(index) find { i => (owner(i) == p) } get
  }

  //UpCall OnAcceptSuggestion(i, q) 
  def OnAcceptSuggestion(i :InstanceNr, q :ServerID) { 
    /*Upon receiving an A C C E P T message that acknowledges
     * a previous SUGGEST message.
     */
    // begin
    
    // TODO
    // QSkipSet ← {j : est_index[q] ≤ j < i ∧ owner(j) = q}; 
    val QSkipSet = est_index(q) until i filter { j => owner(j) == q }
    /* By Optimization 1: S K I P messages are piggybacked on this
     * A C C E P T message. QSkipSet is the set of instances q
     * has skipped.
     */

    // forall j ∈ QSkipSet do 
    //     learned(j) ← no-op; 
    //     Call CheckCommit;
    // end
    for (j <- QSkipSet) { 
//      println("noop by optimization 1: "+j+" i="+i+" est="+est_index(q))
      learned(j) = noop
      CheckCommit
    }
    
    // est_index[q] ← min{j : j > i ∧ owner(j) = q};
    // BUG !? in pseudo-code: if est_index > i, resets est_index to already used index
    // est_index(q) = instanceNumbers(i) find { j => owner(j) == q} get
    est_index(q) = est_index(q) max (instanceNumbers(i) find { j => owner(j) == q } get)
  }

  // UpCall OnSuggestion(i)	
  def OnSuggestion(i :InstanceNr) { 
    /*Upon receiving SUGGEST message for instance i. */
    // begin 
    // q ← owner(i); /*q is the sender of the SUGGEST message. */
    val q = owner(i)

    // QSkipSet ← {j : est index[q] ≤ j < i ∧ owner(j) = q};	
    val QSkipSet = est_index(q) until i filter { j => owner(j) == q }
    /* By Optimization 2, S K I P messages are piggybacked on this
     * S U G G E S T message. QSkipSet is the set of instances q has skipped.
     */

    // forall j ∈ QSkipSet do 
    //     learned(j) ← no-op; 
    // end
    for (j <- QSkipSet) { 
//      println("noop by optimization 2: "+j)
      learned(j) = noop
    }

    // Call CheckCommit
    CheckCommit

    // est_index[q] ← min{j : j > i ∧ owner(j) = q};
    est_index(q) = instanceNumbers(i) find { j => owner(j) == q} get
 
    // SkipSet ← {j : j ≥ index ∧ j < i∧owner(j) = p}; 
    val SkipSet = index until i filter { j => owner(j) == p }
    /* By Rule 2, server p skips all unused instances smaller than i.
     * SkipSet is the set of instances p needs to skip.
     */

    // forall k ∈ SkipSet do 
    //   learned[k] ← no-op;
    // end 
    // Call CheckCommit;
    for (k <- SkipSet) { 
//      println("noop by rule 2: "+k)
      learned(k) = noop
    }
    CheckCommit

    /* p does not send servers immediately.
     * Optimization 1: p piggyback the S K I P message to q on
     * the ACCEPT message.
     */
    // forall k∈{r: 0≤r<n−1 ∧ r \ne p ∧ r \ne q} do
    for (k <- ALL_IDs filter { r  => r != p && r != q }) { 
    /* Optimization 2: For all other servers, S K I P messages are
     * not sent immediately, instead they wait for a future SUGGEST
     * message. 
     */
      // if need to skip[k] = {} then
      if (need_to_skip(k).isEmpty) { 
	/* Set timer for Accelerator */
	SetTimer(k, tau);	
	/* Set the kth timer to trigger at τ unit time from now */
      } else {  
	// need to skip[k] ← need to skip[k]∪SkipSet;
	need_to_skip(k) = need_to_skip(k) ++ SkipSet
      } 
      /* Check if the number of outstanding SKIP is greater than α. */
      // if size(need to skip[k]) > α then
      if (need_to_skip(k).size > alpha) { 
	/* By Accelerator 1, need to propagate the S K I P messages
	 * when the outstanding S K I P messages is larger than α.
	 */
	// Call SendSkip(k);  /* propagate the SKIP messages to server k. */
	SendSkip(k)
      }
    }


    // index <- min{j : owner(j) = p \wedge j > i};
    // BUG!? in pseudo-code: if index > i, resets index to already used value.
    // index = instanceNumbers(i) find { j => owner(j) == p } get 

    // FIX: don't change if index is already larger than calculated new index
    index = index max (instanceNumbers(i) find { j => owner(j) == p } get)
  }

  // DownCall OnSuspect(q)
  def OnSuspect(q :ServerID) { 
    /* Rule 3: p revokes q for large block of instances,
      when suspecting server q has failed. */ 
    // C_q = min{i : owner(i) = q ∧ learned(i) = ⊥}; 
    val Cq = instanceNumbers() find { i => (owner(i) == q) && (learned(i) == bot) } get

    //if C_q <index+β then
    //   RevokeSet←{i:Cq ≤i≤ index + 2β ∧ owner(i) = q ∧ learned(i) = ⊥} ; 
    //   forall k ∈ RevokeSet do
    //       DownCall Revoke(k) /* Revoke instance k. */
    //   end
    // end
    if (Cq < index + beta) { 
      val RevokeSet = Cq to (index + 2*beta) filter { i=> owner(i) == q &&  learned(i) == bot }
      for (k <- RevokeSet) { 
	Revoke(k)
      }
    }
  }
   
  // UpCall OnLearned(i, v)	
  def OnLearned(i :InstanceNr, v :Value) { 
    /*Upon instance i learns value v. */

    // if owner(i) = p ∧ proposed[i] \ne v then
    //   /*Rule 4: v must be no-op and proposed[i] must be re-suggested. */ 
    //   Call OnClientRequest (proposed[i]);
    // end
    // Call CheckCommit;

    if (owner(i) == p && (v==noop || proposed(i).id != v.id)) {  // IMPL compare ids to make it faster
      println("repropose")
      OnClientRequest(proposed(i))
    }
    CheckCommit
  }

  def OnTimeout(k :ServerID) { 
    /* The kth timer times out. */

    // Call SendSkip(k);     /* propagate the SKIP messages to server k */
    SendSkip(k)
  }

  // Procedure SendSkip(k)
  def SendSkip(k :ServerID) { 
    CancelTimer (k);	/* Cancel kth timer */
    // forall q ∈ need to skip[k] do 
    //    DownCall Skip(q);
    // end
    for(q <- need_to_skip(k)) { 
      Skip(q)
    }
    // need to skip[k] ← {};
    need_to_skip.remove(k) // IMPL: emptyset is default
  }

  // Procedure CheckCommit 
  def CheckCommit { 
    /*Check if a new value can be commited */
    // while learned(expected) \ne ⊥ do 
    //   v ← learned(expected);
    //   if v \ne no-op ∧ v \not\in {learned(i) : 0 ≤ i < expected} then 
    //  /* Commit value v only if it is not a no-op and is not a duplication. */
    //     UpCall OnCommit (v);
    //   end
    //   expected ← expected + 1;
    // end
    while (learned(expected).nonEmpty) { 
      val v :Value = learned(expected).get
      if ((v != noop) && (0 until expected find { i => learned(i) == v }).isEmpty) { 
      // if ((v != noop)) { 
	println("Commit:"+expected+" "+v.id)
	OnCommit(v)
      } else if(v == noop) { 
	println("Commit:"+ expected+" no-op")
      }
      expected = expected + 1
    }
  }
} 


trait MenciusTimers extends DSLProtocol { 
  private val activeTimers = collection.mutable.HashSet.empty[ServerID]

  def OnTimeout(k :ServerID) :Unit

  def CancelTimer(k :ServerID) { 
    activeTimers.remove(k)
  }

  def SetTimer(k :ServerID, duration :Duration) { 
    activeTimers.remove(k)
    | AFTER duration DO { 
      if(activeTimers.contains(k)) { 
	activeTimers.remove(k)
	OnTimeout(k)
      }
    }
  }
}

