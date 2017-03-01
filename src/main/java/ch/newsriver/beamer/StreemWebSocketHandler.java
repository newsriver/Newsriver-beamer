package ch.newsriver.beamer;


import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import ch.newsriver.data.content.ArticleRequest;
import ch.newsriver.data.content.HighlightedArticle;
import ch.newsriver.data.website.WebSite;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.elasticsearch.search.sort.SortOrder;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;

/**
 * Created by eliapalme on 20/03/16.
 */


@ServerEndpoint("/streamWebSocket")
@Deprecated
public class StreemWebSocketHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.setConfig(mapper.getSerializationConfig().withView(StreemJSONView.class));
    }

    @OnOpen
    public void onOpen(Session session) {
        ArticleRequest placeholder = new ArticleRequest();
        BeamerMain.beamer.activeSessionsStreem.put(session, placeholder);
    }

    @OnMessage
    public void onMessage(String txt, Session session) throws IOException {
        session.getBasicRemote().sendText(txt.toUpperCase());
        try {

            ArticleRequest searchRequest = mapper.readValue(txt, ArticleRequest.class);

            //for back compatibility if no sort order is defined we set discoverDate
            if (searchRequest.getSortBy() == null) {
                searchRequest.setSortBy("discoverDate");
                searchRequest.setSortOrder(SortOrder.DESC);
            }


            //For some transition time we acccept requrest without token.
            session.getUserProperties().put("userId", -2l);

            BeamerMain.beamer.activeSessionsStreem.put(session, searchRequest);
            ObjectWriter w = mapper.writerWithView(StreemJSONView.class);
            List<HighlightedArticle> articles = ArticleFactory.getInstance().searchArticles(searchRequest);
            for (Article article : articles) {
                session.getBasicRemote().sendText(w.writeValueAsString(article));
            }

            try {
                UsageLogger.logDataPoint((long) session.getUserProperties().get("userId"), articles.size(), "/streamWebSocket");
                UsageLogger.logAPIcall((long) session.getUserProperties().get("userId"), "/streamWebSocket");
            } catch (Exception e) {
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
    private interface StreemJSONView extends Article.JSONViews.Public, WebSite.JSONViews.Public {
    }


}
