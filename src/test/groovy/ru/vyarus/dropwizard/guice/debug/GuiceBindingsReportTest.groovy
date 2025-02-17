package ru.vyarus.dropwizard.guice.debug

import io.dropwizard.Application
import io.dropwizard.Configuration
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import ru.vyarus.dropwizard.guice.GuiceBundle
import ru.vyarus.dropwizard.guice.test.jupiter.TestGuiceyApp
import spock.lang.Specification

/**
 * @author Vyacheslav Rusakov
 * @since 13.08.2019
 */
@TestGuiceyApp(AIApp)
class GuiceBindingsReportTest extends Specification {

    def "Check guice bindings reporting"() {

        // actual reporting checked manually (test used for reporting configuration)

        expect: "checks that reporting doesn't fail"
        true

    }

    static class AIApp extends Application<Configuration> {

        @Override
        void initialize(Bootstrap<Configuration> bootstrap) {
            bootstrap.addBundle(GuiceBundle.builder()
                    .printAllGuiceBindings()
                    .build())
        }

        @Override
        void run(Configuration configuration, Environment environment) throws Exception {

        }
    }
}
