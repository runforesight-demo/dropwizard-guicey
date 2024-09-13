package ru.vyarus.dropwizard.guice.examples

import ru.vyarus.dropwizard.guice.test.ClientSupport
import ru.vyarus.dropwizard.guice.test.jupiter.TestDropwizardApp
import spock.lang.Specification

/**
 * @author Vyacheslav Rusakov
 * @since 22.10.2020
 */
@TestDropwizardApp(GspSpaApplication)
class RouteTest extends Specification {

    def "Check route url leads to html page"(ClientSupport client) {

        when: "loading index page"
        def index = client.targetMain('app/').request().get(String)
        then: "index loaded"
        index.contains("<html lang=\"en\">")

        when: "loading route"
        def route = client.targetMain('app/foo').request().accept('text/html').get(String)
        then: "index loaded"
        route == index
    }

    def "Check app2 routing"(ClientSupport client) {

        when: "loading index page"
        def index = client.targetMain('app2/').request().get(String)
        then: "index loaded"
        index.contains("<html lang=\"en\">")

        when: "loading route"
        def route = client.targetMain('app2/foo').request().accept('text/html').get(String)
        then: "index loaded"
        route == index
    }

    def "Check app3 routing"(ClientSupport client) {

        when: "loading index page"
        def index = client.targetMain('app3/').request().get(String)
        then: "index loaded"
        index.contains("<html lang=\"en\">")

        when: "loading route"
        def route = client.targetMain('app3/foo').request().accept('text/html').get(String)
        then: "index loaded"
        route == index
    }
}
