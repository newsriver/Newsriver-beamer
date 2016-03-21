package ch.newsriver.beamer;

import ch.newsriver.executable.Main;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;



/**
 * Created by eliapalme on 20/03/16.
 */


public class BeamerMain{

    public static  Beamer beamer;


    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // Add javax.websocket support
        ServerContainer container = WebSocketServerContainerInitializer.configureContext(context);

        // Add echo endpoint to server container
        container.addEndpoint(BeamerWebSocketHandler.class);


        ServletHolder defHolder = new ServletHolder("default",new DefaultServlet());
        defHolder.setInitParameter("dirAllowed","true");
        context.addServlet(defHolder,"/");


        beamer = new Beamer();

        try
        {
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
