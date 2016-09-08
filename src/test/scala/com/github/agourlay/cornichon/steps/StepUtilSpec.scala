package com.github.agourlay.cornichon.steps

import com.github.agourlay.cornichon.core.Engine
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.util.ShowInstances

import scala.concurrent.ExecutionContext

trait StepUtilSpec extends ShowInstances {

  val ec = ExecutionContext.global
  val resolver = Resolver.withoutExtractor()
  val engine = Engine.withStepTitleResolver(resolver, ec)

}