package com.github.agourlay.cornichon.core

import cats.Show
import cats.syntax.show._
import cats.syntax.monoid._
import cats.instances.string._
import cats.instances.map._
import cats.instances.vector._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import cats.kernel.Monoid
import com.github.agourlay.cornichon.core.Session._
import com.github.agourlay.cornichon.json.{ JsonPath, NotStringFieldError }
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.util.Strings
import io.circe.Json

import scala.collection.immutable.HashMap

case class Session(private val content: Map[String, Vector[String]]) extends AnyVal {

  def getOpt(key: String, stackingIndice: Option[Int] = None): Option[String] =
    get(key, stackingIndice).toOption

  def getUnsafe(key: String, stackingIndice: Option[Int] = None): String =
    get(key, stackingIndice).fold(ce ⇒ throw ce.toException, identity)

  def get(key: String, stackingIndice: Option[Int] = None): Either[CornichonError, String] =
    for {
      values ← content.get(key).toRight(KeyNotFoundInSession(key, this))
      indice = stackingIndice.getOrElse(values.size - 1)
      value ← values.lift(indice).toRight(IndiceNotFoundForKey(key, indice, values))
    } yield value

  def get(sessionKey: SessionKey): Either[CornichonError, String] = get(sessionKey.name, sessionKey.index)

  def getJson(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root): Either[CornichonError, Json] =
    for {
      sessionValue ← get(key, stackingIndice)
      jsonValue ← parseJson(sessionValue)
      extracted ← JsonPath.run(path, jsonValue)
    } yield extracted

  def getJsonStringField(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root) =
    for {
      json ← getJson(key, stackingIndice, path)
      field ← Either.fromOption(json.asString, NotStringFieldError(json, path))
    } yield field

  def getJsonStringFieldUnsafe(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root) =
    getJsonStringField(key, stackingIndice, path).fold(ce ⇒ throw ce.toException, identity)

  def getJsonOpt(key: String, stackingIndice: Option[Int] = None): Option[Json] = getOpt(key, stackingIndice).flatMap(s ⇒ parseJson(s).toOption)

  def getList(keys: Seq[String]): Either[CornichonError, List[String]] = keys.toList.traverseU(v ⇒ get(v))

  def getHistory(key: String) = content.get(key).toRight(KeyNotFoundInSession(key, this))

  //FIXME turning this into a proper CornichonError creates a lot of work to handle the Either
  def addValue(key: String, value: String) = {
    val trimmedKey = key.trim
    if (trimmedKey.isEmpty)
      throw EmptyKey(this).toException
    else if (Session.notAllowedInKey.exists(forbidden ⇒ trimmedKey.contains(forbidden)))
      throw IllegalKey(trimmedKey).toException
    else
      content.get(key).fold(Session(content + (key → Vector(value)))) { values ⇒
        Session((content - key) + (key → values.:+(value)))
      }
  }

  def addValues(tuples: (String, String)*) = tuples.foldLeft(this)((s, t) ⇒ s.addValue(t._1, t._2))

  def removeKey(key: String) = Session(content - key)

  def rollbackKey(key: String) =
    getHistory(key).map { values ⇒
      val s = Session(content - key)
      val previous = values.init
      if (previous.isEmpty)
        s
      else
        s.copy(content = s.content + (key -> previous))
    }

}

object Session {
  val newEmpty = Session(HashMap.empty)

  val notAllowedInKey = "\r\n<>/[] "

  implicit val monoidSession = new Monoid[Session] {
    def empty: Session = newEmpty
    def combine(x: Session, y: Session): Session =
      Session(x.content.combine(y.content))
  }

  implicit val showSession = Show.show[Session] { s ⇒
    s.content.toSeq
      .sortBy(_._1)
      .map(pair ⇒ pair._1 + " -> " + pair._2.toIterator.map(_.show).mkString("Values(", ", ", ")"))
      .mkString("\n")
  }

  // In companion object to access 'content'
  case class KeyNotFoundInSession(key: String, s: Session) extends CornichonError {
    val similarKeysMsg = {
      val similar = s.content.keys.filter(Strings.levenshtein(_, key) == 1)
      if (similar.isEmpty)
        ""
      else
        s"maybe you meant ${similar.map(s ⇒ s"'$s'").mkString(" or ")}"
    }
    val baseErrorMessage = s"key '$key' can not be found in session $similarKeysMsg \n${s.show}"
  }
}

case class SessionKey(name: String, index: Option[Int] = None) {
  def atIndex(index: Int) = copy(index = Some(index))
}

case class EmptyKey(s: Session) extends CornichonError {
  val baseErrorMessage = s"key can not be empty - session is \n${s.show}"
}

case class IllegalKey(key: String) extends CornichonError {
  val baseErrorMessage = s"error for key '$key' - session key can not contain chars '${Session.notAllowedInKey}'"
}

case class IndiceNotFoundForKey(key: String, indice: Int, values: Vector[String]) extends CornichonError {
  val baseErrorMessage = s"indice '$indice' not found for key '$key' with values \n" +
    s"${values.zipWithIndex.map { case (v, i) ⇒ s"$i -> $v" }.mkString("\n")}"
}