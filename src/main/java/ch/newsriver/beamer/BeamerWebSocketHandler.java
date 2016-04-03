package ch.newsriver.beamer;


import java.io.IOException;


import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

/**
 * Created by eliapalme on 20/03/16.
 */


@ServerEndpoint("/webSocket")
public  class BeamerWebSocketHandler{


        @OnOpen
        public void onOpen(Session session) {
            BeamerMain.beamer.activeSessions.add(session);
        }

        @OnMessage
        public void onMessage(String txt, Session session) throws IOException {
            session.getBasicRemote().sendText(txt.toUpperCase());

        }



        @OnClose
        public void onClose(CloseReason reason, Session session) {
            BeamerMain.beamer.activeSessions.remove(session);
        }


}
