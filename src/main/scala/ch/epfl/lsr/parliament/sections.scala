package ch.epfl.lsr.parliament.sections

import ch.epfl.lsr.distal._
import ch.epfl.lsr.netty.protocol.ProtocolLocation
import collection.Set

object Types { 
  type Priest   = String
  type BallotNr = Option[Long]
  type DecreeNr = Long

  type MinusInftyOr[T] = Option[T]
  val MinusInfty = None

  class OptionWithLessThan[T](val o :Option[T]) { 
    import scala.math.Ordering.{ Option }

    def compare(b :Option[T]) (implicit order :Ordering[Option[T]]) = order.compare(o,b)
    def <(b :Option[T]) (implicit order :Ordering[Option[T]]) = order.lt(o, b)
    def >(b :Option[T]) (implicit order :Ordering[Option[T]]) = order.gt(o, b)
    def >=(b :Option[T]) (implicit order :Ordering[Option[T]]) = order.gteq(o, b)
    def <=(b :Option[T]) (implicit order :Ordering[Option[T]]) = order.lteq(o, b)

    def +(i :Int) (implicit numeric :Numeric[T]) = { 
      o match { 
	case None => Some(numeric.fromInt(i))
	case Some(x) => Some(numeric.plus(x, numeric.fromInt(i)))
      }
    }
  }
  
  import language.implicitConversions
  implicit def __option2optionWithLT[T](o :Option[T]) :OptionWithLessThan[T] = new OptionWithLessThan(o)


  // p. 5 
  case class Ballot[Decree](dec: Decree, qrm :Set[Priest], vot :Set[Priest], bal :BallotNr) { 
    B =>
      def sucessful = vot subsetOf qrm
      def laterThan(Bprime :Ballot[Decree]) = B.bal < Bprime.bal
  }


  // p.6: 
  // A vote v was defined to be a quantity consisting of three components: 
  // a priest $v_{pst}$, a ballot number $v_{bal}$, and a decree $v_{dec}$.
  case class Vote[Decree](pst :Priest, bal :BallotNr, dec :Decree) { 
    // The remaining fragment indicates that, for any votes $v$ and $v'$, 
    // if $v_bal<v'_bal$ then $v<v'$.
    v => 
      def <(vprime :Vote[Decree]) = v.bal < vprime.bal
      def isNull = false
  }

  //implicit object __voteOrdering extends Ordering[Vote_[_]] { 
  //def compare(x :Vote_[_], y :Vote_[_]) = x.bal compare y.bal
  //}
}

import Types._

trait Section2_1 { 
  type Decree 
  type Vote = Types.Vote[Decree]
  def Vote(pst :Priest, bal :BallotNr, dec :Decree) = Types.Vote[Decree](pst, bal, dec)
  val BLANK :Decree
  
  // the priest executing this code
  val priest :Priest

  object NullVote extends Vote(priest, MinusInfty, BLANK) { 
    override def isNull = true
  }

  def MaxVote(b :BallotNr, p :Priest, votes :Seq[Vote]) :Vote = { 
    // MaxVote(b, p, B) is the largest in the set
    // ${v \in Votes(B) : (v_{pst} = p) \wedge (v_{bal} < b)} \cup {null_p}$
    votes.toSet.filter(v => v.pst == p && v.bal < b).maxBy { _.bal }
  }

  // For any nonempty set $Q$ of priests, $MaxVote(b, Q, B)$ was defined to equal 
  // the maximum of all votes $MaxVote(b, p, B)$ with $p$ in $Q$.
  def MaxVote(b :BallotNr, Q :collection.Set[Priest], votes :Seq[Vote]) :Vote = { 
    Q.map(p => MaxVote(b, p, votes)).maxBy { _.bal }
  }


}

trait Ledger[Decree] { 
  def write(d :Decree)
  def allUpTo :DecreeNr
  def getDecreesAbove(n :DecreeNr) :Map[DecreeNr,Decree]
  def missingUpto(n :DecreeNr) :Seq[DecreeNr]
}

trait Section2_3 extends DSLProtocol with Section2_1 { 
  // before section 3 this was 
  // case class NextBallot(b :BallotNr, n :DecreeNr) extends Message 
  case class NextBallot(b :BallotNr, n :DecreeNr) extends Message 
  object NextBallot { def apply(b :BallotNr) :NextBallot = NextBallot(b,-1) }
  
  case class LastVote(b :BallotNr, v :Vote) extends Message
  case class BeginBallot(b :BallotNr, d :Decree) extends Message
  case class Voted(b :BallotNr, q: Priest) extends Message
  case class Success(d :Decree) extends Message
  
  case class SkipToBallot(nextBal :BallotNr) extends Message

  def MAJORITY : Int


  // TODO: the next three should be on the ledger ... 
  var lastTried :BallotNr = MinusInfty
  var nextBal   :BallotNr = MinusInfty
  // according to p.11 this is -\infty, but we $null_p$ instead.
  // note: $null_p$ is also sent in the preliminary protocol
  var prevVote  :Vote     = NullVote 
  
  val ledger :Ledger[Decree] 
  var currentDecree : Decree = BLANK

