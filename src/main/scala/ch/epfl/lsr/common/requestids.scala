package ch.epfl.lsr.common 

case class RequestID(ID :String, seqno :Long)

trait NumberedRequestIDs { 
  val ID :String

  var seqno = -1

  private def nextSeqNo = { 
      seqno = seqno + 1
      seqno 
  }

  def nextReqId = RequestID(ID, nextSeqNo)
  def isPrevId(rid :RequestID) = { 
    (rid.ID == ID) && (rid.seqno == seqno)
  }
}
