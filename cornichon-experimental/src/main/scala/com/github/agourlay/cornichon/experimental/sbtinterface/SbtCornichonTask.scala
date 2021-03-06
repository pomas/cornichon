package com.github.agourlay.cornichon.experimental.sbtinterface

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.feature.BaseFeature
import sbt.testing._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.ExecutionContext.Implicits.global

class SbtCornichonTask(task: TaskDef) extends Task {

  override def tags(): Array[String] = Array.empty
  def taskDef(): TaskDef = task

  override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    val p = Promise[Unit]()
    execute(eventHandler, _ ⇒ p.success(()))
    Await.result(p.future, Duration.Inf)
    Array.empty
  }

  def execute(eventHandler: EventHandler, continuation: (Array[Task]) ⇒ Unit): Unit = {

    val c = Class.forName(task.fullyQualifiedName())
    val cons = c.getConstructor()
    val baseFeature = cons.newInstance().asInstanceOf[BaseFeature]
    val featureDef = baseFeature.feature

    println(SuccessLogInstruction(s"${featureDef.name}:", 0).colorized)

    val featResults = featureDef.scenarios.map { s ⇒
      val startTS = System.currentTimeMillis()
      BaseFeature.reserveGlobalRuntime()
      baseFeature.runScenario(s).map { r ⇒
        //Generate result event
        val endTS = System.currentTimeMillis()
        eventHandler.handle(eventBuilder(r, endTS - startTS))
        BaseFeature.releaseGlobalRuntime()
        r
      }
    }

    Future.sequence(featResults)
      .map(_.foreach(printResultLogs))
      .onComplete(_ ⇒ continuation(Array.empty))
  }

  def printResultLogs(sr: ScenarioReport) = sr match {
    case s: SuccessScenarioReport ⇒
      val msg = s"- ${s.scenarioName} "
      println(SuccessLogInstruction(msg, 0).colorized)
      if (s.shouldShowLogs) LogInstruction.printLogs(s.logs)
    case f: FailureScenarioReport ⇒
      val msg = s"- **failed** ${f.scenarioName} "
      println(FailureLogInstruction(msg, 0).colorized)
      LogInstruction.printLogs(f.logs)
    case i: IgnoreScenarioReport ⇒
      val msg = s"- **ignored** ${i.scenarioName} "
      println(WarningLogInstruction(msg, 0).colorized)
    case p: PendingScenarioReport ⇒
      val msg = s"- **pending** ${p.scenarioName} "
      println(DebugLogInstruction(msg, 0).colorized)
  }

  def eventBuilder(sr: ScenarioReport, durationInMillis: Long) = new Event {
    val status = sr match {
      case _: SuccessScenarioReport ⇒ Status.Success
      case _: FailureScenarioReport ⇒ Status.Failure
      case _: IgnoreScenarioReport  ⇒ Status.Ignored
      case _: PendingScenarioReport ⇒ Status.Pending
    }
    val throwable = sr match {
      case f: FailureScenarioReport ⇒
        new OptionalThrowable(new RuntimeException(f.msg))
      case _ ⇒
        new OptionalThrowable()
    }
    val fullyQualifiedName = task.fullyQualifiedName()
    val selector = task.selectors().head
    val fingerprint = task.fingerprint()
    val duration = durationInMillis
  }

}
