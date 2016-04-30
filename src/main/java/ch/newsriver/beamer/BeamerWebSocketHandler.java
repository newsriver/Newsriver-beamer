package ch.newsriver.beamer;


import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import ch.newsriver.data.content.ArticleRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.util.List;


import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

/**
 * Created by eliapalme on 20/03/16.
 */


@ServerEndpoint("/webSocket")
public  class BeamerWebSocketHandler{

    private static final ObjectMapper mapper = new ObjectMapper();


        static {
            mapper.setConfig(mapper.getSerializationConfig().withView(Article.ArticleViews.PublicView.class));
        }

        @OnOpen
        public void onOpen(Session session) {

            BeamerMain.beamer.activeSessions.put(session,null);
        }

        @OnMessage
        public void onMessage(String txt, Session session) throws IOException {
            session.getBasicRemote().sendText(txt.toUpperCase());
            try {



                ArticleRequest searchRequest =  mapper.readValue(txt,ArticleRequest.class);
                BeamerMain.beamer.activeSessions.put(session,searchRequest);
                ObjectWriter w = mapper.writerWithView(Article.ArticleViews.PublicView.class);
                List<Article> articles = ArticleFactory.getInstance().searchArticles(searchRequest);
                for(Article article : articles){
                    session.getBasicRemote().sendText(w.writeValueAsString(article));
                }

            } catch (IOException e) {
                session.getBasicRemote().sendText("Invalid request");
            }
        }



        @OnClose
        public void onClose(CloseReason reason, Session session) {
            BeamerMain.beamer.activeSessions.remove(session);
        }


}
