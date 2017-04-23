package ch.newsriver.beamer.servlet.v2;


import ch.newsriver.beamer.UsageLogger;
import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import ch.newsriver.data.content.ArticleRequest;
import ch.newsriver.data.content.HighlightedArticle;
import ch.newsriver.data.user.token.TokenBase;
import ch.newsriver.data.user.token.TokenFactory;
import ch.newsriver.data.website.WebSite;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

/**
 * Created by eliapalme on 06/05/16.
 */


@Path("/v2")
public class RestAPISearch {
    private static final Logger log = LogManager.getLogger(RestAPISearch.class);
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


    //This interface is used to combine all required JSONViews
    private interface APIJSONView extends Article.JSONViews.API, WebSite.JSONViews.API {
    }

}
