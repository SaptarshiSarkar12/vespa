// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.jdisc.FilterBindingsProvider;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.DefaultSslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.DefaultSslTrustStoreConfigurator;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.JettyHttpServer;

import com.yahoo.vespa.model.container.http.ssl.DefaultSslProvider;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author einarmr
 * @author mortent
 */
public class JettyContainerModelBuilderTest extends ContainerModelBuilderTestBase {

    @Test
    public void verify_that_overriding_connector_options_works() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0' jetty='true'>\n" +
                "  <http>\n" +
                "    <server id='bananarama' port='4321'>\n" +
                "      <config name='jdisc.http.connector'>\n" +
                "        <requestHeaderSize>300000</requestHeaderSize>\n" +
                "        <headerCacheSize>300000</headerCacheSize>\n" +
                "      </config>\n" +
                "    </server>\n" +
                "  </http>\n" +
                nodesXml +
                "</jdisc>\n"
        );
        createModel(root, clusterElem);
        ConnectorConfig.Builder connectorConfigBuilder = new ConnectorConfig.Builder();
        ConnectorConfig cfg = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/bananarama");
        assertThat(cfg.requestHeaderSize(), is(300000));
        assertThat(cfg.headerCacheSize(), is(300000));
    }

    @Test
    public void verify_that_enabling_jetty_works() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0' jetty='true'>" +
                        nodesXml +
                        "</jdisc>"
        );
        createModel(root, clusterElem);
        assertJettyServerInConfig();
    }

    @Test
    public void verify_that_enabling_jetty_works_for_custom_http_servers() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0' jetty='true'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                nodesXml,
                "</jdisc>" );
        createModel(root, clusterElem);
        assertJettyServerInConfig();
    }

    @Test
    public void verifyThatJettyHttpServerHasFilterBindingsProvider() throws Exception {
        final Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0' jetty='true'>",
                nodesXml,
                "</jdisc>" );
        createModel(root, clusterElem);

        final ComponentsConfig.Components jettyHttpServerComponent = extractComponentByClassName(
                containerComponentsConfig(), com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName());
        assertThat(jettyHttpServerComponent, is(not(nullValue())));

        final ComponentsConfig.Components filterBindingsProviderComponent = extractComponentByClassName(
                containerComponentsConfig(), FilterBindingsProvider.class.getName());
        assertThat(filterBindingsProviderComponent, is(not(nullValue())));

        final ComponentsConfig.Components.Inject filterBindingsProviderInjection = extractInjectionById(
                jettyHttpServerComponent, filterBindingsProviderComponent.id());
        assertThat(filterBindingsProviderInjection, is(not(nullValue())));
    }

    @Test
    public void verifyThatJettyHttpServerHasFilterBindingsProviderForCustomHttpServers() throws Exception {
        final Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0' jetty='true'>",
                "  <http>",
                "    <server port='9000' id='foo' />",
                "  </http>",
                nodesXml,
                "</jdisc>" );
        createModel(root, clusterElem);

        final ComponentsConfig.Components jettyHttpServerComponent = extractComponentByClassName(
                clusterComponentsConfig(), com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName());
        assertThat(jettyHttpServerComponent, is(not(nullValue())));

        final ComponentsConfig.Components filterBindingsProviderComponent = extractComponentByClassName(
                clusterComponentsConfig(), FilterBindingsProvider.class.getName());
        assertThat(filterBindingsProviderComponent, is(not(nullValue())));

        final ComponentsConfig.Components.Inject filterBindingsProviderInjection = extractInjectionById(
                jettyHttpServerComponent, filterBindingsProviderComponent.id());
        assertThat(filterBindingsProviderInjection, is(not(nullValue())));
    }

    @Test
    public void verify_that_old_http_config_override_inside_server_tag_works() throws Exception {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0' jetty='true'>",
                "  <http>",
                "    <server port='9000' id='foo'>",
                "      <config name=\"container.jdisc.config.http-server\">",
                "        <tcpKeepAliveEnabled>true</tcpKeepAliveEnabled>",
                "        <tcpNoDelayEnabled>false</tcpNoDelayEnabled>",
                "        <tcpListenBacklogLength>2</tcpListenBacklogLength>",
                "        <idleConnectionTimeout>34.1</idleConnectionTimeout>",
                "        <soLinger>42.2</soLinger>",
                "        <sendBufferSize>1234</sendBufferSize>",
                "        <maxHeaderSize>4321</maxHeaderSize>",
                "        <ssl>",
                "          <enabled>true</enabled>",
                "          <keyStoreType>JKS</keyStoreType>",
                "          <keyStorePath>apple</keyStorePath>",
                "          <trustStorePath>grape</trustStorePath>",
                "          <keyDBKey>tomato</keyDBKey>",
                "          <algorithm>onion</algorithm>",
                "          <protocol>carrot</protocol>",
                "        </ssl>",
                "      </config>",
                "    </server>",
                "  </http>",
                nodesXml,
                "</jdisc>" );
        createModel(root, clusterElem);
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        List<JettyHttpServer> jettyServers = cluster.getChildrenByTypeRecursive(JettyHttpServer.class);

        assertThat(jettyServers.size(), is(1));

        JettyHttpServer server = jettyServers.get(0);
        assertThat(server.model.bundleInstantiationSpec.classId.toString(),
                is(com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName()));
        assertThat(server.model.bundleInstantiationSpec.bundle.toString(), is("jdisc_http_service"));
        assertThat(server.getConnectorFactories().size(), is(1));

        ConnectorConfig.Builder connectorConfigBuilder = new ConnectorConfig.Builder();
        server.getConnectorFactories().get(0).getConfig(connectorConfigBuilder);
        ConnectorConfig connector = new ConnectorConfig(connectorConfigBuilder);
        assertThat(connector.name(), equalTo("foo"));
        assertThat(connector.tcpKeepAliveEnabled(), equalTo(true));
        assertThat(connector.tcpNoDelay(), equalTo(false));
        assertThat(connector.acceptQueueSize(), equalTo(2));
        assertThat(connector.idleTimeout(), equalTo(34.1));
        assertThat(connector.soLingerTime(), equalTo(42.2));
        assertThat(connector.outputBufferSize(), equalTo(1234));
        assertThat(connector.headerCacheSize(), equalTo(4321));
        assertThat(connector.ssl().enabled(), equalTo(true));
        assertThat(connector.ssl().keyStoreType(), equalTo(KeyStoreType.Enum.JKS));
        assertThat(connector.ssl().keyStorePath(), equalTo("apple"));
        assertThat(connector.ssl().trustStorePath(), equalTo("grape"));
        assertThat(connector.ssl().keyDbKey(), equalTo("tomato"));
        assertThat(connector.ssl().sslKeyManagerFactoryAlgorithm(), equalTo("onion"));
        assertThat(connector.ssl().protocol(), equalTo("carrot"));

        assertThat(
                extractComponentByClassName(
                        clusterComponentsConfig(),
                        com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName()),
                is(not(nullValue())));
    }

    @Test
    public void ssl_keystore_and_truststore_configurator_can_be_overriden() throws IOException, SAXException {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0' jetty='true'>",
                "  <http>",
                "    <server port='9000' id='foo'>",
                "      <ssl-keystore-configurator class='com.yahoo.MySslKeyStoreConfigurator' bundle='mybundle'/>",
                "      <ssl-truststore-configurator class='com.yahoo.MySslTrustStoreConfigurator' bundle='mybundle'/>",
                "    </server>",
                "    <server port='9001' id='bar'/>",
                "  </http>",
                nodesXml,
                "</jdisc>");
        createModel(root, clusterElem);
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        List<ConnectorFactory> connectorFactories = cluster.getChildrenByTypeRecursive(ConnectorFactory.class);
        {
            ConnectorFactory firstConnector = connectorFactories.get(0);
            assertConnectorHasInjectedComponents(firstConnector, "ssl-keystore-configurator@foo", "ssl-truststore-configurator@foo", "dummy-ssl-provider@foo");
            assertComponentHasClassNameAndBundle(getChildComponent(firstConnector, 0),
                                                 "com.yahoo.MySslKeyStoreConfigurator",
                                                 "mybundle");
            assertComponentHasClassNameAndBundle(getChildComponent(firstConnector, 1),
                                                 "com.yahoo.MySslTrustStoreConfigurator",
                                                 "mybundle");
        }
        {
            ConnectorFactory secondConnector = connectorFactories.get(1);
            assertConnectorHasInjectedComponents(secondConnector, "ssl-keystore-configurator@bar", "ssl-truststore-configurator@bar", "dummy-ssl-provider@bar");
            assertComponentHasClassNameAndBundle(getChildComponent(secondConnector, 0),
                                                 DefaultSslKeyStoreConfigurator.class.getName(),
                                                 "jdisc_http_service");
            assertComponentHasClassNameAndBundle(getChildComponent(secondConnector, 1),
                                                 DefaultSslTrustStoreConfigurator.class.getName(),
                                                 "jdisc_http_service");
        }
    }

    @Test
    public void verify_that_ssl_element_generates_connector_config_and_inject_provider_component() {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0' jetty='true'>",
                "    <http>",
                "        <server port='9000' id='minimal'>",
                "            <ssl>",
                "                <private-key-file>/foo/key</private-key-file>",
                "                <certificate-file>/foo/cert</certificate-file>",
                "            </ssl>",
                "        </server>",
                "        <server port='9001' id='with-cacerts'>",
                "            <ssl>",
                "                <private-key-file>/foo/key</private-key-file>",
                "                <certificate-file>/foo/cert</certificate-file>",
                "                <ca-certificates-file>/foo/cacerts</ca-certificates-file>",
                "            </ssl>",
                "        </server>",
                "        <server port='9002' id='need-client-auth'>",
                "            <ssl>",
                "                <private-key-file>/foo/key</private-key-file>",
                "                <certificate-file>/foo/cert</certificate-file>",
                "                <client-authentication>need</client-authentication>",
                "            </ssl>",
                "        </server>",
                "    </http>",
                nodesXml,
                "",
                "</jdisc>");

        createModel(root, clusterElem);
        ConnectorConfig minimalCfg = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/minimal/default-ssl-provider@minimal");
        assertTrue(minimalCfg.ssl().enabled());
        assertThat(minimalCfg.ssl().privateKeyFile(), is(equalTo("/foo/key")));
        assertThat(minimalCfg.ssl().certificateFile(), is(equalTo("/foo/cert")));
        assertThat(minimalCfg.ssl().caCertificateFile(), is(equalTo("")));
        assertThat(minimalCfg.ssl().clientAuth(), is(equalTo(ConnectorConfig.Ssl.ClientAuth.Enum.DISABLED)));

        ConnectorConfig withCaCerts = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/with-cacerts/default-ssl-provider@with-cacerts");
        assertTrue(withCaCerts.ssl().enabled());
        assertThat(withCaCerts.ssl().privateKeyFile(), is(equalTo("/foo/key")));
        assertThat(withCaCerts.ssl().certificateFile(), is(equalTo("/foo/cert")));
        assertThat(withCaCerts.ssl().caCertificateFile(), is(equalTo("/foo/cacerts")));
        assertThat(withCaCerts.ssl().clientAuth(), is(equalTo(ConnectorConfig.Ssl.ClientAuth.Enum.DISABLED)));

        ConnectorConfig needClientAuth = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/need-client-auth/default-ssl-provider@need-client-auth");
        assertTrue(needClientAuth.ssl().enabled());
        assertThat(needClientAuth.ssl().privateKeyFile(), is(equalTo("/foo/key")));
        assertThat(needClientAuth.ssl().certificateFile(), is(equalTo("/foo/cert")));
        assertThat(needClientAuth.ssl().caCertificateFile(), is(equalTo("")));
        assertThat(needClientAuth.ssl().clientAuth(), is(equalTo(ConnectorConfig.Ssl.ClientAuth.Enum.NEED_AUTH)));

        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        List<ConnectorFactory> connectorFactories = cluster.getChildrenByTypeRecursive(ConnectorFactory.class);
        connectorFactories.forEach(connectorFactory -> assertChildComponentExists(connectorFactory, DefaultSslProvider.COMPONENT_CLASS));
    }

    @Test
    public void verify_tht_ssl_provider_configuration_configures_correct_config() {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0' jetty='true'>",
                "    <http>",
                "        <server port='9000' id='ssl'>",
                "            <ssl-provider class='com.yahoo.CustomSslProvider' bundle='mybundle'/>",
                "        </server>",
                "    </http>",
                nodesXml,
                "",
                "</jdisc>");

        createModel(root, clusterElem);
        ConnectorConfig sslProvider = root.getConfig(ConnectorConfig.class, "default/http/jdisc-jetty/ssl/ssl-provider@ssl");

        assertTrue(sslProvider.ssl().enabled());

        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        List<ConnectorFactory> connectorFactories = cluster.getChildrenByTypeRecursive(ConnectorFactory.class);
        ConnectorFactory connectorFactory = connectorFactories.get(0);
        assertChildComponentExists(connectorFactory, "com.yahoo.CustomSslProvider");
    }

    private static void assertConnectorHasInjectedComponents(ConnectorFactory connectorFactory, String... componentNames) {
        Set<String> injectedComponentIds = connectorFactory.getInjectedComponentIds();
        assertThat(injectedComponentIds.size(), equalTo(componentNames.length));
        Arrays.stream(componentNames)
                .forEach(name -> assertThat(injectedComponentIds, hasItem(name)));
    }

    private static SimpleComponent getChildComponent(ConnectorFactory connectorFactory, int index) {
        return connectorFactory.getChildrenByTypeRecursive(SimpleComponent.class).get(index);
    }

    private static void assertComponentHasClassNameAndBundle(SimpleComponent simpleComponent,
                                                             String className,
                                                             String bundleName) {
        BundleInstantiationSpecification spec = simpleComponent.model.bundleInstantiationSpec;
        assertThat(spec.classId.toString(), is(className));
        assertThat(spec.bundle.toString(), is(bundleName));
    }

    private static void assertChildComponentExists(ConnectorFactory connectorFactory, String className) {
        Optional<SimpleComponent> simpleComponent = connectorFactory.getChildren().values().stream()
                .map(z -> (SimpleComponent) z)
                .filter(component -> component.getClassId().stringValue().equals(className))
                .findFirst();
        assertTrue(simpleComponent.isPresent());
    }

    private void assertJettyServerInConfig() {
        ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        List<JettyHttpServer> jettyServers = cluster.getChildrenByTypeRecursive(JettyHttpServer.class);

        assertThat(jettyServers.size(), is(1));

        JettyHttpServer server = jettyServers.get(0);
        assertThat(server.model.bundleInstantiationSpec.classId.toString(),
                is(com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName()));
        assertThat(server.model.bundleInstantiationSpec.bundle.toString(), is("jdisc_http_service"));
        assertThat(server.getConnectorFactories().size(), is(1));

        assertThat(
                extractComponentByClassName(
                        containerComponentsConfig(),
                        com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName()),
                is(not(nullValue())));
    }

    private static ComponentsConfig.Components extractComponentByClassName(
            final ComponentsConfig componentsConfig, final String className) {
        for (final ComponentsConfig.Components component : componentsConfig.components()) {
            if (className.equals(component.classId())) {
                return component;
            }
        }
        return null;
    }

    private static ComponentsConfig.Components.Inject extractInjectionById(
            final ComponentsConfig.Components component, final String id) {
        for (final ComponentsConfig.Components.Inject injection : component.inject()) {
            if (id.equals(injection.id())) {
                return injection;
            }
        }
        return null;
    }

    private ComponentsConfig containerComponentsConfig() {
        final ContainerCluster cluster = (ContainerCluster) root.getChildren().get("default");
        return root.getConfig(
                ComponentsConfig.class,
                cluster.getContainers().get(0).getConfigId());
    }

    private ComponentsConfig clusterComponentsConfig() {
        return componentsConfig();
    }
}
