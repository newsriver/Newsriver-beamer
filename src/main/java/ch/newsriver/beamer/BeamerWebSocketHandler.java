package ch.newsriver.beamer;


import java.io.IOException;


import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

/**
 * Created by eliapalme on 20/03/16.
 */


@ServerEndpoint("/streem")
public  class BeamerWebSocketHandler{





        @OnOpen
        public void onOpen(Session session) {

            System.out.println("WebSocket opened: " + session.getId());

            BeamerMain.beamer.activeSessions.add(session);
        }

        @OnMessage
        public void onMessage(String txt, Session session) throws IOException {

            System.out.println("Message received: " + txt);

            session.getBasicRemote().sendText(txt.toUpperCase());

        }



        @OnClose
        public void onClose(CloseReason reason, Session session) {
            BeamerMain.beamer.activeSessions.remove(session);
            System.out.println("Closing a WebSocket due to " + reason.getReasonPhrase());

        }


}
