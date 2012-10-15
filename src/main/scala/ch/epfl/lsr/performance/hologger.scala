package ch.epfl.lsr.performance

import java.{ io => IO}

object HOLogging { 
  def getLogDir = { 
    System.getenv("LOG_DIR")
  }
  def withcomma(a:String, b:String) = { a +","+ b }
  def time() = System.nanoTime 

}

class HOLogger(localId :String) { 
  import HOLogging._

  var start :Long = time()

  def duration = { 
    val now = time()
    val ret = now - start
    start = now
    ret
  }

  val os = new IO.PrintStream( new IO.FileOutputStream(getLogDir+"/HO."+localId) )

  def log(p :String, r:Int, set :Seq[String]) = { 
    os.print("HO(%s,%d)={%s} (%d)\n".format(p,r,set map { _.toString } reduceLeft withcomma, duration))
    os.flush
  }

  def shutdown = { os.close }

}


class AllHOLogger(localId :String, ALL :Set[String], f:Int) { 
  import ch.epfl.lsr.asyncrounds.Round  
  import HOLogging._
  val n = ALL.size
  var previous = ""

  val os = new IO.PrintStream( new IO.FileOutputStream(getLogDir+"/allHO."+localId) )

  def log(set :Seq[Round]) { 
    var hos :Set[String] = ALL.toSet
    val rounds = set.groupBy(_.i ).keySet
    if(rounds.size > 1) { 
      println(" ALERT: "+set)
    } else if(rounds.head > 1) { 
      var str = ""
      for { 
	msg <- set.sortBy(_.sender)
	p = msg.sender
	set = msg.previousHO
	r = msg.i - 1
	if((set != null) && set.nonEmpty)
      } { 
	hos = hos & (set.toSet + p)
	str = str+" %s:{%s}".format(p,set.sorted reduceLeft withcomma)
      }
      val eqPrev = (str==previous).toString
      val hostring = if(hos.size == n-f)
			"true:{"+(hos.toSeq.sorted reduceLeft withcomma)+"}"
		     else
			"false"
      os.print("%d %s %s %s\n".format(rounds.head, str, eqPrev, hostring))
      previous = str
    }
  }

  def shutdown = {     
    os.flush
    os.flush
    os.flush
    os.close 
  }





}
