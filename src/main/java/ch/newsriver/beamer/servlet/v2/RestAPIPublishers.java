package ch.newsriver.beamer.servlet.v2;

import ch.newsriver.data.user.User;
import ch.newsriver.data.user.UserFactory;
import ch.newsriver.data.user.token.TokenFactory;
import ch.newsriver.data.website.WebSite;
import ch.newsriver.data.website.WebSiteFactory;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static ch.newsriver.data.user.User.Subscription.FREE;
import static ch.newsriver.data.user.User.Usage.EXCEEDED;

/**
 * Created by eliapalme on 09.01.17.
 */

@Path("/v2/publisher")
public class RestAPIPublishers {


    private static final Logger log = LogManager.getLogger(RestAPIPublishers.class);
    private static final ObjectMapper mapper = new ObjectMapper();


    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @JsonView(WebSite.JSONViews.API.class)
    public Response search(@HeaderParam("Authorization") String tokenStr, @Context HttpServletResponse servlerResponse, @QueryParam("query") String name,@QueryParam("owner") String owner) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");


        User user;
        try {
            TokenFactory tokenFactory = new TokenFactory();
            user = tokenFactory.getTokenUser(tokenStr);
        } catch (TokenFactory.TokenVerificationException e) {
            return Response.serverError().entity(e.getMessage()).build();
        } catch (UserFactory.UserNotFountException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }


        if (user != null && user.getUsage() == EXCEEDED && user.getSubscription() == FREE) {
            return Response.status(429).entity("API Usage Limit Exceeded").build();
        }
        Long ownerId = null;
        if(owner.equalsIgnoreCase("me")){
            ownerId = user.getId();
        }
        List<WebSite> webSites = WebSiteFactory.getInstance().searchWebsitesWithName(name,ownerId);

        return Response.ok(webSites, MediaType.APPLICATION_JSON_TYPE).build();
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


    @OPTIONS
    @Path("{id}")
    @Produces(MediaType.TEXT_HTML)
    public String getPublisherOp(@Context HttpServletResponse servlerResponse) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST, GET, OPTIONS");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        return "ok";
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @JsonView(WebSite.JSONViews.API.class)
    public Response getPublisher(@HeaderParam("Authorization") String tokenStr, @Context HttpServletResponse servlerResponse, @PathParam("id") String hostname) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "GET");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");


        User user;
        try {
            TokenFactory tokenFactory = new TokenFactory();
            user = tokenFactory.getTokenUser(tokenStr);
        } catch (TokenFactory.TokenVerificationException e) {
            return Response.serverError().entity(e.getMessage()).build();
        } catch (UserFactory.UserNotFountException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }


        if (user != null && user.getUsage() == EXCEEDED && user.getSubscription() == FREE) {
            return Response.status(429).entity("API Usage Limit Exceeded").build();
        }


        if (user == null || (user.getRole() != User.Role.ADMIN && user.getRole() != User.Role.SUBSCRIBER)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("No Authorized Access").build();
        }


        WebSite webSite = WebSiteFactory.getInstance().getWebsite(hostname);

        if(user.getRole() != User.Role.ADMIN && (webSite.getOwnerId()== null || webSite.getOwnerId()!= user.getId())){
            return Response.status(Response.Status.UNAUTHORIZED).entity("No Authorized Access").build();
        }

        return Response.ok(webSite, MediaType.APPLICATION_JSON_TYPE).build();
    }


    @POST
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @JsonView(WebSite.JSONViews.API.class)
    public Response setPublisher(@HeaderParam("Authorization") String tokenStr, @Context HttpServletResponse servlerResponse, @PathParam("id") String hostname, String publisherJSON) throws JsonProcessingException, IOException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");


        User user;
        try {
            TokenFactory tokenFactory = new TokenFactory();
            user = tokenFactory.getTokenUser(tokenStr);
        } catch (TokenFactory.TokenVerificationException e) {
            return Response.serverError().entity(e.getMessage()).build();
        } catch (UserFactory.UserNotFountException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }


        if (user != null && user.getUsage() == EXCEEDED && user.getSubscription() == FREE) {
            return Response.status(429).entity("API Usage Limit Exceeded").build();
        }

        if (user == null || (user.getRole() != User.Role.ADMIN && user.getRole() != User.Role.SUBSCRIBER)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("No Authorized Access").build();
        }

        WebSite originalWebSite = WebSiteFactory.getInstance().getWebsite(hostname);

        if(user.getRole() != User.Role.ADMIN && (originalWebSite.getOwnerId()== null || originalWebSite.getOwnerId()!= user.getId())){
            return Response.status(Response.Status.UNAUTHORIZED).entity("No Authorized Access").build();
        }


        //Because the JSON object sent over the API is a subset of the JSON stored in Elastic
        //We need to merge the new version with the full version
        //Also note that we are not de-serialising the publisherJSON because the serialized version is a view of full JSON
        //meaning that some fields would result being null if deserialize into a WebSite object.
        ObjectReader updater = mapper.readerForUpdating(originalWebSite);
        originalWebSite = updater.readValue(publisherJSON);


        if (!originalWebSite.getHostName().equalsIgnoreCase(hostname)) {
            return Response.serverError().entity("Path id (hostname) and the posted Publisher hostname must be the same.").build();
        }

        WebSiteFactory.getInstance().updateWebsite(originalWebSite);

        return Response.ok(originalWebSite, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @OPTIONS
    @Path("/add/{id}")
    @Produces(MediaType.TEXT_HTML)
    public String addPublisherOp(@Context HttpServletResponse servlerResponse) throws JsonProcessingException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST, GET, OPTIONS");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");

        return "ok";
    }


    @POST
    @Path("/add/{url}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @JsonView(WebSite.JSONViews.API.class)
    public Response addPublisher(@HeaderParam("Authorization") String tokenStr, @Context HttpServletResponse servlerResponse, @PathParam("url") String url, String publisherJSON) throws JsonProcessingException, IOException, URISyntaxException {

        servlerResponse.addHeader("Allow-Control-Allow-Methods", "POST");
        servlerResponse.addHeader("Access-Control-Allow-Origin", "*");
        servlerResponse.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");


        User user;
        try {
            TokenFactory tokenFactory = new TokenFactory();
            user = tokenFactory.getTokenUser(tokenStr);
        } catch (TokenFactory.TokenVerificationException e) {
            return Response.serverError().entity(e.getMessage()).build();
        } catch (UserFactory.UserNotFountException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }


        if (user != null && user.getUsage() == EXCEEDED && user.getSubscription() == FREE) {
            return Response.status(429).entity("API Usage Limit Exceeded").build();
        }

        if (user == null || (user.getRole() != User.Role.ADMIN && user.getRole() != User.Role.SUBSCRIBER)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("No Authorized Access").build();
        }


        WebSite newWebsite = mapper.readValue(publisherJSON,WebSite.class);
        newWebsite.initWebsite(url);
        newWebsite.setName(newWebsite.getDomainName());
        newWebsite.setOwnerId(user.getId());


        String id = WebSiteFactory.getInstance().addWebsite(newWebsite);
        if(id==null){
            return  Response.status(Response.Status.CONFLICT).entity("Unable to add publisher").build();
        }

        return Response.ok(id, MediaType.APPLICATION_JSON_TYPE).build();
    }





}
