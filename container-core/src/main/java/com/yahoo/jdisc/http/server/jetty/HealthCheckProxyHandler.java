// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.TransportSecurityOptions;
import com.yahoo.security.tls.TransportSecurityUtils;
import com.yahoo.security.tls.TrustAllX509TrustManager;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.ProxyProtocolClientConnectionFactory;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.DetectorConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.server.jetty.RequestUtils.getConnectorLocalPort;

/**
 * A handler that proxies status.html health checks
 *
 * @author bjorncs
 */
class HealthCheckProxyHandler extends HandlerWrapper {

    private static final Logger log = Logger.getLogger(HealthCheckProxyHandler.class.getName());

    private static final String HEALTH_CHECK_PATH = "/status.html";

    private final Executor executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("health-check-proxy-client-"));
    private final Map<Integer, ProxyTarget> portToProxyTargetMapping;

    HealthCheckProxyHandler(List<JDiscServerConnector> connectors) {
        this.portToProxyTargetMapping = createPortToProxyTargetMapping(connectors);
    }

    private static Map<Integer, ProxyTarget> createPortToProxyTargetMapping(List<JDiscServerConnector> connectors) {
        var mapping = new HashMap<Integer, ProxyTarget>();
        for (JDiscServerConnector connector : connectors) {
            ConnectorConfig.HealthCheckProxy proxyConfig = connector.connectorConfig().healthCheckProxy();
            if (proxyConfig.enable()) {
                Duration targetTimeout = Duration.ofMillis((int) (proxyConfig.clientTimeout() * 1000));
                Duration cacheExpiry = Duration.ofMillis((int) (proxyConfig.cacheExpiry() * 1000));
                ProxyTarget target = createProxyTarget(proxyConfig.port(), targetTimeout, cacheExpiry, connectors);
                mapping.put(connector.listenPort(), target);
                log.info(String.format("Port %1$d is configured as a health check proxy for port %2$d. " +
                                               "HTTP requests to '%3$s' on %1$d are proxied as HTTPS to %2$d.",
                                       connector.listenPort(), proxyConfig.port(), HEALTH_CHECK_PATH));
            }
        }
        return mapping;
    }

    private static ProxyTarget createProxyTarget(int targetPort, Duration targetTimeout, Duration cacheExpiry,
                                                 List<JDiscServerConnector> connectors) {
        JDiscServerConnector targetConnector = connectors.stream()
                .filter(connector -> connector.listenPort() == targetPort)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Could not find any connector with listen port " + targetPort));
        SslContextFactory.Server sslContextFactory =
                Optional.ofNullable(targetConnector.getConnectionFactory(SslConnectionFactory.class))
                        .or(() -> Optional.ofNullable(targetConnector.getConnectionFactory(DetectorConnectionFactory.class))
                                .map(detectorConnFactory -> detectorConnFactory.getBean(SslConnectionFactory.class)))
                        .map(connFactory -> (SslContextFactory.Server) connFactory.getSslContextFactory())
                        .orElseThrow(() -> new IllegalArgumentException("Health check proxy can only target https port"));
        ConnectorConfig.ProxyProtocol proxyProtocolCfg = targetConnector.connectorConfig().proxyProtocol();
        boolean proxyProtocol = proxyProtocolCfg.enabled() && !proxyProtocolCfg.mixedMode();
        return new ProxyTarget(targetPort, targetTimeout, cacheExpiry, sslContextFactory, proxyProtocol);
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws IOException, ServletException {
        int localPort = getConnectorLocalPort(request);
        ProxyTarget proxyTarget = portToProxyTargetMapping.get(localPort);
        if (proxyTarget != null) {
            AsyncContext asyncContext = servletRequest.startAsync();
            asyncContext.setTimeout(proxyTarget.timeout.plusSeconds(1).toMillis()); // add additional time for response sending
            asyncContext.addListener(new AsyncListener() {
                @Override public void onComplete(AsyncEvent event) {}
                @Override public void onError(AsyncEvent event) {}
                @Override public void onStartAsync(AsyncEvent event) {}
                @Override
                public void onTimeout(AsyncEvent event) {
                    log.log(Level.FINE, event.getThrowable(), () -> "Original request timeout");
                    servletResponse.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
                    asyncContext.complete();
                }
            });
            ServletOutputStream out = servletResponse.getOutputStream();
            if (servletRequest.getRequestURI().equals(HEALTH_CHECK_PATH)) {
                executor.execute(new ProxyRequestTask(asyncContext, proxyTarget, servletResponse, out));
            } else {
                servletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                asyncContext.complete();
            }
            request.setHandled(true);
        } else {
            _handler.handle(target, request, servletRequest, servletResponse);
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (ProxyTarget target : portToProxyTargetMapping.values()) {
            target.close();
        }
        super.doStop();
    }

    private static class ProxyRequestTask implements Runnable {

        final AsyncContext asyncContext;
        final ProxyTarget target;
        final HttpServletResponse servletResponse;
        final ServletOutputStream output;

        ProxyRequestTask(AsyncContext asyncContext, ProxyTarget target, HttpServletResponse servletResponse, ServletOutputStream output) {
            this.asyncContext = asyncContext;
            this.target = target;
            this.servletResponse = servletResponse;
            this.output = output;
        }

        @Override
        public void run() {
            StatusResponse statusResponse = target.requestStatusHtml();
            servletResponse.setStatus(statusResponse.statusCode);
            if (statusResponse.contentType != null) {
                servletResponse.setHeader("Content-Type", statusResponse.contentType);
            }
            servletResponse.setHeader("Vespa-Health-Check-Proxy-Target", Integer.toString(target.port));
            output.setWriteListener(new WriteListener() {
                @Override
                public void onWritePossible() throws IOException {
                    if (output.isReady()) {
                        if (statusResponse.content != null) {
                            output.write(statusResponse.content);
                        }
                        asyncContext.complete();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.log(Level.FINE, t, () -> "Failed to write status response: " + t.getMessage());
                    asyncContext.complete();
                }
            });
        }
    }

    private static class ProxyTarget implements AutoCloseable {
        final int port;
        final Duration timeout;
        final Duration cacheExpiry;
        final SslContextFactory.Server serverSsl;
        final boolean proxyProtocol;
        volatile HttpClient client;
        volatile StatusResponse lastResponse;

        ProxyTarget(int port, Duration timeout, Duration cacheExpiry, SslContextFactory.Server serverSsl,
                    boolean proxyProtocol) {
            this.port = port;
            this.timeout = timeout;
            this.cacheExpiry = cacheExpiry;
            this.serverSsl = serverSsl;
            this.proxyProtocol = proxyProtocol;
        }

        StatusResponse requestStatusHtml() {
            StatusResponse response = lastResponse;
            if (response != null && !response.isExpired(cacheExpiry)) {
                return response;
            }
            return this.lastResponse = getStatusResponse();
        }

        private StatusResponse getStatusResponse() {
            try {
                var request = client().newRequest("https://localhost:" + port + HEALTH_CHECK_PATH);
                request.timeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
                request.idleTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (proxyProtocol) {
                    request.tag(new ProxyProtocolClientConnectionFactory.V1.Tag());
                }
                ContentResponse response = request.send();
                byte[] content = response.getContent();
                if (content != null && content.length > 0) {
                    return new StatusResponse(response.getStatus(), response.getMediaType(), content);
                } else {
                    return new StatusResponse(response.getStatus(), null, null);
                }
            } catch (TimeoutException e) {
                log.log(Level.FINE, e, () -> "Proxy request timeout ('" + e.getMessage() + "')");
                return new StatusResponse(503, null, null);
            } catch (Exception e) {
                log.log(Level.FINE, e, () -> "Proxy request failed ('" + e.getMessage() + "')");
                return new StatusResponse(500, "text/plain", e.getMessage().getBytes());
            }
        }

        // Client construction must be delayed to ensure that the SslContextFactory is started before calling getSslContext().
        private HttpClient client() throws Exception {
            if (client == null) {
                synchronized (this) {
                    if (client == null) {
                        int timeoutMillis = (int) timeout.toMillis();
                        SslContextFactory.Client clientSsl = new SslContextFactory.Client();
                        clientSsl.setHostnameVerifier((__, ___) -> true);
                        clientSsl.setSslContext(getSslContext(serverSsl));
                        HttpClient client = new HttpClient(clientSsl);
                        client.setMaxConnectionsPerDestination(4);
                        client.setConnectTimeout(timeoutMillis);
                        client.setStopTimeout(timeoutMillis);
                        client.setIdleTimeout(timeoutMillis);
                        client.setUserAgentField(new HttpField(HttpHeader.USER_AGENT, "health-check-proxy-client"));
                        client.start();
                        this.client = client;
                    }
                }
            }
            return client;
        }

        private SSLContext getSslContext(SslContextFactory.Server sslContextFactory) {
            // A client certificate is only required if the server connector's ssl context factory is configured with "need-auth".
            if (sslContextFactory.getNeedClientAuth()) {
                log.info(String.format("Port %d requires client certificate - client will provide its node certificate", port));
                // We should ideally specify the client certificate through connector config, but the model has currently no knowledge of node certificate location on disk.
                // Instead we assume that the server connector will accept its own node certificate. This will work for the current hosted use-case.
                // The Vespa TLS config will provide us the location of certificate and key.
                TransportSecurityOptions options = TransportSecurityUtils.getOptions()
                        .orElseThrow(() ->
                                new IllegalStateException("Vespa TLS configuration is required when using health check proxy to a port with client auth 'need'"));
                return new SslContextBuilder()
                        .withKeyStore(options.getPrivateKeyFile().get(), options.getCertificatesFile().get())
                        .withTrustManager(new TrustAllX509TrustManager())
                        .build();
            } else {
                log.info(String.format(
                        "Port %d does not require a client certificate - client will not provide a certificate", port));
                return new SslContextBuilder()
                        .withTrustManager(new TrustAllX509TrustManager())
                        .build();
            }
        }

        @Override
        public void close() throws Exception {
            synchronized (this) {
                if (client != null) {
                    client.stop();
                    client.destroy();
                    client = null;
                }
            }
        }
    }

    private static class StatusResponse {
        final long createdAt = System.nanoTime();
        final int statusCode;
        final String contentType;
        final byte[] content;

        StatusResponse(int statusCode, String contentType, byte[] content) {
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.content = content;
        }

        boolean isExpired(Duration expiry) { return System.nanoTime() - createdAt > expiry.toNanos(); }
    }
}
