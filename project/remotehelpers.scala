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

  def join(strings :String*) = strings.mkString(" ")
  def joinFiles(files :Classpath) = files.map(_.data.toString).mkString(" ")

  def scp(log: ProcessLogger,//user: String,
    site :String,  src: String, dst: String) = {
    log.info("Copying file " + src + " to " + site + ":" + dst + "...")
    //val cmd = "scp -r " + src + " " + user + "@" + site + ":" + dst
    val cmd = "scp -r " + src + " " + site + ":" + dst
    cmd ! new PrefixingLogger(site+":",log)

  }

  def ssh(log: ProcessLogger, //user: String,
    site :String, commands :String*) = {
    val chain = commands(0) + commands.toList.tail.foldLeft("")(_ + " && " + _)
    log.info("Executing command chain '" + chain + "' at " + site + "...")
    //"ssh " + user + "@" + site + " bash -c \"" + chain +"\"" ! new PrefixingLogger("["+site+"]:",log)
    "ssh " + site + " bash -c \"" + chain +"\"" ! new PrefixingLogger("["+site+"]:",log)
  }

  def sed(expr:String, files :String*) = join("sed -i.orig -e " +: expr +: files :_*)


  def doDeploy(log: ProcessLogger, frontend :String, dst: String, jars :Classpath, resources :Seq[File]) = {

    val rmmkdir = join("rm -rf",dst,"&&","mkdir -p",dst,dst+"/jars")
    val resourceFiles = resources.map { _.getName } mkString " "

    //println("SCP user:"+user)
    ssh(log,frontend,rmmkdir)
    scp(log,frontend,joinFiles(jars),dst+"/jars/")
    //    scp(log,frontend,binJar.toString,dst+"/jars/")
    scp(log,frontend,resources.mkString(" "), dst)
    val cd = join("cd",dst)
    //val jarxf = join("jar xf ",jar,all,one)
    val sed = join("sed -i.orig -e ",buildSedExpr("DIR",dst),resourceFiles)
    val chmod = join("chmod +x ", resourceFiles)

    //ssh(log,user,frontend,cd,jarxf,sed,chmod)
    ssh(log,frontend,cd,sed,chmod)
  }


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
