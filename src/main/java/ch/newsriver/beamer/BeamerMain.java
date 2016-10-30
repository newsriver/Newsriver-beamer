package ch.newsriver.beamer;

import ch.newsriver.beamer.websocket.v2.WebSocketAPIHandler;
import ch.newsriver.executable.poolExecution.MainWithPoolExecutorOptions;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.servlet.ServletException;
import javax.websocket.DeploymentException;
import java.util.HashMap;
import java.util.SortedMap;


/**
 * Created by eliapalme on 20/03/16.
 */


public class BeamerMain extends MainWithPoolExecutorOptions {

    private static final int DEFAUTL_PORT = 9090;
    public static Beamer beamer;
    private Server server;


    public BeamerMain(String[] args) {
        super(args, false);
    }

    public static void main(String[] args) {
        new BeamerMain(args);

    }

    static public HashMap<String, SortedMap<Long, Long>> getMetric() {
        return metrics;
    }

    public int getDefaultPort() {
        return DEFAUTL_PORT;
    }

    @Override
    public void shutdown() {
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start() {

        server = new Server(getPort());

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");


        server.setHandler(context);

        try {
            // Add javax.websocket support
            ServerContainer container = WebSocketServerContainerInitializer.configureContext(context);
            // Add echo endpoint to server container
            container.addEndpoint(StreemWebSocketHandler.class);
            container.addEndpoint(LookupWebSocketHandler.class);
            container.addEndpoint(WebSocketAPIHandler.class);
        } catch (ServletException e) {

        } catch (DeploymentException e) {

        }


        ResourceConfig config = new ResourceConfig();
        config.packages("ch.newsriver.beamer.servlet");
        ServletHolder servlet = new ServletHolder(new ServletContainer(config));
        context.addServlet(servlet, "/*");


        try {
            System.out.println("Threads pool size:" + this.getPoolSize() + "\tbatch size:" + this.getBatchSize() + "\tqueue size:" + this.getBatchSize());
            beamer = new Beamer(this.getPoolSize(), this.getBatchSize(), this.getQueueSize(), this.getInstanceName());
            new Thread(beamer).start();
            server.start();
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
