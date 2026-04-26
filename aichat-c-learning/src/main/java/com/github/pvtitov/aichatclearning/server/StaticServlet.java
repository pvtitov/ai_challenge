package com.github.pvtitov.aichatclearning.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Serves static files from the classpath.
 * Used as the root servlet to serve the web terminal UI.
 */
public class StaticServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();

        // Map "/" and "/index.html" to index.html
        if ("/".equals(path) || "/index.html".equals(path)) {
            serveResource(resp, "/static/index.html", "text/html");
            return;
        }

        // Serve other static resources by path
        String resourcePath = "/static" + path;
        String contentType = getContentType(path);
        serveResource(resp, resourcePath, contentType);
    }

    private void serveResource(HttpServletResponse resp, String path, String contentType) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            resp.setContentType(contentType);
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

            byte[] buffer = new byte[8192];
            try (OutputStream out = resp.getOutputStream()) {
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }
}
