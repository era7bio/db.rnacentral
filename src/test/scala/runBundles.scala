package era7bio.db.test

import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._, s3._

import era7bio.db._, rnaCentralCompats._
import era7.defaults._

case object runBundles {

  // use `sbt test:console`:
  // > era7bio.db.test.bundles.runBundle(...)
  def runBundle[B <: AnyBundle](compat: DefaultCompatible[B], user: AWSUser): List[String] =
    EC2.create(user.profile)
      .runInstances(
        amount = 1,
        compat.instanceSpecs(
          c3.x2large,
          user.keypair.name,
          Some(ec2Roles.projects.name)
        )
      )
      .map { _.getInstanceId }
}
