package ch.newsriver.beamer.servlet.v2;


import ch.newsriver.beamer.UsageLogger;
import ch.newsriver.dao.JDBCPoolUtil;
import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import ch.newsriver.data.content.ArticleRequest;
import ch.newsriver.data.content.HighlightedArticle;
import ch.newsriver.data.user.User;
import ch.newsriver.data.user.UserFactory;
import ch.newsriver.data.user.token.TokenBase;
import ch.newsriver.data.user.token.TokenFactory;
import ch.newsriver.data.website.WebSite;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.intercom.api.Conversation;
import io.intercom.api.CustomAttribute;
import io.intercom.api.Intercom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.search.sort.SortOrder;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by eliapalme on 06/05/16.
 */


@Path("/v2")
public class RestAPIHandler {
    private static final Logger log = LogManager.getLogger(RestAPIHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        Intercom.setApiKey("2a87448659f01b6bfae8aaf5c968551b1cea9294");
        Intercom.setAppID("zi3ghfd1");
    }


    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @JsonView(APIJSONView.class)
    public Response search(@HeaderParam("Authorization") String tokenStr, @Context HttpServletResponse servlerResponse, @QueryParam("query") String searchPhrase, @DefaultValue("discoverDate") @QueryParam("sortBy") String sortBy, @DefaultValue("DESC") @QueryParam("sortOrder") SortOrder sortOrder, @DefaultValue("25") @QueryParam("limit") int limit) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");


        if (tokenStr == null) {
            return Response.serverError().entity("Authorization token missing").build();
        }
        if (limit > 100) {
            return Response.serverError().entity("Maximum articles limit is 100, please decrease your limit").build();
        }


        TokenFactory tokenFactory = new TokenFactory();
        TokenBase token = tokenFactory.verifyToken(tokenStr);

        if (token == null) {
            return Response.serverError().entity("Invalid Token").build();
        }


        ArticleRequest searchRequest = new ArticleRequest();
        searchRequest.setLimit(limit);
        searchRequest.setQuery(searchPhrase);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortOrder(sortOrder);

        List<HighlightedArticle> result = ArticleFactory.getInstance().searchArticles(searchRequest);

        try {
            UsageLogger.logQuery(token.getUserId(), searchRequest.getQuery(), result.size(), "/v2/search");
            UsageLogger.logDataPoint(token.getUserId(), result.size(), "/v2/search");
            UsageLogger.logAPIcall(token.getUserId(), "/v2/search");
        } catch (Exception e) {
            log.fatal("unable to log usage", e);
        }

        return Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @OPTIONS
    @Path("/search")
    @Produces(MediaType.TEXT_HTML)
    public String searchOp(@Context HttpServletResponse servlerResponse) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST, GET, OPTIONS");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        return "ok";
    }

    @GET
    @Path("/user/sign-up")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response signup(@Context HttpServletResponse servlerResponse, @QueryParam("email") String email, @QueryParam("password") String password, @QueryParam("name") String name) {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");

        String sql = "INSERT INTO user (name,email,password) VALUES (?,?,SHA2(?,512))";
        long userId;
        try (Connection conn = JDBCPoolUtil.getInstance().getConnection(JDBCPoolUtil.DATABASES.Sources); PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);) {

            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, password);
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
                                .setSignedUpAt(new java.util.Date().getTime() / 1000)
                                .addCustomAttribute(CustomAttribute.newStringAttribute("API token", tokenStr))
                                .setNewSession(true)
                                .setUpdateLastRequestAt(true);
                        io.intercom.api.User.create(user);

                        //Used to trigger the instantaneous welcome email
                        Map<String, String> params = new HashMap();
                        params.put("type", "user");
                        params.put("user_id", "" + userId);
                        Conversation.list(params);

                    } catch (Exception e) {
                        log.fatal("Unable to create Intercom user", e);
                    }
                    return Response.ok(tokenStr, MediaType.APPLICATION_JSON_TYPE).build();
                }
            }


        } catch (SQLException e) {
            log.fatal("Unable to crete user", e);
        }

        return Response.serverError().build();
    }


    @GET
    @Path("/user/sign-in")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response signin(@Context HttpServletResponse servlerResponse, @QueryParam("email") String email, @QueryParam("password") String password) {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");

        String sql = "SELECT id FROM user WHERE email like ? AND password like SHA2(?,512)";
        long userId;
        try (Connection conn = JDBCPoolUtil.getInstance().getConnection(JDBCPoolUtil.DATABASES.Sources); PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);) {

            stmt.setString(1, email);
            stmt.setString(2, password);

            try (ResultSet user = stmt.executeQuery()) {
                if (user.next()) {
                    TokenFactory tokenFactory = new TokenFactory();
                    String tokenStr = tokenFactory.generateTokenAPI(user.getLong("id"));

                    return Response.ok(tokenStr, MediaType.APPLICATION_JSON_TYPE).build();
                } else {
                    return Response.status(401).build();
                }
            }


        } catch (SQLException e) {
            log.fatal("Unable to crete user", e);
        }

        return Response.serverError().build();
    }


    @GET
    @Path("/user/verify")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response verify(@Context HttpServletResponse servlerResponse, @HeaderParam("Authorization") String tokenStr) {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        if (tokenStr == null) {
            return Response.serverError().entity("Authorization token missing").build();
        }

        TokenFactory tokenFactory = new TokenFactory();
        TokenBase token = tokenFactory.verifyToken(tokenStr);

        if (token == null) {
            return Response.serverError().entity("Invalid token").build();
        }


        User user = UserFactory.getInstance().getUser(token.getUserId());

        if (user == null) {
            return Response.serverError().entity("Unable to fetch user").build();
        }

        return Response.ok(user, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @OPTIONS
    @Path("/user/verify")
    @Produces(MediaType.TEXT_HTML)
    public String verifyOp(@Context HttpServletResponse servlerResponse) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST, GET, OPTIONS");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        return "ok";
    }

    //This interface is used to combine all required JSONViews
    private interface APIJSONView extends Article.JSONViews.API, WebSite.JSONViews.API {
    }

}
