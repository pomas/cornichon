package com.github.agourlay.cornichon.core

import cats.data.Xor
import com.github.agourlay.cornichon.core.Feature.FeatureDef

trait Feature {

  val resolver = new Resolver
  val engine = new Engine(resolver)
  private val session = Session.newSession

  val feat: FeatureDef

  def runFeature(): FeatureReport = {
    val scenarioReports = feat.scenarios.map(engine.runScenario(_)(session))
    val (failedReport, successReport) = scenarioReports.partition(_.isLeft)
    if (failedReport.isEmpty)
      SuccessFeatureReport(successReport.collect { case Xor.Right(sr) ⇒ sr })
    else
      FailedFeatureReport(successReport.collect { case Xor.Right(sr) ⇒ sr }, failedReport.collect { case Xor.Left(fr) ⇒ fr })
  }

  def failedFeatureErrorMsg(report: FailedFeatureReport): Seq[String] = {
    report.failedScenariosResult.map { r ⇒
      s"""
         |Scenario "${r.scenarioName}" failed
         |at step "${r.failedStep.step}"
         |with error "${r.failedStep.error.msg}"
         | """.trim.stripMargin
    }
  }
}

object Feature {
  case class FeatureDef(name: String, scenarios: Seq[Scenario])
}