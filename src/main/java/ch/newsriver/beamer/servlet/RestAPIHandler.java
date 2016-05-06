package ch.newsriver.beamer.servlet;

import com.microtripit.mandrillapp.lutung.MandrillApi;
import com.microtripit.mandrillapp.lutung.model.MandrillApiError;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;

import javax.websocket.server.ServerEndpoint;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by eliapalme on 06/05/16.
 */


@Path("/v1/access-request")
public class RestAPIHandler {

    @GET
    @Path("invite")
    @Produces(MediaType.TEXT_PLAIN)
    public Response helloWorld(@QueryParam("email") String email) {

        String response="Error";
        MandrillApi mandrillApi = new MandrillApi("p-jQPeTF5GI461p6dwWcpA");

        MandrillMessage message = new MandrillMessage();
        message.setSubject("Newsriver Request");
        message.setHtml("<h1>Access request</h1><br />from:"+email);
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
        }catch (IOException e){

        }catch (MandrillApiError e){

        }
        response="Request sent!";
        return Response.ok().entity(response)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET")
                .allow("OPTIONS").build();

    }

}
