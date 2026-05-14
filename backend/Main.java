import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * SENTRIVA - Web Inspection Tool
 * --------------------------------
 * Entry point. Starts Java HTTP server on port 8080.
 *
 * COMPILE:
 *   javac -d out *.java models/*.java
 *
 * RUN (Mac/Linux):
 *   java -cp out:resources Main
 *
 * RUN (Windows):
 *   java -cp out;resources Main
 */
public class Main {

    public static void main(String[] args) throws Exception {

        // Create HTTP server on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Register /scan endpoint → handled by ScanHandler
        server.createContext("/scan", new ScanHandler());

        // Allow multiple requests at the same time
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.start();

        System.out.println("========================================");
        System.out.println("  SENTRIVA - Web Inspection Tool");
        System.out.println("  Server running on http://localhost:8080");
        System.out.println("  Open index.html in your browser");
        System.out.println("========================================");
    }
}
