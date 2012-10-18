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
    val ClusterRun    = TaskKey[Int]("cluster-run")
    val ClusterShutdown    = TaskKey[Int]("cluster-shutdown")

    val ClusterUser = SettingKey[String]("user")
    val ClusterHost = SettingKey[String]("host")
    val ClusterDst  = SettingKey[String]("dst")
    val ClusterRunOne = SettingKey[String]("cluster-run-one-script")
    val ClusterRunAll = SettingKey[String]("cluster-run-all-script")
    val ClusterNodesCount = SettingKey[Int]("numNodes")
    val ClusterHostListFile = SettingKey[String]("host-list-file")
    val ClusterShutdownScript = SettingKey[String]("cluster-shutdown-script")
    val ClusterScripts = SettingKey[IndexedSeq[String]]("cluster-all-script-files")
  }


  val clustersettings = Seq(
    ClusterScripts <<= (streams,ClusterRunAll,ClusterRunOne,ClusterHostListFile,ClusterShutdownScript) apply { 
      (streams,all,one,list,kill) => IndexedSeq(all,one,list,kill)
    },
    ClusterDeploy <<= (streams,ClusterDst,ClusterUser,ClusterHost,ClusterScripts,
		   fullClasspath in Runtime, packageBin in Compile //,mainClass in Runtime
		     ) map { 
      (out,dst,user,cluster,scripts,cp :Classpath, binJar )=> //,main) =>
	type CPelement = Attributed[File]
	val hostlist = scripts(2)

	val (jars:Classpath,dirs:Classpath) = cp partition { x :CPelement => ClasspathUtilities.isArchive(x.data) }

	val jar = binJar.getName
	val log = new FilteringLogger("Killed by signal", out.log)
	val rmmkdir = join("rm -rf",dst,"&&","mkdir -p",dst,dst+"/deps")
	ssh(log,user,cluster,rmmkdir)
	scp(log,user,cluster,joinFiles(jars),dst+"/deps/")
	scp(log,user,cluster,binJar.toString,dst)
	val cd = join("cd",dst)
	val jarxf = join("jar xf " +: jar +: scripts :_*)
	val sedcmd = sed(buildSedExpr("JAR",jar,"DIR",dst, //"MAIN",main.get,
				      "HOSTLIST",hostlist),scripts :_*)
	val chmod = join("chmod +x " +: scripts :_*)
	ssh(log,user,cluster,cd,jarxf,sedcmd,chmod)
    }, 
    ClusterRun <<= (streams,ClusterDst,ClusterUser,ClusterHost,ClusterRunAll,ClusterNodesCount) map { 
      (out,dst,user,cluster,script,nodes) =>
        ssh(out.log, user, cluster, dst+"/"+script+" "+nodes) 
      //(cmd run out.log).exitValue
    } dependsOn ClusterDeploy, 
    ClusterShutdown <<= (streams,ClusterDst,ClusterUser,ClusterHost,ClusterShutdownScript) map { 
      (out,dst,user,cluster,script) =>
        ssh(out.log, user, cluster, dst+"/"+script) 
      
      //ssh(out.log,user,site,"rm -rf logs/ out.txt; date >> out.txt; oarsub -l nodes=4 -E out.txt -O out.txt async-rounds/run-all.sh; tail -f out.txt")
    } dependsOn ClusterDeploy,
    ClusterRunAll := "cluster-all.sh",
    ClusterRunOne := "cluster-one.sh",
    ClusterShutdownScript := "cluster-shutdown.sh"
  )
}


