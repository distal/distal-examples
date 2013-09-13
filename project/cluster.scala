package sbtCluster
import sbt._
import Keys._
import classpath.ClasspathUtilities

//import sbtassembly.Plugin.{ AssemblyKeys => Ass}

object ClusterPlugin extends Plugin {
  import ClusterKeys._
  import sbtplugins.RemoteHelpers._


  object ClusterKeys {
    val ClusterDeploy = TaskKey[Unit]("cluster-deploy")
    val ClusterRun    = TaskKey[Unit]("cluster-run")
    val ClusterShutdown    = TaskKey[Int]("cluster-shutdown")

    //val ClusterUser = SettingKey[String]("user")
    val ClusterHost = SettingKey[String]("cluster-host")
    val ClusterDst  = SettingKey[String]("cluster-dst")
    val ClusterRunOne = SettingKey[String]("cluster-run-one-script")
    val ClusterRunAll = SettingKey[String]("cluster-run-all-script")
    val ClusterNodesCount = SettingKey[Int]("numNodes")
    val ClusterHostListFile = SettingKey[String]("host-list-file")
    val ClusterShutdownScript = SettingKey[String]("cluster-shutdown-script")
    val ClusterScripts = SettingKey[IndexedSeq[String]]("cluster-all-script-files")
  }


  val clustersettings = Seq(
    ClusterScripts <<= (streams,ClusterRunAll,ClusterRunOne//,ClusterHostListFile,ClusterShutdownScript
                       ) apply {
      //(streams,all,one,list,kill) => IndexedSeq(all,one,list,kill)
        (streams,all,one) => IndexedSeq(all,one)
    },
    ClusterDeploy <<= (streams,ClusterDst,ClusterHost,ClusterScripts,
                         fullClasspath in Runtime, packageBin in Compile,
                         resourceDirectory in Runtime
    ) map {
      (out,dst,cluster,scripts,cp :Classpath, binJar, resources) =>
      type CPelement = Attributed[File]

      val log = new FilteringLogger("Killed by signal", out.log)
      val (jars:Classpath,dirs:Classpath) = cp partition { x :CPelement => ClasspathUtilities.isArchive(x.data) }

      doDeploy(log, cluster, dst, Seq(Attributed.blank(binJar)) ++ jars, resources.listFiles)
    },
    ClusterRun <<= (streams,ClusterDst,ClusterHost,ClusterRunAll //,ClusterNodesCount
                    ) map {
      (out,dst,cluster,script //,nodes
      ) =>
      // TODO re-implement run
      // ssh(out.log, cluster, dst+"/"+script+" "+nodes)
      //(cmd run out.log).exitValue
    } dependsOn ClusterDeploy,
    ClusterShutdown <<= (streams,ClusterDst,ClusterHost,ClusterShutdownScript) map {
      (out,dst,cluster,script) =>
        ssh(out.log, cluster, dst+"/"+script)

      //ssh(out.log,user,site,"rm -rf logs/ out.txt; date >> out.txt; oarsub -l nodes=4 -E out.txt -O out.txt async-rounds/run-all.sh; tail -f out.txt")
    } dependsOn ClusterDeploy,
    ClusterRunAll := "cluster-all.sh",
    ClusterRunOne := "cluster-one.sh",
    ClusterShutdownScript := "cluster-shutdown.sh"
  )
}
