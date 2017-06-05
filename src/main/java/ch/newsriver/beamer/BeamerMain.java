package ch.newsriver.beamer;

import ch.newsriver.beamer.websocket.v2.WebSocketAPIHandler;
import ch.newsriver.executable.poolExecution.MainWithPoolExecutorOptions;
import org.apache.commons.cli.Option;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.util.LinkedList;
import java.util.SortedMap;


/**
 * Created by eliapalme on 20/03/16.
 */


public class BeamerMain extends MainWithPoolExecutorOptions {

    private static final int DEFAUTL_PORT = 9090;
    private static final LinkedList<Option> additionalOptions = new LinkedList<>();
    private static final Logger logger = LogManager.getLogger(BeamerMain.class);
    public static Beamer beamer;

    static {
        additionalOptions.add(Option.builder("es").longOpt("es-path").hasArg().desc("Local ES home path").build());
    }

    private Server server;

    public BeamerMain(String[] args) {
        super(args, additionalOptions, false);

    }

    public static void main(String[] args) {
        new BeamerMain(args);
    }

    static public HashMap<Metric, SortedMap<Long, Long>> getMetric() {
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
            container.addEndpoint(WebSocketAPIHandler.class);
            //@Deprecated we should implement it properly and consider to offer it as an API
            //container.addEndpoint(LookupWebSocketHandler.class);

        } catch (ServletException e) {

        } catch (DeploymentException e) {

        }


        ResourceConfig config = new ResourceConfig();
        config.packages("ch.newsriver.beamer.servlet");
        ServletHolder servlet = new ServletHolder(new ServletContainer(config));
        context.addServlet(servlet, "/*");


        try {
            System.out.println("Threads pool size:" + this.getPoolSize() + "\tbatch size:" + this.getBatchSize() + "\tqueue size:" + this.getQueueSize());
            beamer = new Beamer(this.getPoolSize(), this.getBatchSize(), this.getQueueSize(), this.getInstanceName(), super.getCustomOption("es"));
            new Thread(beamer).start();
            server.start();
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
