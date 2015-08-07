package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature
import spray.json._

class SessionHttpExamplesSpec extends CornichonFeature with ExampleServer {

  lazy val feat =
    feature("Session DSL") {
      scenario("Playing with the http DSL")(

        // Simple GET
        When(GET(s"$baseUrl/superheroes/Batman")),

        // Test status of previous request
        Then(status_is(200)),

        // Test body of previous request body as Json
        Then(response_body_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
        """.parseJson)),

        // Set a key/value in the Scenario's session
        Given(Set("favorite-superhero" -> "Batman")),

        // Retrieve dynamically from session with <key> for URL construction
        When(GET(s"$baseUrl/superheroes/<favorite-superhero>", expectedBody =
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
        """.parseJson
        )),

        // To make debugging easier, here are some debug steps printing into console
        Then(showSession),
        Then(showLastStatus),
        Then(showLastResponseJson)
      )
    }
}