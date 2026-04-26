package com.github.pvtitov.aichatclearning.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Embedded Jetty HTTP server that serves:
 * 1. Static web client (HTML/CSS/JS) at /
 * 2. WebSocket endpoint at /ws for terminal sessions
 *
 * By default binds to 0.0.0.0 (all interfaces) on port 8080
 * so it's accessible from the local network.
 */
public class ChatServer {

    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);

    private final int port;
    private Server server;

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        server = new Server(port);

        // Single ServletContextHandler for everything
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        // WebSocket support at /ws
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            wsContainer.addMapping("/ws", (req, res) -> new TerminalWebSocket());
        });

        // Static file servlet for everything else
        ServletHolder staticServletHolder = new ServletHolder("static", StaticServlet.class);
        context.addServlet(staticServletHolder, "/*");

        server.setHandler(context);

        // Print access information
        String localHost = getLocalIpAddress();
        logger.info("============================================================");
        logger.info("  AI Chat Lite - Web Server Starting");
        logger.info("============================================================");
        logger.info("  Local access:  http://localhost:{}", port);
        logger.info("  Network access: http://{}:{}", localHost, port);
        logger.info("  WebSocket URL: ws://{}:{}/ws", localHost, port);
        logger.info("============================================================");
        System.out.println("============================================================");
        System.out.println("  AI Chat Lite - Web Server");
        System.out.println("============================================================");
        System.out.println("  Local access:  http://localhost:" + port);
        System.out.println("  Network access: http://" + localHost + ":" + port);
        System.out.println("  Open in browser: http://localhost:" + port);
        System.out.println("============================================================");
        System.out.println();

        server.start();
    }

    public void join() throws InterruptedException {
        if (server != null) {
            server.join();
        }
    }

    public void stop() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
            logger.info("Server stopped");
        }
    }

    private String getLocalIpAddress() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            String hostAddress = localHost.getHostAddress();
            if (!hostAddress.equals("127.0.0.1")) {
                return hostAddress;
            }
            // Fallback: try to find a site-local address
            try {
                var interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                if (interfaces != null) {
                    while (interfaces.hasMoreElements()) {
                        var iface = interfaces.nextElement();
                        try {
                            if (iface.isUp() && !iface.isLoopback() && !iface.isVirtual()) {
                                var addresses = iface.getInetAddresses();
                                while (addresses.hasMoreElements()) {
                                    var addr = addresses.nextElement();
                                    if (addr.isSiteLocalAddress() && !addr.isLoopbackAddress()) {
                                        return addr.getHostAddress();
                                    }
                                }
                            }
                        } catch (java.net.SocketException e) {
                            logger.debug("Skipping network interface: {}", e.getMessage());
                        }
                    }
                }
            } catch (java.net.SocketException e) {
                logger.debug("Failed to enumerate network interfaces: {}", e.getMessage());
            }
            return hostAddress;
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }
}
