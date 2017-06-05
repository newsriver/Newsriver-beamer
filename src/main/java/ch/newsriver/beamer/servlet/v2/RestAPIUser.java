package ch.newsriver.beamer.servlet.v2;

import ch.newsriver.dao.JDBCPoolUtil;
import ch.newsriver.data.analytics.AnalyticsFactory;
import ch.newsriver.data.analytics.UserUsage;
import ch.newsriver.data.content.Article;
import ch.newsriver.data.user.User;
import ch.newsriver.data.user.UserFactory;
import ch.newsriver.data.user.token.TokenBase;
import ch.newsriver.data.user.token.TokenFactory;
import ch.newsriver.data.website.WebSite;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.model.Customer;
import io.intercom.api.Conversation;
import io.intercom.api.CustomAttribute;
import io.intercom.api.Intercom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
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
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by eliapalme on 07.04.17.
 */

@Path("/v2/user")
public class RestAPIUser {
    private static final Logger log = LogManager.getLogger(RestAPIUser.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        Intercom.setApiKey("2a87448659f01b6bfae8aaf5c968551b1cea9294");
        Intercom.setAppID("zi3ghfd1");
    }


    @GET
    @Path("/sign-up")
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
    @Path("/sign-in")
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
    @Path("/verify")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response verify(@Context HttpServletResponse servlerResponse, @HeaderParam("Authorization") String tokenStr) {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        User user;
        try {
            TokenFactory tokenFactory = new TokenFactory();
            user = tokenFactory.getTokenUser(tokenStr);

        } catch (TokenFactory.TokenVerificationException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.ok(user, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @OPTIONS
    @Path("/verify")
    @Produces(MediaType.TEXT_HTML)
    public String verifyOp(@Context HttpServletResponse servlerResponse) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST, GET, OPTIONS");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        return "ok";
    }

    @POST
    @Path("/card")
    @Produces(MediaType.TEXT_HTML)
    public Response card(@Context HttpServletResponse servlerResponse, @HeaderParam("Authorization") String tokenStr, String cardToken) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");


        User user;
        try {
            TokenFactory tokenFactory = new TokenFactory();
            user = tokenFactory.getTokenUser(tokenStr);

        } catch (TokenFactory.TokenVerificationException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }


        Stripe.apiKey = "sk_live_p6DmrcRmT8KllQeJRvAvFqry";

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("email", user.getEmail());
        params.put("source", cardToken);

        try {
            Customer customer = Customer.create(params);
        } catch (Exception e) {
            return Response.serverError().entity("Unable to add credit card").build();
        }

        UserFactory.getInstance().setSubscription(user.getId(), User.Subscription.BUSINESS);

        return Response.ok(user, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @OPTIONS
    @Path("/card")
    @Produces(MediaType.TEXT_HTML)
    public String cardOp(@Context HttpServletResponse servlerResponse) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST, GET, OPTIONS");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        return "ok";
    }


    @GET
    @Path("/usage")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response usage(@Context HttpServletResponse servlerResponse, @HeaderParam("Authorization") String tokenStr) {

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

        UserUsage usage = AnalyticsFactory.getInstance().getUsage(token.getUserId(), Period.ofDays(30));


        return Response.ok(usage, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @OPTIONS
    @Path("/usage")
    @Produces(MediaType.TEXT_HTML)
    public String usageOp(@Context HttpServletResponse servlerResponse) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST, GET, OPTIONS");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        return "ok";
    }


    //This interface is used to combine all required JSONViews
    private interface APIJSONView extends Article.JSONViews.API, WebSite.JSONViews.API {
    }

}