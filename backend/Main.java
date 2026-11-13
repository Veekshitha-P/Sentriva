import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * SENTRIVA - Web Inspection Tool
 * --------------------------------
 * Entry point. Starts Java HTTP server on port 8080.
 *
 * COMPILE (from backend/):
 *   javac -d out *.java models/*.java
 *
 * RUN (Windows, from backend/):
 *   java -cp "out;resources" Main
 *
 * RUN (Mac/Linux, from backend/):
 *   java -cp out:resources Main
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/scan", new ScanHandler());
        server.createContext("/health", Main::handleHealth);
        server.createContext("/sample-target", Main::handleSampleTarget);
        server.createContext("/", Main::handleFrontend);

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("========================================");
        System.out.println("  SENTRIVA - Web Inspection Tool v3.0");
        System.out.println("  App:    http://localhost:" + port);
        System.out.println("  Health: http://localhost:" + port + "/health");
        System.out.println("  Scan:   http://localhost:" + port + "/scan");
        System.out.println("========================================");
    }

    private static void handleHealth(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        String body = "{\"status\":\"ok\",\"version\":\"3.0.0\",\"modules\":5}";
        sendBytes(ex, 200, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void handleFrontend(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            byte[] notFound = "Not Found".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            sendBytes(ex, 404, notFound);
            return;
        }

        Path htmlPath = resolveFrontendHtml();
        if (htmlPath == null) {
            byte[] msg = "frontend/index.html not found".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            sendBytes(ex, 404, msg);
            return;
        }

        byte[] bytes = Files.readAllBytes(htmlPath);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        sendBytes(ex, 200, bytes);
    }

    private static void handleSampleTarget(HttpExchange ex) throws IOException {
        String sampleHtml = "<!DOCTYPE html>\n"
            + "<html>\n"
            + "<head>\n"
            + "  <script src=\"https://cdn.example.com/vendor.js\"></script>\n"
            + "  <script src=\"https://connect.facebook.net/en_US/fbevents.js\"></script>\n"
            + "  <script async src=\"https://www.googletagmanager.com/gtag/js\"></script>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <div class=\"countdown\" data-countdown=\"2026-12-31\">Offer ends in 01:14:52</div>\n"
            + "  <p class=\"warn\">Only 2 left! 19 people are viewing this now.</p>\n"
            + "  <img src=\"hero.jpg\">\n"
            + "  <img src=\"gallery1.jpg\">\n"
            + "  <img src=\"gallery2.jpg\">\n"
            + "  <form>\n"
            + "    <input type=\"email\" placeholder=\"Enter email\">\n"
            + "    <p>Free trial then $19.99/mo automatically charged. Cancel by calling support.*</p>\n"
            + "    <a href=\"#\" class=\"decline\">No thanks, I prefer to pay full price</a>\n"
            + "  </form>\n"
            + "  <div class=\"result\" data-sponsored=\"true\">Featured listing</div>\n"
            + "  <p>We share your data with third party partners for targeted advertising.</p>\n"
            + "  <a href=\"https://external.example.com\" target=\"_blank\">Partner terms</a>\n"
            + "</body>\n"
            + "</html>";
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        ex.getResponseHeaders().add("Set-Cookie", "session_id=demo123; Path=/");
        sendBytes(ex, 200, sampleHtml.getBytes(StandardCharsets.UTF_8));
    }

    private static Path resolveFrontendHtml() {
        Path[] candidates = {
            Path.of("..", "frontend", "index.html"),
            Path.of("frontend", "index.html"),
            Path.of("index.html")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private static void sendBytes(HttpExchange ex, int status, byte[] bytes) throws IOException {
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
        ex.close();
    }
}
