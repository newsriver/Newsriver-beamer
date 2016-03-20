package ch.newsriver.beamer;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;



/**
 * Created by eliapalme on 20/03/16.
 */


public class Beamer {


    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);
        WebSocketHandler wsHandler = new BeamerWebSocketHandler() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.register(BeamerWebSocketHandler.class);
            }
        };
        server.setHandler(wsHandler);
        server.start();
        server.join();
    }

}
