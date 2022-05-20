package ru.vyarus.dropwizard.guice.test.jupiter.ext;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.dropwizard.Application;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.commons.support.AnnotationSupport;
import ru.vyarus.dropwizard.guice.hook.GuiceyConfigurationHook;
import ru.vyarus.dropwizard.guice.module.installer.util.PathUtils;
import ru.vyarus.dropwizard.guice.test.jupiter.TestDropwizardApp;
import ru.vyarus.dropwizard.guice.test.jupiter.env.TestEnvironmentSetup;
import ru.vyarus.dropwizard.guice.test.jupiter.ext.conf.ExtensionBuilder;
import ru.vyarus.dropwizard.guice.test.jupiter.ext.conf.ExtensionConfig;
import ru.vyarus.dropwizard.guice.test.util.ConfigOverrideUtils;
import ru.vyarus.dropwizard.guice.test.util.ConfigurablePrefix;
import ru.vyarus.dropwizard.guice.test.util.HooksUtils;
import ru.vyarus.dropwizard.guice.test.util.RandomPortsListener;
import ru.vyarus.dropwizard.guice.test.util.RegistrationTrackUtils;
import ru.vyarus.dropwizard.guice.test.util.TestSetupUtils;

import java.util.Collections;
import java.util.List;

/**
 * {@link TestDropwizardApp} junit 5 extension implementation. Normally, extension should be activated with annotation,
 * but in some cases manual registration may be used:
 * <pre>{@code @RegisterExtension
 * static TestDropwizardAppExtension app = TestDropwizardAppExtension.forApp(MyApp.class).create()
 * }</pre>
 * This is complete equivalent of annotation declaration! Static modifier is important! There is no additional
 * methods in extension (intentionally), so registration type changes nothing in usage.
 * <p>
 * Reasons why it could be used instead of annotation:
 * <ul>
 *     <li>Incorrect execution order with some other extensions. Manually registered extension will execute
 *     after(!) all class level registrations (junit native behaviour). So moving guicey extension to manual
 *     registration may guarantee its execution after some other extension.</li>
 *     <li>Manual registration allows short hook declarations with lambdas:
 *     {@code .hooks(builder -> builder.modules(new DebugGuiceModule()))}</li>
 *     <li>Config overrides registration as {@link ConfigOverride} objects (required for delayed evaluated values:
 *     e.g. when it is obtained from some other junit extension)</li>
 * </ul>
 * <p>
 * You can't use manual registration to configure multiple applications because junit allows only one extension
 * instance (if you really need to use multiple applications in tests then register one with extension and for
 * another use {@link DropwizardTestSupport} directly).
 * <p>
 * If both declarations will be used at the same class (don't do that!) then annotation will win and manual
 * registration will be ignored (junit default behaviour).
 * <p>
 * Other extensions requiring access to dropwizard application may use
 * {@link GuiceyExtensionsSupport#lookupSupport(ExtensionContext)}.
 *
 * @author Vyacheslav Rusakov
 * @since 28.04.2020
 */
public class TestDropwizardAppExtension extends GuiceyExtensionsSupport {

    private static final String STAR = "*";

    private Config config;

    public TestDropwizardAppExtension() {
        // for usage with annotation
    }

    private TestDropwizardAppExtension(final Config config) {
        this.config = config;
    }

