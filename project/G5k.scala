package sbtg5k
import sbt._
import Keys._
import classpath.ClasspathUtilities


//import sbtassembly.Plugin.{ AssemblyKeys => Ass}

object G5kPlugin extends Plugin { 
  import g5kKeys._
  import sbtplugins.RemoteHelpers._

  object g5kKeys { 
    val g5kDeploy = TaskKey[Unit]("g5kdeploy")
    val g5kRun    = TaskKey[Int]("g5krun")

    val g5kUser = SettingKey[String]("g5kuser")
    val g5kSite = SettingKey[String]("site")
    val g5kCluster = SettingKey[String]("cluster")
    val g5kDst  = SettingKey[String]("dst")
//    val g5kJar  = SettingKey[File]("g5k-jar")
//    val g5kOpts = SettingKey[String]("g5k-oarsub-options")
    val g5kRunOne = SettingKey[String]("g5k-run-one-script")
    val g5kRunAll = SettingKey[String]("g5k-run-all-script")
    val g5kNodes = SettingKey[Seq[Int]]("g5k-nodes-seq")
  }


  val g5ksettings = Seq(
    g5kDeploy <<= (streams,g5kDst,g5kUser,g5kSite,g5kRunAll,g5kRunOne,    
		   fullClasspath in Runtime, packageBin in Compile,mainClass in Runtime,resourceDirectory in Runtime) map { 
      (out,dst,user,sitename,all,one,cp :Classpath, binJar,main,resources) =>
	type CPelement = Attributed[File]

	val site = sitename +".g5k"
	val (jars:Classpath,dirs:Classpath) = cp partition { x :CPelement => ClasspathUtilities.isArchive(x.data) }

	val jar = binJar.getName
	val log = new FilteringLogger("Killed by signal", out.log)
	val rmmkdir = join("rm -rf",dst,"&&","mkdir -p",dst,dst+"/jars")
      	println("SCP user:"+user)
	ssh(log,user,site,rmmkdir)
	scp(log,user,site,joinFiles(jars),dst+"/jars/")
	scp(log,user,site,binJar.toString,dst+"/jars/")
	scp(log,user,site,resources+"/"+all,dst)
	scp(log,user,site,resources+"/"+one,dst)
	val cd = join("cd",dst)
	//val jarxf = join("jar xf ",jar,all,one)
	val sed = join("sed -i.orig -e ",buildSedExpr("JAR",jar,"DIR",dst),one,all)
	val chmod = join("chmod +x ",one,all)
      
	//ssh(log,user,site,cd,jarxf,sed,chmod)
	ssh(log,user,site,cd,sed,chmod)
    }, 
    g5kRun <<= (streams,g5kDst,g5kUser,g5kSite,g5kCluster,g5kRunAll,g5kNodes) map { 
      (out,dst,user,sitename,cluster,script,nodeseq) =>
	val site = sitename+".g5k"
	val cmds = for(nodes <- nodeseq) yield ("oarsub -l nodes="+ nodes +",walltime=0:05:00 -p \\\"cluster='"+cluster+"'\\\" -E out.txt -O out.txt "+dst+"/"+script) 
	println(cmds)
	//(cmd run out.log).exitValue
      ssh(out.log,user,site, cmds :_*)
      //ssh(out.log,user,site,"rm -rf logs/ out.txt; date >> out.txt; oarsub -l nodes=4 -E out.txt -O out.txt async-rounds/run-all.sh; tail -f out.txt")
    } dependsOn g5kDeploy,
    g5kRunAll := "run-all.sh",
    g5kRunOne := "run-one.sh"
  )
}

