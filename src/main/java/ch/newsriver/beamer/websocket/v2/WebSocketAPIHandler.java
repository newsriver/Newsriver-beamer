package ch.newsriver.beamer.websocket.v2;


import ch.newsriver.beamer.BeamerMain;
import ch.newsriver.beamer.UsageLogger;
import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import ch.newsriver.data.content.ArticleRequest;
import ch.newsriver.data.content.HighlightedArticle;
import ch.newsriver.data.user.User;
import ch.newsriver.data.user.UserFactory;
import ch.newsriver.data.user.token.TokenFactory;
import ch.newsriver.data.website.WebSite;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.search.sort.SortOrder;

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

import static ch.newsriver.data.user.User.Subscription.FREE;
import static ch.newsriver.data.user.User.Usage.EXCEEDED;

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


            if (!session.getUserProperties().containsKey("userId")) {
                session.getBasicRemote().sendText((String) session.getUserProperties().get("error"));
                session.close();
                return;
            }
            User user = (User) session.getUserProperties().get("user");
            if (user != null && user.getUsage() == EXCEEDED && user.getSubscription() == FREE) {
                session.getBasicRemote().sendText("API Usage Limit Exceeded");
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

            //for back compatibility if no sort order is defined we set discoverDate
            if (searchRequest.getSortBy() == null) {
                searchRequest.setSortBy("discoverDate");
                searchRequest.setSortOrder(SortOrder.DESC);
            }


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
    public interface StreemJSONView extends Article.JSONViews.API, WebSite.JSONViews.ArticleNested {
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

            User user;
            try {
                TokenFactory tokenFactory = new TokenFactory();
                user = tokenFactory.getTokenUser(tokenStr);

                sec.getUserProperties().put("token", tokenStr);
                sec.getUserProperties().put("userId", user.getId());
                sec.getUserProperties().put("user", user);

            } catch (TokenFactory.TokenVerificationException e) {
                sec.getUserProperties().put("error", e.getMessage());
            } catch (UserFactory.UserNotFountException e) {
                sec.getUserProperties().put("error", e.getMessage());
            }


        }
    }

}