    /**
     * Builder for manual extension registration with {@link RegisterExtension}. Provides the same configuration
     * options as {@link TestDropwizardApp} annotation (annotation considered as preferred usage way).
     * <p>
     * IMPORTANT: extension must be used with static field only! You can't register multiple extensions!
     * <p>
     * This is just a different way of extension configuration! Resulted extension object does not provide any
     * additional methods (and not intended to be used at all)!
     * <p>
     * Pure {@link DropwizardTestSupport} provides an ability to register custom {@link io.dropwizard.lifecycle.Managed}
     * or listener {@link DropwizardTestSupport#addListener(DropwizardTestSupport.ServiceListener)}. If you need these
     * then use {@link Builder#hooks(GuiceyConfigurationHook...)} to register additional managed object or
     * additional dropwizard or guicey bundle (which will be the same as listener above).
     *
     * @param app application class
     * @return builder for extension configuration.
     */
    public static Builder forApp(final Class<? extends Application> app) {
        return new Builder(app);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected DropwizardTestSupport<?> prepareTestSupport(final ExtensionContext context) {
        if (config == null) {
            // Configure from annotation
            // Note that it is impossible to have both manually build config and annotation because annotation
            // will be processed first and manual registration will be simply ignored

            final TestDropwizardApp ann = AnnotationSupport
                    // also search annotation inside other annotations (meta)
                    .findAnnotation(context.getElement(), TestDropwizardApp.class).orElse(null);

            // catch incorrect usage by direct @ExtendWith(...)
            Preconditions.checkNotNull(ann, "%s annotation not declared: can't work without configuration, "
                            + "so either use annotation or extension with @%s for manual configuration",
                    TestDropwizardApp.class.getSimpleName(),
                    RegisterExtension.class.getSimpleName());

            config = Config.parse(ann);
        }

        TestSetupUtils.findAndProcessSetupObjects(config, context);
        HooksUtils.register(config.hooks);

        // config overrides work through system properties so it is important to have unique prefixes
        final String configPrefix = ConfigOverrideUtils.createPrefix(context.getRequiredTestClass());
        final DropwizardTestSupport support = new DropwizardTestSupport(config.app,
                config.configPath,
                configPrefix,
                buildConfigOverrides(configPrefix, context));

        if (config.randomPorts) {
            support.addListener(new RandomPortsListener());
        }
        return support;
    }

    @SuppressWarnings("unchecked")
    private <T extends ConfigOverride & ConfigurablePrefix> ConfigOverride[] buildConfigOverrides(
            final String prefix, final ExtensionContext context) {
        ConfigOverride[] overrides = ConfigOverrideUtils.convert(prefix, config.configOverrides);
        if (!Strings.isNullOrEmpty(config.restMapping)) {
            String mapping = PathUtils.leadingSlash(config.restMapping);
            if (!mapping.endsWith(STAR)) {
                mapping = PathUtils.trailingSlash(mapping) + STAR;
            }
            overrides = ConfigOverrideUtils.merge(overrides, ConfigOverride.config(prefix, "server.rootPath", mapping));
        }
        return config.configOverrideObjects.isEmpty() ? overrides
                : ConfigOverrideUtils.merge(overrides,
                ConfigOverrideUtils.prepareExtensionOverrides(
                        ConfigOverrideUtils.prepareOverrides(prefix, (List<T>) (List<?>) config.configOverrideObjects),
                        context
                ));
    }


    /**
     * Builder used for manual extension registration ({@link #forApp(Class)}).
     */
    public static class Builder extends ExtensionBuilder<Builder, Config> {

        public Builder(final Class<? extends Application> app) {
            super(new Config());
            this.cfg.app = Preconditions.checkNotNull(app, "Application class must be provided");
        }

        /**
         * Same as {@link TestDropwizardApp#config()}.
         *
         * @param configPath configuration file path
         * @return builder instance for chained calls
         */
        public Builder config(final String configPath) {
            cfg.configPath = configPath;
            return this;
        }

        /**
         * Same as {@link TestDropwizardApp#randomPorts()}.
         *
         * @param randomPorts true to use random ports
         * @return builder instance for chained calls
         */
        public Builder randomPorts(final boolean randomPorts) {
            cfg.randomPorts = randomPorts;
            return this;
        }

        /**
         * Shortcut for {@link #randomPorts(boolean)}.
         *
         * @return builder instance for chained calls
         */
        public Builder randomPorts() {
            return randomPorts(true);
        }

        /**
         * Same as {@link TestDropwizardApp#restMapping()}.
         *
         * @param mapping rest mapping path
         * @return builder instance for chained calls
         */
        public Builder restMapping(final String mapping) {
            cfg.restMapping = mapping;
            return this;
        }

        /**
         * Environment support object is the simplest way to prepare additional objects for test
         * (like database) and apply configuration overrides. Provided classes would be instantiated with the
         * default constructor.
         * <p>
         * To avoid confusion with guicey hooks: setup object required to prepare test environment before test (and
         * apply required configurations) whereas hooks is a general mechanism for application customization (not only
         * in tests).
         * <p>
         * Anonymous implementation could be simply declared as static field:
         * {@code @EnableSupport static TestEnvironmentSupport ext = ext -> ext.configOverrides("foo:1")}
         * All such fields will be detected automatically and objects registered. Fields declared in base test classes
         * are also counted.
         *
         * @param support support object classes
         * @return builder instance for chained calls
         */
        @SafeVarargs
        public final Builder setup(final Class<? extends TestEnvironmentSetup>... support) {
            cfg.extensionsClasses(support);
            return this;
        }

        /**
         * Environment support objects is the simplest mechanism to prepare additional objects for test
         * (like database) and apply configuration overrides.
         * <p>
         * To avoid confusion with guicey hooks: setup object required to prepare test environment before test (and
         * apply required configurations) whereas hooks is a general mechanism for application customization (not only
         * in tests).
         * <p>
         * Anonymous implementation could be simply declared as static field:
         * {@code @EnableSupport static TestEnvironmentSupport ext =
         * ext -> ext.configOverrides("foo:1")}
         * All such fields will be detected automatically and objects registered. Fields declared in base test classes
         * are also counted.
         *
         * @param support support object instances
         * @return builder instance for chained calls
         */
        public Builder setup(final TestEnvironmentSetup... support) {
            cfg.extensionInstances(support);
            return this;
        }

        /**
         * Creates extension.
         * <p>
         * Note that extension must be assigned to static field! Extension instance does not provide additional
         * methods so use field and parameter injections as with annotation extension declaration.
         *
         * @return extension instance
         */
        public TestDropwizardAppExtension create() {
            return new TestDropwizardAppExtension(cfg);
        }
    }

    /**
     * Unified configuration.
     */
    @SuppressWarnings({"checkstyle:VisibilityModifier", "PMD.DefaultPackage"})
    private static class Config extends ExtensionConfig {
        Class<? extends Application> app;
        String configPath = "";
        boolean randomPorts;
        String restMapping = "";

        final void extensionInstances(final TestEnvironmentSetup... exts) {
            Collections.addAll(extensions, exts);
            // track
            RegistrationTrackUtils.fromInstance(extensionsSource, String.format("@%s %s instance",
                    RegisterExtension.class.getSimpleName(),
                    TestDropwizardAppExtension.class.getSimpleName()), exts);
        }

        @SafeVarargs
        final void extensionsClasses(final Class<? extends TestEnvironmentSetup>... exts) {
            extensions.addAll(TestSetupUtils.create(exts));
            // track
            RegistrationTrackUtils.fromClass(extensionsSource, String.format("@%s %s class",
                    RegisterExtension.class.getSimpleName(),
                    TestDropwizardAppExtension.class.getSimpleName()), exts);
        }

        /**
         * Converts annotation to unified configuration object.
         *
         * @param ann configuration annotation
         * @return configuration instance
         */
        static Config parse(final TestDropwizardApp ann) {
            final Config res = new Config();
            res.app = ann.value();
            res.configPath = ann.config();
            res.configOverrides = ann.configOverride();
            res.hooks = HooksUtils.create(ann.hooks());
            res.randomPorts = ann.randomPorts();
            res.restMapping = ann.restMapping();
            res.extensionsFromAnnotation(ann.annotationType(), ann.setup());
            return res;
        }
    }
}
