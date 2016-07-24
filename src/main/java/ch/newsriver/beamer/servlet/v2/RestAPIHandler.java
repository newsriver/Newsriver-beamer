package ch.newsriver.beamer.servlet.v2;


import ch.newsriver.dao.JDBCPoolUtil;
import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import ch.newsriver.data.content.ArticleRequest;
import ch.newsriver.data.content.HighlightedArticle;
import ch.newsriver.data.user.User;
import ch.newsriver.data.user.UserFactory;
import ch.newsriver.data.user.river.NewsRiver;
import ch.newsriver.data.user.token.TokenBase;
import ch.newsriver.data.user.token.TokenFactory;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microtripit.mandrillapp.lutung.MandrillApi;
import com.microtripit.mandrillapp.lutung.model.MandrillApiError;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import io.intercom.api.Conversation;
import io.intercom.api.CustomAttribute;
import io.intercom.api.Intercom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Created by eliapalme on 06/05/16.
 */


@Path("/v2")
public class RestAPIHandler {
    private static final Logger log = LogManager.getLogger(RestAPIHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final static ExecutorService service = Executors.newFixedThreadPool(10);

    static{
        Intercom.setApiKey("2a87448659f01b6bfae8aaf5c968551b1cea9294");
        Intercom.setAppID("zi3ghfd1");
    }

    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON+ ";charset=utf-8")
    @JsonView(Article.ArticleViews.APIView.class)
    public Response search(@HeaderParam("Authorization") String tokenStr, @Context HttpServletResponse servlerResponse, @QueryParam("query") String searchPhrase, @DefaultValue("25") @QueryParam("limit") int limit) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");


        if(tokenStr==null){
            return Response.serverError().entity("Authorization token missing").build();
        }

        TokenFactory tokenFactory = new TokenFactory();
        TokenBase token = tokenFactory.verifyToken(tokenStr);

        if(token==null){
            return Response.serverError().entity("Invalid Token").build();
        }


        ArticleRequest searchRequest =  new ArticleRequest();
        searchRequest.setLimit(limit);
        searchRequest.setQuery(searchPhrase);

        List<HighlightedArticle> result = ArticleFactory.getInstance().searchArticles(searchRequest);

        try {
            logUsage(token.getUserId(),searchRequest.getQuery(),result.size());
        }catch (Exception e){
            log.fatal("unable to log usage",e);
        }

        return  Response.ok(result,MediaType.APPLICATION_JSON_TYPE).build();
    }

    @OPTIONS
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON+ ";charset=utf-8")
    public String searchOp(@Context HttpServletResponse servlerResponse) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST, GET, OPTIONS");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        return "ok";
    }


    public static Future<Boolean> logUsage(final long userId, final String query, final long resultCount) {

        return service.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {

                try {
                    io.intercom.api.User user = new io.intercom.api.User()
                            .setUserId("" +userId)
                            .setUpdateLastRequestAt(true)
                            .setNewSession(true);
                    io.intercom.api.User.update(user);
                }catch (Exception e){};

                final String sqlQuery = "INSERT INTO logQuery     (userId,queryHash,count,query,lastExecution,cumulatedResults) VALUES (?,SHA2(?,512),1,?,now(),?) ON DUPLICATE KEY UPDATE  count=count+1,lastExecution=NOW(),cumulatedResults=cumulatedResults+?";
                final String sqlCount = "INSERT INTO logDataPoint (userId,day,count) VALUES (?,NOW(),?) ON DUPLICATE KEY UPDATE  count=count+?";

                try (Connection conn = JDBCPoolUtil.getInstance().getConnection(JDBCPoolUtil.DATABASES.Sources);
                     PreparedStatement stmtQuery = conn.prepareStatement(sqlQuery);
                     PreparedStatement stmtCount = conn.prepareStatement(sqlCount)) {

                    stmtQuery.setLong(1,userId);
                    stmtQuery.setString(2,query);
                    stmtQuery.setString(3,query);
                    stmtQuery.setLong(4,resultCount);
                    stmtQuery.setLong(5,resultCount);
                    stmtQuery.executeUpdate();

                    stmtCount.setLong(1,userId);
                    stmtCount.setLong(2,resultCount);
                    stmtCount.setLong(3,resultCount);
                    stmtCount.executeUpdate();



                }catch (SQLException e){
                    log.fatal("Unable to crete user",e);
                    return  false;
                }

                return  true;
            }

        });
    }


    @GET
    @Path("/user/sign-up")
    @Produces(MediaType.APPLICATION_JSON+ ";charset=utf-8")
    public Response signup(@Context HttpServletResponse servlerResponse, @QueryParam("email") String email, @QueryParam("password") String password, @QueryParam("name") String name) {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");

        String sql = "INSERT INTO user (name,email,password) VALUES (?,?,SHA2(?,512))";
        long userId;
        try (Connection conn = JDBCPoolUtil.getInstance().getConnection(JDBCPoolUtil.DATABASES.Sources); PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);) {

            stmt.setString(1,name);
            stmt.setString(2,email);
            stmt.setString(3,password);
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    userId = generatedKeys.getLong(1);
                    TokenFactory tokenFactory = new TokenFactory();
                    String tokenStr = tokenFactory.generateTokenAPI(userId);
                    try {
                        io.intercom.api.User user = new io.intercom.api.User()
                                .setEmail(email)
                                .setName(name)
                                .setUserId("" + userId)
                                .setSignedUpAt(new java.util.Date().getTime()/1000)
                                .addCustomAttribute(CustomAttribute.newStringAttribute("API token", tokenStr))
                                .setNewSession(true)
                                .setUpdateLastRequestAt(true);
                        io.intercom.api.User.create(user);

                        //Used to trigger the instantaneous welcome email
                        Map<String, String> params = new HashMap();
                        params.put("type", "user");
                        params.put("user_id", ""+userId);
                        Conversation.list(params);

                    }catch (Exception e){
                        log.fatal("Unable to create Intercom user",e);
                    }
                    return  Response.ok(tokenStr,MediaType.APPLICATION_JSON_TYPE).build();
                }
            }


        }catch (SQLException e){
            log.fatal("Unable to crete user",e);
        }

        return  Response.serverError().build();
    }


    @GET
    @Path("/user/verify")
    @Produces(MediaType.APPLICATION_JSON+ ";charset=utf-8")
    public Response verify(@Context HttpServletResponse servlerResponse, @HeaderParam("Authorization") String tokenStr) {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        if(tokenStr==null){
            return Response.serverError().entity("Authorization token missing").build();
        }

        TokenFactory tokenFactory = new TokenFactory();
        TokenBase token = tokenFactory.verifyToken(tokenStr);

        if(token==null){
            return Response.serverError().entity("Invalid token").build();
        }

        UserFactory userFactory = new UserFactory();
        User user = userFactory.getUser(token.getUserId());

        if(user==null){
            return Response.serverError().entity("Unable to fetch user").build();
        }

        return  Response.ok(user,MediaType.APPLICATION_JSON_TYPE).build();
    }

    @OPTIONS
    @Path("/user/verify")
    @Produces(MediaType.APPLICATION_JSON+ ";charset=utf-8")
    public String verifyOp(@Context HttpServletResponse servlerResponse) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST, GET, OPTIONS");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        return "ok";
    }

}
