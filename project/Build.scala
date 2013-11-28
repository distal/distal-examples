import sbt._
import Keys._
import ch.epfl.lsr.sbt.distal.DistalLocalRunner._

object ExamplesBuild extends Build {

  lazy val typesafe = "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  lazy val typesafeSnapshot = "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/"

  def computeSettings = {
    Defaults.defaultSettings ++ super.settings ++
    Seq[Setting[_]](
       libraryDependencies ++= Seq(

         "org.apache.commons" % "commons-math" % "2.2",
	 "ch.epfl.lsr" %% "distal" % "0.1"
       ),
       resolvers ++= Seq(
	 typesafe,
	 typesafeSnapshot
       ),
      name := "distal-paxos",
      organization := "ch.epfl.lsr",
      version := "0.1",
      scalaVersion := "2.10.1",
      scalacOptions ++= Seq("-deprecation", "-feature"),
      fork := true
    ) ++ distalLocalRunnerSettings ++ Seq(
      distalProtocolsMap := Map("1" -> Seq("ch.epfl.lsr.ping.Server"), "2" -> Seq("ch.epfl.lsr.ping.Client"))
    ) ++ PrivateSettings()
  }

  lazy val project = Project(id = "distal-examples",
                             base = file("."),
                             settings = computeSettings)
}
