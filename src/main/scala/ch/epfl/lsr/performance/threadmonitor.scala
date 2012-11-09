package ch.epfl.lsr.performance

import java.lang.Runtime
import java.lang.management.ManagementFactory._
import java.lang.management.ThreadMXBean

import java.util.concurrent.TimeUnit.{ NANOSECONDS, MILLISECONDS, SECONDS }

object ThreadMonitor { 
  class WrappedBean { 
    val bean = { 
      val b = getThreadMXBean
      b setThreadCpuTimeEnabled true
      b setThreadContentionMonitoringEnabled true
      b
    }
    val startTime = System.currentTimeMillis

    def reportString = { 
      import bean._
      
      ("THREAD  id:   time   user   wait  block  times name "+ (System.currentTimeMillis - startTime))+"\n"+
      (getAllThreadIds.map { 
	id =>
	  val info = getThreadInfo(id, 0) // 0 == no stack trace
	  "THREAD %3x: %6d %6d %6d %6d %6d %s".format(id,
						      MILLISECONDS.convert( getThreadCpuTime(id), NANOSECONDS), 
						      MILLISECONDS.convert( getThreadUserTime(id), NANOSECONDS),
						      info.getWaitedTime,
						      info.getBlockedTime,
						      info.getBlockedCount,
						      info.getThreadName)
      }.mkString("\n"))
    }
    
    def printReport = println("\n"+reportString)
  }

  private lazy val thehook = { 
    new Thread{ 
      val bean = new WrappedBean
      override def run { 
	bean.printReport
      }
    }
  }

  def installHook { 
    Runtime.getRuntime.addShutdownHook(thehook)
  }

  def getBean = new WrappedBean

}



