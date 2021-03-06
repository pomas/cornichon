package com.github.agourlay.cornichon.core

import cats.data.Validated.Valid
import cats.data.{ NonEmptyList, ValidatedNel }
import cats.kernel.Semigroup

import scala.concurrent.Future

sealed trait ScenarioReport {
  def isSuccess: Boolean
  def scenarioName: String
  def session: Session
  def logs: Vector[LogInstruction]
}

object ScenarioReport {
  def build(scenarioName: String, runState: RunState, result: ValidatedNel[FailedStep, Done]): ScenarioReport =
    result.fold(
      failedSteps ⇒ FailureScenarioReport(scenarioName, failedSteps, runState.session, runState.logs),
      _ ⇒ SuccessScenarioReport(scenarioName, runState.session, runState.logs)
    )
}

case class SuccessScenarioReport(scenarioName: String, session: Session, logs: Vector[LogInstruction]) extends ScenarioReport {
  val isSuccess = true

  // In case of success, logs are only shown if the scenario contains DebugLogInstruction
  val shouldShowLogs = logs.collect { case d: DebugLogInstruction ⇒ d }.nonEmpty
}

case class IgnoreScenarioReport(scenarioName: String, session: Session) extends ScenarioReport {
  val logs = Vector.empty[LogInstruction]
  val isSuccess = false
}

case class PendingScenarioReport(scenarioName: String, session: Session) extends ScenarioReport {
  val logs = Vector.empty[LogInstruction]
  val isSuccess = false
}

case class FailureScenarioReport(scenarioName: String, failedSteps: NonEmptyList[FailedStep], session: Session, logs: Vector[LogInstruction]) extends ScenarioReport {
  val isSuccess = false

  val msg =
    s"""|Scenario '$scenarioName' failed:
        |${failedSteps.map(_.messageForFailedStep).toList.mkString("\nand\n")}""".stripMargin
}

sealed abstract class Done
case object Done extends Done {
  val rightDone = Right(Done)
  val validDone = Valid(Done)
  val futureDone = Future.successful(Done)
  implicit val semigroup = new Semigroup[Done] {
    def combine(x: Done, y: Done): Done = x
  }
}

case class FailedStep(step: Step, errors: NonEmptyList[CornichonError]) {
  val messageForFailedStep =
    s"""
       |at step:
       |${step.title}
       |
       |with error(s):
       |${errors.map(_.renderedMessage).toList.mkString("\nand\n")}
       |""".stripMargin

}

object FailedStep {
  def fromSingle(step: Step, error: CornichonError) = FailedStep(step, NonEmptyList.of(error))
}