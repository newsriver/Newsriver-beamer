package ch.newsriver.beamer;

import ch.newsriver.executable.Main;
import ch.newsriver.executable.poolExecution.MainWithPoolExecutorOptions;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import javax.servlet.ServletException;
import javax.websocket.DeploymentException;
import java.util.HashMap;
import java.util.SortedMap;


/**
 * Created by eliapalme on 20/03/16.
 */


public class BeamerMain extends MainWithPoolExecutorOptions {

    public static Beamer beamer;
    private Server server;
    private static final int DEFAUTL_PORT = 8080;


    public static void main(String[] args){
        new BeamerMain(args);

    }



    public int getDefaultPort(){
        return DEFAUTL_PORT;
    }

    public  BeamerMain(String[] args){
        super(args, false);
    }


    @Override
    public void shutdown() {
        try
        {
            server.stop();
        }
        catch (Exception e)
        {
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
            container.addEndpoint(BeamerWebSocketHandler.class);
        }catch (ServletException e) {

        }catch (DeploymentException e){

        }


        ServletHolder defHolder = new ServletHolder("default",new ConsoleServlet(metrics));
        defHolder.setInitParameter("dirAllowed","true");
        context.addServlet(defHolder,"/");



        try
        {
            System.out.println("Threads pool size:" + this.getPoolSize() +"\tbatch size:"+this.getBatchSize()+"\tqueue size:"+this.getBatchSize());
            beamer = new Beamer(this.getPoolSize(),this.getBatchSize(),this.getQueueSize());
            new Thread(beamer).start();
            server.start();
            server.join();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