  def nextDecree : Decree

  // p12:
  // (1) Priest $p$ chooses a new ballot number $b$ greater than 
  // $lastTried[p]$, sets $lastTried[p]$ to $b$, and sends a $NextBallot(b)$ 
  // message to some set of priests.
  def startBallot() { 
    val b = lastTried + 1
    lastTried = b
    | SEND NextBallot(b) TO ALL 
  }
  
  // (2)  Upon receipt of a $NextBallot(b)$ message from $p$ with $b > nextBal[q]$, 
  // priest $q$ sets $nextBal[q]$ to $b$ and sends a $LastVote(b, v)$ message to $p$, 
  // where $v$ equals $prevVote[q]$. (A $NextBallot(b)$ message is ignored if $b <= nextBal[q]$.)
  UPON RECEIVING NextBallot WITH { _.b > nextBal } DO { 
    msg => 
    | SEND createLastVote(msg) TO SENDER
    
    | DISCARD msg
  }
  
  def createLastVote(msg :NextBallot) = { 
    nextBal = msg.b
    LastVote(nextBal, prevVote)
  }

  // (3) After receiving a $LastVote(b, v)$ message from every priest 
  // in some majority set $Q$, where $b = lastTried[p]$, priest $p$ initiates 
  // a new ballot with number $b$, quorum $Q$, and decree $d$, where $d$ is 
  // chosen to satisfy $B3$. He then sends a $BeginBallot(b, d)$ message to 
  // every priest in $Q$.
  UPON RECEIVING LastVote WITH { _.b == lastTried } TIMES MAJORITY DO { 
    msgs =>
      doStep3(msgs, SENDERS)
  }
  // what happens in step 3 is different for the Parliamentary protocol, so 
  // we us a function instead of directly implementing it. 
  def doStep3(msgs :Seq[LastVote], Q :Set[Priest]) { 
      val knownVotes :Seq[Vote] = msgs.map(_.v)
      // $B3: \forall B\in\mathcal{B}: (MaxVote(B_{bal},B_{qrm},\mathcal{B}){bal}\ne-\infty) 
      // \Rightarrow (B{dec} = MaxVote(B_{bal}, B_{qrm}, \mathcal{B})_dec)$
      val maxVote = MaxVote(lastTried, Q, knownVotes)
      val d = if(maxVote.bal != MinusInfty) maxVote.dec else nextDecree 
      
    | SEND BeginBallot(lastTried, d) TO ALL
      
      currentDecree = d // needed in step (5)

    // TODO: discard these and later LastVote messages
  }

  // (4) Upon receipt of a $BeginBallot(b, d)$ message with $b = nextBal[q]$, 
  // priest $q$ casts his vote in ballot number $b$, sets $prevVote[q]$ to 
  // this vote, and sends a $Voted(b, q)$ message to $p$. 
  // (A BeginBallot(b, d) message is ignored if $b\ne nextBal[q].)
  UPON RECEIVING BeginBallot WITH { _.b == nextBal} DO { 
    msg =>
      prevVote = Vote(ID, nextBal, msg.d)
    | SEND Voted(nextBal, ID) TO SENDER

    | DISCARD msg
  }
  
  // (5) If $p$ has received a $Voted(b, q)$ message from every priest 
  // $q$ in $Q$ (the quorum for ballot number $b$), where $b = lastTried[p]$, 
  // then he writes $d$ (the decree of that ballot) in his ledger and sends 
  // a Success(d) message to every priest.
  UPON RECEIVING Voted WITH { _.b == nextBal } TIMES MAJORITY DO { 
    msgs => 
      ledger write currentDecree
    | SEND Success(currentDecree) TO ALL

      // TODO discard these and later Voted Messages
  }


  // (6) Upon receiving a $Success(d)$ message, a priest enters decree $d$ in his ledger.
  UPON RECEIVING Success DO { 
    msg => 
      ledger write msg.d
  }

}

trait Section2_4 extends Section2_3 { 
  def PRESIDENT :Priest

  // p. 13: 
  // This could be done by extending the protocol to require that if a priest 
  // $q$ received a NextBallot (b) or a $BeginBallot(b, d)$ message from $p$ with 
  // $b < nextBal [q]$, then he sent $p$ a message containing $nextBal[q]$. ...
  UPON RECEIVING NextBallot WITH { nextBal >= _.b } DO { 
    msg =>
      | SEND SkipToBallot(nextBal) TO SENDER

      | DISCARD msg
  }
  UPON RECEIVING BeginBallot WITH { nextBal >= _.b } DO { 
    msg =>
      | SEND SkipToBallot(nextBal) TO SENDER

      | DISCARD msg
  }
  // ... Priest $p$ would then initiate a new ballot with a larger ballot number.
  UPON RECEIVING SkipToBallot DO { 
    msg => 
      lastTried = msg.nextBal
    | DISCARD msg
      startBallot
  }

  // p. 13: The complete protocol therefore included a procedure for choosing a 
  // single priest, called the president, to initiate ballots.
  override def startBallot() { 
    if(PRESIDENT == ID) 
      super.startBallot()
  }

