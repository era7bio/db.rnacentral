name          := "db.rnacentral"
organization  := "ohnosequences"
description   := "Mirror and preprocessing of RNACentral data"

scalaVersion := "2.11.8"

bucketSuffix  := "era7.com"

GithubRelease.repo := { organization.value +"/"+ name.value }

resolvers := Seq(
  "Era7 private maven releases"  at s3("private.releases.era7.com").toHttps(s3region.value.toString)
) ++ resolvers.value

libraryDependencies ++= Seq(
  "ohnosequences" %% "fastarious" % "0.6.0",
  "ohnosequences" %% "blast-api"  % "0.7.0",
  "ohnosequences-bundles" %% "blast"     % "0.3.0",
  "com.github.tototoshi"  %% "scala-csv" % "1.2.2",
  // Test only:
  "era7" %% "defaults" % "0.1.0" % Test
)

wartremoverErrors in (Test, compile) := Seq()
wartremoverErrors in (Compile, compile) := Seq()

// shows time for each test:
testOptions in Test += Tests.Argument("-oD")
// disables parallel exec
parallelExecution in Test := false

generateStatikaMetadataIn(Compile)

// This includes tests sources in the assembled fat-jar:
fullClasspath in assembly := (fullClasspath in Test).value

// This turns on fat-jar publishing during release process:
publishFatArtifact in Release := true
