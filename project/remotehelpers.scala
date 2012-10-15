package sbtplugins;
import sbt._
import Keys._
import classpath.ClasspathUtilities

object RemoteHelpers { 

  def buildSedExpr(pairs :String*) = { 
    var str = ""
    val iter = pairs.iterator 
    while(iter.hasNext) { 
      str += "s/@@"+iter.next+"@@/"+iter.next+"/g;"
    }
    "'"+str+"'"
  }

  def join(strings :String*) = strings.reduce(_ + " " + _)
  def joinFiles(files :Classpath) = files.map(_.data.toString).reduce(_ + " " + _)

  def scp(log: ProcessLogger,user: String, site :String,  src: String, dst: String) = {
    log.info("Copying file " + src + " to " + site + ":" + dst + "...")
    "scp -r " + src + " " + user + "@" + site + ":" + dst ! new PrefixingLogger(site+":",log)
  }

  def ssh(log: ProcessLogger, user: String, site :String, commands :String*) = {
    val chain = commands(0) + commands.toList.tail.foldLeft("")(_ + " && " + _)
    log.info("Executing command chain '" + chain + "' at " + site + "...")
    "ssh " + user + "@" + site + " bash -c \"" + chain +"\"" ! new PrefixingLogger("["+site+"]:",log)
  }

  def sed(expr:String, files :String*) = join("sed -i.orig -e " +: expr +: files :_*)

class PrefixingLogger(prefix: String, underlying :ProcessLogger) extends ProcessLogger { 
  def buffer[T](f : =>T) = underlying.buffer[T](f)  // ?

  def error(s : =>String) = underlying.error({prefix+" "+s})
  def info(s : =>String) = underlying.info({ prefix +" "+s})
}

class FilteringLogger(filter: String, underlying :ProcessLogger) extends ProcessLogger { 
  def buffer[T](f : =>T) = underlying.buffer[T](f)  // ?

  def error(s : =>String) = { 
    val str:String = s
    if(s.contains(filter))
      ()
    else
      underlying.error(s)
  }

  def info(s : =>String) = { 
    val str:String = s
    if(s.contains(filter))
      ()
    else
      underlying.info(s)
  }

}

}
