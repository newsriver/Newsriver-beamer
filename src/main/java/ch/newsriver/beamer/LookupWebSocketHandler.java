package ch.newsriver.beamer;


import ch.newsriver.data.content.Article;
import ch.newsriver.data.url.ManualURL;
import ch.newsriver.data.website.WebSite;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

/**
 * Created by eliapalme on 20/03/16.
 */

@Deprecated
@ServerEndpoint("/lookupWebSocket")
public class LookupWebSocketHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.setConfig(mapper.getSerializationConfig().withView(LookupJSONView.class));
    }

    @OnOpen
    public void onOpen(Session session) {

        BeamerMain.beamer.activeSessionsLookup.put(session, "");
    }

    @OnMessage
    public void onMessage(String url, Session session) throws IOException {
        try {
            BeamerMain.beamer.activeSessionsLookup.put(session, url);
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

    //This interface is used to combine all required JSONViews
    private interface LookupJSONView extends Article.JSONViews.Public, WebSite.JSONViews.Public {
    }


}
