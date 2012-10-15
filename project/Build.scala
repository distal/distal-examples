import sbt._ 
import Keys._

import sbtassembly.Plugin._
import sbtassembly.Plugin.{ AssemblyKeys => Ass }
import sbtg5k.G5kPlugin._
import sbtg5k.G5kPlugin.{g5kKeys => G5}

object AsyncRoundsBuild extends Build { 

  lazy val typesafe = "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  lazy val typesafeSnapshot = "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/"

  def computeSettings = { 
    Defaults.defaultSettings ++ super.settings ++ baseAssemblySettings ++ 
    Seq[Setting[_]](
       libraryDependencies ++= Seq(

         "org.apache.commons" % "commons-math" % "2.2",
	 "ch.epfl.lsr" %% "distal" % "0.1-SNAPSHOT"

       ),
       resolvers ++= Seq(
	 typesafe,
	 typesafeSnapshot
       ),
      //name := "async-rounds",
      organization := "ch.epfl.lsr",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.10.0-M7",
      scalacOptions ++= Seq("-deprecation", "-feature"), 
      fork := true
     ) ++ PrivateSettings()
  }

  lazy val project = Project(id = "async-rounds",
                             base = file("."),
                             settings = computeSettings)
}
