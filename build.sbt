name          := "db.rnacentral"
organization  := "ohnosequences"
description   := "Mirror and preprocessing of RNACentral data"

scalaVersion := "2.11.8"

bucketSuffix  := "era7.com"

resolvers := Seq(
  "Era7 private maven releases"  at s3("private.releases.era7.com").toHttps(s3region.value.toString)
) ++ resolvers.value

libraryDependencies ++= Seq(
  "ohnosequences" %% "fastarious" % "0.6.0",
  "ohnosequences" %% "blast-api"  % "0.7.0",
  "ohnosequences" %% "statika"    % "2.0.0-M5",
  "ohnosequences-bundles" %% "blast"     % "0.3.0",
  "com.github.tototoshi"  %% "scala-csv" % "1.2.2",
  // Test only:
  "era7"          %% "defaults"  % "0.1.0" % Test,
  "org.scalatest" %% "scalatest" % "2.2.6" % Test
)

wartremoverErrors in (Test, compile) := Seq()

// shows time for each test:
testOptions in Test += Tests.Argument("-oD")
// disables parallel exec
parallelExecution in Test := false

addFatArtifactPublishing(Test)

enablePlugins(BuildInfoPlugin)
buildInfoPackage := "generated.metadata.db"
buildInfoObject  := "rnacentral"
buildInfoOptions := Seq(BuildInfoOption.Traits("ohnosequences.statika.AnyArtifactMetadata"))
buildInfoKeys    := Seq[BuildInfoKey](
  organization,
  version,
  "artifact" -> name.value,
  "artifactUrl" -> fatArtifactUrl.value
)
