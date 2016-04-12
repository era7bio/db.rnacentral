package era7bio.db

import ohnosequences.cosas._, types._, klists._
import ohnosequences.statika._
import ohnosequences.fastarious.fasta._
import ohnosequences.blast.api._
import ohnosequences.awstools.s3._

import ohnosequencesBundles.statika.Blast

import com.amazonaws.auth._
import com.amazonaws.services.s3.transfer._

import com.github.tototoshi.csv._

import better.files._

import csvUtils._, RNACentral5._


trait AnyBlastDB {
  val dbType: BlastDBType

  val name: String

  val predicate: (Row, FASTA.Value) => Boolean

  private[db] val sourceFasta: S3Object
  private[db] val sourceTable: S3Object

  val s3location: S3Folder

  case object release extends Bundle() {
    val destination: File = File(s3location.key)

    val id2taxa: File = destination / "id2taxa.tsv"

    // This is where the DB will be downloaded
    val dbLocation: File = destination / "blastdb/"
    // This is what you pass to BLAST
    val dbName: File = dbLocation / s"${name}.fasta"

    def instructions: AnyInstructions = {
      LazyTry {
        val transferManager = new TransferManager(new InstanceProfileCredentialsProvider())

        transferManager.downloadDirectory(
          s3location.bucket, s3location.key,
          file".".toJava
        ).waitForCompletion
      } -&-
      say(s"Reference database ${name} was dowloaded to ${dbLocation.path}")
    }
  }
}


case object blastBundle extends Blast("2.2.31")


class GenerateBlastDB[DB <: AnyBlastDB](val db: DB) extends Bundle(blastBundle) {

  // Files
  lazy val sources = file"sources/"
  lazy val outputs = file"outputs/"

  lazy val sourceFasta: File = sources / "source.fasta"
  lazy val sourceTable: File = sources / "source.table.tsv"

  lazy val outputFasta: File = outputs / s"${db.name}.fasta"
  lazy val outputTable: File = outputs / db.release.id2taxa.name


  def instructions: AnyInstructions = {

    val transferManager = new TransferManager(new InstanceProfileCredentialsProvider())

    LazyTry {
      println(s"""Downloading the sources...
        |fasta: ${db.sourceFasta}
        |table: ${db.sourceTable}
        |""".stripMargin)

      transferManager.download(
        db.sourceFasta.bucket, db.sourceFasta.key,
        sourceFasta.toJava
      ).waitForCompletion

      transferManager.download(
        db.sourceTable.bucket, db.sourceTable.key,
        sourceTable.toJava
      ).waitForCompletion
    } -&-
    LazyTry {
      println("Processing sources...")

      processSources(
        sourceTable,
        outputTable
      )(sourceFasta,
        outputFasta
      )
    } -&-
    seqToInstructions(
      makeblastdb(
        argumentValues =
          in(outputFasta) ::
          input_type(DBInputType.fasta) ::
          dbtype(db.dbType) ::
          *[AnyDenotation],
        optionValues =
          title(db.name) ::
          *[AnyDenotation]
      ).toSeq
    ) -&-
    LazyTry {
      println("Uploading the DB...")

      // Moving blast DB files to a separate folder
      val blastdbDir = (outputs / "blastdb").createDirectory()
      outputs.list
        .filter{ _.name.startsWith(s"${outputFasta.name}.") }
        .foreach { f => f.moveTo(blastdbDir / f.name) }

      // Uploading all together
      transferManager.uploadDirectory(
        db.s3location.bucket, db.s3location.key,
        outputs.toJava,
        true // includeSubdirectories
      ).waitForCompletion
    } -&-
    say(s"The database is uploaded to [${db.s3location}]")
  }

  // This is the main processing part, that is separate to facilitate local testing
  final def processSources(
    tableInFile: File,
    tableOutFile: File
  )(fastaInFile: File,
    fastaOutFile: File
  ) {
    tableOutFile.createIfNotExists()
    fastaOutFile.createIfNotExists()

    val tableReader = CSVReader.open(tableInFile.toJava)(tableFormat)
    val tableWriter = CSVWriter.open(tableOutFile.toJava, append = true)(tableFormat)

    val rows: Iterator[Row] = tableReader.iterator
    val fastas: Iterator[FASTA.Value] = parseFastaDropErrors(fastaInFile.lines)

    // NOTE: here we rely on that the sources are prefiltered and don't have duplicate ID
    (rows zip fastas)
      .filter { case (row, fasta) => db.predicate(row, fasta) }
      .foreach { case (row, fasta) =>

        val rowID = row.select(id)
        val fastaID = fasta.getV(header).id

        if (rowID != fastaID)
          sys.error(s"Table row ID [${rowID}] doesn't match FASTA ID [${fastaID}]!")

        val extID = s"${rowID}|lcl|${db.name}"

        tableWriter.writeRow(List(
          extID,
          row.select(tax_id)
        ))

        fastaOutFile.appendLine(
          fasta.update(
            header := FastaHeader(s"${extID} ${fasta.getV(header).description}")
          ).asString
        )
      }

    tableReader.close()
    tableWriter.close()
  }

}
