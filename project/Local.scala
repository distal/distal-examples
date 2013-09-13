package sbt.distal
import sbt._
import Keys._

object DistalLocalRunner extends Plugin {
  import LocalRunnerKeys._
  val basePort = 4000 // TODO: make configurable

  override lazy val settings = localDistalSettings

  object LocalRunnerKeys {
    val startProtocolsLocal = TaskKey[Unit]("local-start-protocols", "starts the protocols locally")
    lazy val localProtocolsMap = SettingKey[Map[String,Seq[String]]]("local-protocols-map", "map ports to protocol classes")
  }


  val localDistalSettings = Seq(
    startProtocolsLocal <<= (streams,localProtocolsMap,fullClasspath in Runtime, packageBin in Compile) map {
      (out,protocols,classpath :Classpath,packageBin) =>
      val cpString = classpath.map(_.data).mkString(":")
      val locations =
        for {
          ((k,v),i) <- protocols.zipWithIndex
          port = basePort + i
          clazz <- v
        }  yield("lsr://"+clazz+"@localhost:"+port+"/"+k+"/"+clazz)
      val cmdFormat = "java -cp " + cpString + " ch.epfl.lsr.distal.deployment.LocalRunner " + " %s " + " " + locations.mkString(" ")
      val processes = for {
        (k,v) <- protocols
      } yield (cmdFormat.format(k) run out.log)

      processes.foreach(_.exitValue)
      println("done")
    }
  )

}
