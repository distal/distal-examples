package ch.epfl.lsr.performance

import org.apache.commons.math.stat.descriptive.SummaryStatistics

import java.util.concurrent.TimeUnit.{ NANOSECONDS, MILLISECONDS, SECONDS }

trait SimpleSummaryStats { 
  def getIdentifier :String
  val discardFor :Int
  val collectFor :Int

  private var printedReport = false
  private val stats = new SummaryStatistics
  private var firstRequestTS: Long = -1
  private var lastRequestTS: Long = -1
  private lazy val simStart :Long = System.nanoTime()
  private lazy val discardUntil = simStart + NANOSECONDS.convert(discardFor, SECONDS)
  private lazy val reportAfter = discardUntil + NANOSECONDS.convert(collectFor, SECONDS)
  
  var printed = false
  private var lastEventId = -1

  def recordEvent(i :Int) { 
    if(!printed) { 
      //println("%s: now %d starting at %d %d".format(getIdentifier, simStart, discardUntil, i))
      printed = true
    }
    lastEventId = i
    val now = System.nanoTime()
    if (now > discardUntil) { 
      if (firstRequestTS == -1) {
	//println("%s: Starting to record with event %d (@%d)".format(getIdentifier, i, System.nanoTime))
        firstRequestTS = now;
      } else { 
        stats.addValue(now - lastRequestTS)
      }
    } 
    lastRequestTS = now;

    if (now > reportAfter) { 
      if(!printedReport) { 
        printedReport = true
        println(report)
      }
    }
  }

  def nanos2millis(nanos :Double) = 
    NANOSECONDS.convert(nanos.toLong, MILLISECONDS)

  def report = { 
    println("%s: report requested (last=%d @%d)".format(getIdentifier, lastEventId, System.nanoTime))
    val duration = MILLISECONDS.convert(lastRequestTS - firstRequestTS, NANOSECONDS)
    val thrpt = ((stats.getN().toDouble / duration)*1000)  //"CLIENT "+clientID+" finishing time "+ System.nanoTime()+" last request "+lastRequestTS+" \n"+
    val str = "%s % 6d %6.2f % 6d % 6d % 6d % 6d".format(getIdentifier, 
							    stats.getN(), 
							    thrpt, 
							    nanos2millis( stats.getMean ),
							    nanos2millis( stats.getStandardDeviation ), 
							    nanos2millis( stats.getMin ),
							    nanos2millis( stats.getMax ))
    str
  }
}
