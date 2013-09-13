package sbtg5k
import sbt._
import Keys._
import classpath.ClasspathUtilities


//import sbtassembly.Plugin.{ AssemblyKeys => Ass}

object G5kPlugin extends Plugin {
  import g5kKeys._
  import sbtplugins.RemoteHelpers._

  object g5kKeys {
    val g5kDeploy = TaskKey[Unit]("g5k-deploy")
    val g5kRun    = TaskKey[Int]("g5k-run")

//    val g5kUser = SettingKey[String]("g5kuser")
    val g5kSite = SettingKey[String]("site")
    val g5kCluster = SettingKey[String]("g5k-cluster")
    val g5kDst  = SettingKey[String]("dst")
//    val g5kJar  = SettingKey[File]("g5k-jar")
//    val g5kOpts = SettingKey[String]("g5k-oarsub-options")
    val g5kRunOne = SettingKey[String]("g5k-run-one-script")
    val g5kRunAll = SettingKey[String]("g5k-run-all-script")
    val g5kNodes = SettingKey[Seq[Int]]("g5k-nodes-seq")
  }


  val g5ksettings = Seq(
    g5kDeploy <<= (streams,g5kDst, //g5kUser,
      g5kSite, fullClasspath in Runtime, packageBin in Compile,mainClass in Runtime,resourceDirectory in Runtime) map {
      (out,dst,sitename,cp :Classpath, binJar,main,resources) =>
        type CPelement = Attributed[File]

        val frontend = sitename +".g5k"
        val log = new FilteringLogger("Killed by signal", out.log)
        val (jars:Classpath,dirs:Classpath) = cp partition { x :CPelement => ClasspathUtilities.isArchive(x.data) }

      doDeploy(log, frontend, dst, Seq(Attributed.blank(binJar)) ++ jars, resources.listFiles)
    },
    g5kRun <<= (streams,g5kDst,//g5kUser,
      g5kSite,g5kCluster,g5kRunAll,g5kNodes) map {
      (out,dst,sitename,cluster,script,nodeseq) =>
        val site = sitename+".g5k"
        val cmds = for(nodes <- nodeseq) yield ("oarsub -l nodes="+ nodes +",walltime=0:05:00 -p \\\"cluster='"+cluster+"'\\\" -E out.txt -O out.txt "+dst+"/"+script)
        println(cmds)
        //(cmd run out.log).exitValue
        ssh(out.log,site, cmds :_*)
       //ssh(out.log,user,site,"rm -rf logs/ out.txt; date >> out.txt; oarsub -l nodes=4 -E out.txt -O out.txt async-rounds/run-all.sh; tail -f out.txt")
    } dependsOn g5kDeploy,
    g5kRunAll := "run-all.sh",
    g5kRunOne := "run-one.sh"
  )
}
