name := "async-rounds"

organization := "ch.epfl.lsr"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.0-M7"

scalacOptions += "-deprecation"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/"

libraryDependencies += "ch.epfl.lsr" %% "distal" % "0.1-SNAPSHOT"

fork := true
