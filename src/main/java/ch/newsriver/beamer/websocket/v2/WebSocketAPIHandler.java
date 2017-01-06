package ch.newsriver.beamer.websocket.v2;


import ch.newsriver.beamer.BeamerMain;
import ch.newsriver.beamer.UsageLogger;
import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import ch.newsriver.data.content.ArticleRequest;
import ch.newsriver.data.content.HighlightedArticle;
import ch.newsriver.data.user.token.TokenBase;
import ch.newsriver.data.user.token.TokenFactory;
import ch.newsriver.data.website.WebSite;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.websocket.CloseReason;
import javax.websocket.HandshakeResponse;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.List;

/**
 * Created by eliapalme on 20/03/16.
 */


@ServerEndpoint(value = "/v2/search-stream", configurator = WebSocketAPIHandler.MyServerConfigurator.class)
public class WebSocketAPIHandler {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = LogManager.getLogger(WebSocketAPIHandler.class);

    static {
        mapper.setConfig(mapper.getSerializationConfig().withView(StreemJSONView.class));
    }

    @OnOpen
    public void onOpen(Session session) {
        try {
            ArticleRequest placeholder = new ArticleRequest();

            if (!session.getUserProperties().containsKey("token")) {
                //session.getBasicRemote().sendText("Authorization token missing!");
                //session.close();
                //return;
                //TODO: uncomment and remove the following line
                //For some transition time we acccept requrest without token.
                session.getUserProperties().put("userId", -1l);
            }

            if (!session.getUserProperties().containsKey("userId")) {
                session.getBasicRemote().sendText("Invalid authorization token");
                session.close();
                return;
            }


            BeamerMain.beamer.activeSessionsStreem.put(session, placeholder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(String txt, Session session) throws IOException {
        try {
            ArticleRequest searchRequest = mapper.readValue(txt, ArticleRequest.class);
            BeamerMain.beamer.activeSessionsStreem.put(session, searchRequest);
            ObjectWriter w = mapper.writerWithView(StreemJSONView.class);
            List<HighlightedArticle> articles = ArticleFactory.getInstance().searchArticles(searchRequest);
            for (Article article : articles) {
                session.getBasicRemote().sendText(w.writeValueAsString(article));
            }

            try {
                UsageLogger.logDataPoint((long) session.getUserProperties().get("userId"), articles.size(), "/v2/search-stream");
                UsageLogger.logAPIcall((long) session.getUserProperties().get("userId"), "/v2/search-stream");
            } catch (Exception e) {
                log.fatal("unable to log usage", e);
            }

        } catch (IOException e) {
            session.getBasicRemote().sendText("Invalid request");
        }
    }

    @OnClose
    public void onClose(CloseReason reason, Session session) {
        BeamerMain.beamer.activeSessionsStreem.remove(session);
    }

    //This interface is used to combine all required JSONViews
    public interface StreemJSONView extends Article.JSONViews.API, WebSite.JSONViews.Public {
    }

    public static class MyServerConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {

            String tokenStr = null;

            if (request.getHeaders().containsKey("Authorization")) {
                tokenStr = request.getHeaders().get("Authorization").get(0);

            }
            if (tokenStr == null && request.getParameterMap().containsKey("token")) {
                tokenStr = request.getParameterMap().get("token").get(0);
            }

            if (tokenStr != null) {
                sec.getUserProperties().put("token", tokenStr);

                TokenFactory tokenFactory = new TokenFactory();
                TokenBase token = tokenFactory.verifyToken(tokenStr);
                if (token != null) {
                    sec.getUserProperties().put("userId", token.getUserId());
                }
            }

        }
    }

}


