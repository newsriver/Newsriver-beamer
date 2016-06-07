package ch.newsriver.beamer;


import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import ch.newsriver.data.content.ArticleRequest;
import ch.newsriver.data.url.FeedURL;
import ch.newsriver.data.url.ManualURL;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;

/**
 * Created by eliapalme on 20/03/16.
 */


@ServerEndpoint("/lookupWebSocket")
public  class LookupWebSocketHandler {

    private static final ObjectMapper mapper = new ObjectMapper();


        static {
            mapper.setConfig(mapper.getSerializationConfig().withView(Article.ArticleViews.PublicView.class));
        }

        @OnOpen
        public void onOpen(Session session) {

            BeamerMain.beamer.activeSessionsLookup.put(session,null);
        }

        @OnMessage
        public void onMessage(String url, Session session) throws IOException {
            try {
                BeamerMain.beamer.activeSessionsLookup.put(session,url);
                session.getBasicRemote().sendText("Extraction started...");
                ManualURL manualURL = new ManualURL();
                manualURL.setSessionId(session.getId());
                manualURL.setUrl(url);
                manualURL.setReferralURL(url);
                String json = mapper.writeValueAsString(manualURL);
                BeamerMain.beamer.producer.send(new ProducerRecord<String, String>("raw-urls-priority", manualURL.getUrl(), json));
            } catch (IOException e) {
                session.getBasicRemote().sendText("Invalid request");
            }
        }

        @OnClose
        public void onClose(CloseReason reason, Session session) {
            BeamerMain.beamer.activeSessionsLookup.remove(session);
        }


}