  // TODO elect president and timeouts of page 13ff.

}


trait Section3_1 extends Section2_4 { 

  class Instance(n: DecreeNr, b: BallotNr, d :Decree,  decided :Boolean)

  class ExtendedLastVote(b :BallotNr, v :Vote, val inform: Map[DecreeNr, Decree], val askFor: Seq[DecreeNr], val sender: Priest) extends LastVote(b, v)
  
  override def doStep3(_msgs :Seq[LastVote], Q :Set[Priest]) { 
    val msgs = _msgs.asInstanceOf[Seq[ExtendedLastVote]]
    val receivedInformation : Map[DecreeNr, Decree] = msgs.map{ _.inform }.reduce { _ ++ _ }

    // TODO determine for each instance of synod, what maxVote

  }

  // if a newly elected president $p$ has all decrees with numbers less 
  // than or equal to $n$ written in his ledger, then he sends a 
  // $NextBallot(b, n)$ message that serves as a $NextBallot(b)$ 
  // message in all instances of the Synod protocol for decree 
  // numbers larger than $n$.
  override def startBallot() { 
    val n = ledger.allUpTo
    if(PRESIDENT == ID) { 
      val b = lastTried + 1
      lastTried = b
      | SEND NextBallot(b, n) TO ALL 
    }
  }

  // In his response to this message, legislator $q$ informs p of all 
  // decrees numbered greater than n that already appear in $q$'s ledger 
  // (in addition to sending the usual LastVote information for decrees 
  // not in his ledger), and he asks $p$ to send him any decrees numbered 
  // $n$ or less that are not in his ledger.
  override def createLastVote(msg :NextBallot) = { 
    val lv = super.createLastVote(msg) 
    new ExtendedLastVote(lv.b, lv.v, ledger.getDecreesAbove(msg.n), ledger.missingUpto(msg.n), ID)
  }

}


// ALTERNATIVE: 
/* Section 3.1:  in the Synod protocol, the president does not choose the decree or the quorum until step 3. */
// so we implement the first two steps from section 2.3

/*
 * (1) Priest $p$ chooses a new ballot number $b$ greater than last $Tried[p]$,sets $lastTried[p]$ to $b$,
 * and sends a $NextBallot(b)$ message to some set of priests.
 * 
 * (2) Upon receipt of a $NextBallot(b)$ message from $p$ with $b > nextBal[q]$, priest q sets $nextBal[q]$ to $b$
 * and sends a $LastVote(b, v)$ message to $p$, where $v$ equals $prevVote[q]$.
 *
 * (A NextBallot(b) message is ignored if b ≤ nextBal[q].)
 */
trait Step1and2[BallotNr, NextBallotMessage] extends DSLProtocol { 
  val ballotNrIsNumeric :Numeric[BallotNr]
  import Numeric.Implicits
  import ballotNrIsNumeric._

  case class NextBallot(b :BallotNr) extends Message 
  case class SkipToBallot(b :BallotNr) extends Message
  
  var lastTried :BallotNr
  var nextBal   :BallotNr

  def LastVoteMessage(b :BallotNr) : Message
 
  def startBallot() { 
    val b = lastTried + one
    lastTried = b
  | SEND NextBallot(b) TO ALL
  }

  UPON RECEIVING NextBallot WITH { _.b > nextBal } DO { 
    msg =>
      nextBal = msg.b
    | SEND LastVoteMessage(msg.b) TO SENDER

    | DISCARD msg
  }

  /* p. 13: 
   * This could be done by extending the protocol to require that if a priest 
   * $q$ received a NextBallot (b) or a $BeginBallot(b, d)$ message from $p$ with 
   * $b < nextBal [q]$, then he sent $p$ a message containing $nextBal[q]$. ... */
  UPON RECEIVING NextBallot WITH {  nextBal >= _.b } DO { 
    msg =>
      | SEND SkipToBallot(nextBal) TO SENDER

      | DISCARD msg
  }

  // ... Priest $p$ would then initiate a new ballot with a larger ballot number.
  UPON RECEIVING SkipToBallot WITH { _.b > lastTried } DO { 
    msg => 
      lastTried = msg.b
    | DISCARD msg
      startBallot
  }
}


trait ParliamentaryStep3[BallotNr] extends DSLProtocol { 
  
  case class LastVote(b :BallotNr) extends Message
  
  def MAJORITY :Int 
  def lastTried :BallotNr
  def LastVoteMessage(b :BallotNr) = LastVote(b)

  UPON RECEIVING LastVote WITH { _.b == lastTried } TIMES MAJORITY DO { 
    msg =>
      
  }
}


case class RealNextBallot(b :Int, n :Int) extends Message

class PaxosParliament(numLegislators :Int, val ID :String) extends Step1and2[Int,RealNextBallot] with ParliamentaryStep3[Int] { 
  val ballotNrIsNumeric = implicitly[Numeric[Int]]

  val MAJORITY = numLegislators/2+1
  
  var lastTried = -1
  var nextBal   = -1

  def NextBallotMessage(b :Int) = RealNextBallot(b, -1)
}
