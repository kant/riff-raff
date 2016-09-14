package magenta.graph

import com.amazonaws.services.s3.AmazonS3Client
import magenta.{Host, KeyRing}
import magenta.tasks.{HealthcheckGrace, S3Upload, SayHello}
import org.scalatest.{FlatSpec, ShouldMatchers}
import org.scalatest.mock.MockitoSugar

class DeploymentGraphTest extends FlatSpec with ShouldMatchers with MockitoSugar {
  implicit val fakeKeyRing = KeyRing()
  implicit val artifactClient = mock[AmazonS3Client]

  "DeploymentGraph" should "Convert a list of tasks to a graph" in {
    val graph = DeploymentGraph(threeSimpleTasks, "unnamed")
    graph.nodes.size should be(3)
    graph.edges.size should be(2)
    graph.nodes.filterMidNodes.head.value.tasks should be(threeSimpleTasks)
    DeploymentGraph.toTaskList(graph) should be(threeSimpleTasks)
  }

  val threeSimpleTasks = List(
    S3Upload("test-bucket", Seq()),
    SayHello(Host("testHost")),
    HealthcheckGrace(1000)
  )

}