package ch.newsriver.beamer.servlet;

import ch.newsriver.beamer.servlet.responses.ResponseRiver;
import ch.newsriver.beamer.servlet.responses.ResponseToken;
import ch.newsriver.beamer.servlet.responses.ResponseUser;
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
import ch.newsriver.data.website.WebSite;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microtripit.mandrillapp.lutung.MandrillApi;
import com.microtripit.mandrillapp.lutung.model.MandrillApiError;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.search.sort.SortOrder;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by eliapalme on 06/05/16.
 */


@Path("/v1")
public class RestAPIHandler {
    private static final Logger log = LogManager.getLogger(RestAPIHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();


    @GET
    @Path("/access-request/invite")
    @Produces(MediaType.TEXT_PLAIN)
    public Response email(@QueryParam("email") String email) {

        String response = "Error";
        MandrillApi mandrillApi = new MandrillApi("p-jQPeTF5GI461p6dwWcpA");

        MandrillMessage message = new MandrillMessage();
        message.setSubject("Newsriver Request");
        message.setHtml("<h1>Access request</h1><br />from:" + email);
        message.setAutoText(true);
        message.setFromEmail("support@newsriver.io");
        message.setFromName("Newsriver Support");

        ArrayList<MandrillMessage.Recipient> recipients = new ArrayList<MandrillMessage.Recipient>();
        MandrillMessage.Recipient recipient = new MandrillMessage.Recipient();
        recipient.setEmail("elia.palme@newsriver.io");
        recipient.setName("Elia Palme");
        recipients.add(recipient);

        message.setTo(recipients);
        message.setPreserveRecipients(true);
        try {
            MandrillMessageStatus[] messageStatusReports = mandrillApi.messages().send(message, false);
        } catch (IOException e) {

        } catch (MandrillApiError e) {

        }
        response = "Request sent!";
        return Response.ok().entity(response)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET")
                .allow("OPTIONS").build();

    }


    @GET
    @Path("/search/news")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @JsonView(APIJSONView.class)
    public List<HighlightedArticle> search(@QueryParam("searchPhrase") String searchPhrase, @DefaultValue("discoverDate") @QueryParam("sortBy") String sortBy, @DefaultValue("DESC") @QueryParam("sortOrder") SortOrder sortOrder, @DefaultValue("100") @QueryParam("limit") int limit) throws JsonProcessingException {


        ArticleRequest searchRequest = new ArticleRequest();
        searchRequest.setLimit(limit);
        searchRequest.setQuery(searchPhrase);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortOrder(sortOrder);
        return ArticleFactory.getInstance().searchArticles(searchRequest);
    }


    @POST
    @Path("/newsriver")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseRiver addRiver(@Context HttpServletResponse servlerResponse, @QueryParam("token") String tokenStr, NewsRiver river) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");

        ResponseRiver response = new ResponseRiver();

        TokenFactory tokenFactory = new TokenFactory();
        TokenBase token = tokenFactory.verifyToken(tokenStr);
        if (token == null) {
            response.setError("Invalid token");
            return response;
        }

        String sql = "INSERT INTO riverSetting (value,userId) VALUES (?,?)";
        try (Connection conn = JDBCPoolUtil.getInstance().getConnection(JDBCPoolUtil.DATABASES.Sources); PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);) {

            stmt.setString(1, mapper.writeValueAsString(river));
            stmt.setLong(2, token.getUserId());
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    response.setRiverId(generatedKeys.getLong(1));
                    response.setStatus(true);
                }
            }


        } catch (SQLException e) {
            log.fatal("Unable to crete river", e);
        }

        return response;
    }

    @OPTIONS
    @Path("/newsriver")
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.APPLICATION_JSON)
    public String options(@Context HttpServletResponse servlerResponse) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST, GET, OPTIONS");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");

        return "ok";
    }


    @GET
    @Path("/user/sign-up")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public ResponseToken signup(@Context HttpServletResponse servlerResponse, @QueryParam("email") String email, @QueryParam("password") String password, @QueryParam("name") String name) {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
        ResponseToken response = new ResponseToken();

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
                    response.setToken(tokenFactory.generateTokenAPI(userId));
                    response.setStatus(true);
                }
            }


        } catch (SQLException e) {
            log.fatal("Unable to crete user", e);
        }

        return response;
    }


    @GET
    @Path("/user/verify")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public ResponseUser verify(@Context HttpServletResponse servlerResponse, @QueryParam("token") String tokenStr) {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
        ResponseUser response = new ResponseUser();


        TokenFactory tokenFactory = new TokenFactory();
        TokenBase token = tokenFactory.verifyToken(tokenStr);

        if (token == null) {
            response.setError("Invalid token");
            return response;
        }


        User user = UserFactory.getInstance().getUser(token.getUserId());

        if (user == null) {
            response.setError("Unable to fetch user");
            return response;
        }

        response.setUser(user);
        response.setStatus(true);
        return response;
    }

    //This interface is used to combine all required JSONViews
    private interface APIJSONView extends Article.JSONViews.API, WebSite.JSONViews.API {
    }

}
