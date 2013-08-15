package magenta.tasks

import magenta.{ApiCredentials, MessageBroker, KeyRing, Package}
import com.gu.FastlyAPIClient
import java.io.File
import net.liftweb.json._

case class UpdateFastlyConfig(pkg: Package) extends Task {

  implicit val formats = DefaultFormats

  override def execute(keyRing: KeyRing, stopFlag: => Boolean) {

    FastlyApiClientProvider.get(keyRing).map {
      client =>

        val activeVersionNumber = getActiveVersionNumber(client, stopFlag)
        val nextVersionNumber = clone(activeVersionNumber, client, stopFlag)
        deleteAllVclFilesFrom(nextVersionNumber, client, stopFlag)
        uploadNewVclFilesTo(nextVersionNumber, pkg.srcDir, client, stopFlag)
        activateVersion(nextVersionNumber, client, stopFlag)
    }
  }

  private def getActiveVersionNumber(client: FastlyAPIClient, stopFlag: => Boolean): Int = {
    if (!stopFlag) {
      val versionsJson = client.versions().get.getResponseBody
      val versions = parse(versionsJson).extract[List[Version]]
      val activeVersion = versions.filter(x => x.active.getOrElse(false) == true)(0)
      MessageBroker.info("Current activate version %d".format(activeVersion.number))
      activeVersion.number
    } else {
      -1
    }
  }

  private def clone(versionNumber: Int, client: FastlyAPIClient, stopFlag: => Boolean): Int = {
    if (!stopFlag) {
      val cloned = client.versionClone(versionNumber).get.getResponseBody

      val clonedVersion = parse(cloned).extract[Version]
      MessageBroker.info("Cloned version %d".format(clonedVersion.number))
      clonedVersion.number
    } else {
      -1
    }
  }

  private def deleteAllVclFilesFrom(versionNumber: Int, client: FastlyAPIClient, stopFlag: => Boolean) = {
    if (!stopFlag) {
      val vclListJson = client.vclList(versionNumber).get.getResponseBody

      val vclFilesToDelete = parse(vclListJson).extract[List[Vcl]]
      vclFilesToDelete.foreach {
        file =>
          MessageBroker.info("Deleting %s".format(file.name))
          client.vclDelete(versionNumber, file.name).get.getResponseBody
      }
    }
  }

  private def uploadNewVclFilesTo(versionNumber: Int, srcDir: File, client: FastlyAPIClient, stopFlag: => Boolean) = {
    if (!stopFlag) {
      val vclFilesToUpload = srcDir.listFiles().toList
      vclFilesToUpload.foreach {
        file =>
          if (file.getName.endsWith(".vcl")) {
            MessageBroker.info("Uploading %s".format(file.getName))
            val vcl = scala.io.Source.fromFile(file.getAbsolutePath).mkString
            client.vclUpload(versionNumber, vcl, file.getName, file.getName)
          }
      }
      client.vclSetAsMain(versionNumber, "main.vcl").get
    }
  }

  private def activateVersion(versionNumber: Int, client: FastlyAPIClient, stopFlag: => Boolean) = {
    if (validateNewConfigFor(versionNumber, client, stopFlag)) {
      client.versionActivate(versionNumber).get
      MessageBroker.info("Fastly version %s is now active".format(versionNumber))
    } else {
      MessageBroker.fail("Error validating Fastly version %s".format(versionNumber))
    }
  }

  private def validateNewConfigFor(versionNumber: Int, client: FastlyAPIClient, stopFlag: => Boolean): Boolean = {
    if (!stopFlag) {

      MessageBroker.info("Waiting 5 seconds for the VCL to compile".format(versionNumber))
      Thread.sleep(5000)

      MessageBroker.info("Validating new congif %s".format(versionNumber))
      val response = client.versionValidate(versionNumber).get
      val validationResponse = parse(response.getResponseBody) \\ "status"
      validationResponse == JString("ok")
    } else {
      false
    }
  }

  override def description: String = "Update configuration of Fastly edge-caching service"

  override def verbose: String = description

}

case class Version(number: Int, active: Option[Boolean])

case class Vcl(name: String)

object FastlyApiClientProvider {

  private var fastlyApiClients = Map[String, FastlyAPIClient]()

  def get(keyRing: KeyRing): Option[FastlyAPIClient] = {

    keyRing.apiCredentials.get("fastly").map { credentials =>
        val serviceId = credentials.id
        val apiKey = credentials.secret

        if (fastlyApiClients.get(serviceId).isEmpty) {
          this.fastlyApiClients += (serviceId -> new FastlyAPIClient(apiKey, serviceId))
        }
        return fastlyApiClients.get(serviceId)
    }

    None
  }
}
