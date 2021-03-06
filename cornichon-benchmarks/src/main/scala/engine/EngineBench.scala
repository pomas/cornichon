package engine

import java.util.concurrent.{ ExecutorService, Executors }

import cats.instances.int._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ Engine, Scenario, Session }
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import org.openjdk.jmh.annotations._
import engine.EngineBench._
import monix.execution.Scheduler

import scala.concurrent.Await
import scala.concurrent.duration._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 20)
@Measurement(iterations = 20)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+UnlockCommercialFeatures",
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=duration=60s,filename=./profiling-data.jfr,name=profile,settings=profile",
  "-XX:FlightRecorderOptions=settings=/Library/Java/JavaVirtualMachines/jdk1.8.0_144.jdk/Contents/Home/jre/lib/jfr/profile.jfc,samplethreads=true"))
class EngineBench {

  var es: ExecutorService = _
  var engine: Engine = _
  @Param(Array("10", "20", "50", "100", "200"))
  var stepsNumber: String = _

  @Setup(Level.Trial)
  final def beforeAll(): Unit = {
    println("")
    println("Creating Engine...")
    val resolver = PlaceholderResolver.withoutExtractor()
    es = Executors.newFixedThreadPool(1)
    val scheduler = Scheduler(es)
    engine = Engine.withStepTitleResolver(resolver)(scheduler)
  }

  @TearDown(Level.Trial)
  final def afterAll(): Unit = {
    println("")
    println("Shutting down ExecutionContext...")
    es.shutdown()
  }

  //    [info] Benchmark                (stepsNumber)   Mode  Cnt       Score     Error   Units
  //    [info] EngineBench.lotsOfSteps             10  thrpt   20  130454.985 ± 7743.991  ops/s
  //    [info] EngineBench.lotsOfSteps             20  thrpt   20   79174.242 ± 2540.647  ops/s
  //    [info] EngineBench.lotsOfSteps             50  thrpt   20   35998.823 ±  734.948  ops/s
  //    [info] EngineBench.lotsOfSteps            100  thrpt   20   18788.477 ±  342.522  ops/s
  //    [info] EngineBench.lotsOfSteps            200  thrpt   20    9086.221 ±  414.376  ops/s
  @Benchmark
  def lotsOfSteps() = {
    val assertSteps = List.fill(stepsNumber.toInt / 2)(assertStep)
    val effectSteps = List.fill(stepsNumber.toInt / 2)(effectStep)
    val scenario = Scenario("test scenario", setupSession +: (assertSteps ++ effectSteps))
    val f = engine.runScenario(Session.newEmpty)(scenario)
    val res = Await.result(f, Duration.Inf)
    assert(res.isSuccess)
  }
}

object EngineBench {
  val setupSession = EffectStep.fromSync("setup session", s ⇒ s.addValues("v1" -> "2", "v2" -> "1"))
  val assertStep = AssertStep(
    "addition step",
    s ⇒ Assertion.either {
      for {
        two ← s.get("v1").map(_.toInt)
        one ← s.get("v2").map(_.toInt)
      } yield GenericEqualityAssertion(two + one, 3)
    })
  val effectStep = EffectStep.fromSync("identity", s ⇒ s)
}
