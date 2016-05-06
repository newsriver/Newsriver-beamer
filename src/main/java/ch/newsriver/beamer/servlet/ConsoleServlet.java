package ch.newsriver.beamer.servlet;


import ch.newsriver.beamer.BeamerMain;
import ch.newsriver.executable.Main;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.HashMap;
import java.util.SortedMap;

/**
 * Created by eliapalme on 29/03/16.
 */


@Path("/")
public class ConsoleServlet {

    @GET
    @Path("")
    @Produces(MediaType.TEXT_HTML)
    public String helloWorld() {
        StringBuilder body = new StringBuilder();
        body.append("<html><head><meta http-equiv=\"refresh\" content=\"1\"></head><body style='font-family: monospace'>");
        body.append(Main.getManifest().replace("\n","<br/>").replace(" ","&nbsp;"));
        body.append("<br/><table border='1'>");
        body.append("<tr><td><b>Metric</b></td><td>&nbsp;</td></tr>");



        for(String metric : BeamerMain.getMetric().keySet()){
            SortedMap<Long,Long> units = BeamerMain.getMetric().get(metric);

            int mesurments =   30;
            Long totalCount = 0l;
            long now = Duration.ofNanos(System.nanoTime()).getSeconds();
            for(int i=1;i<=mesurments;i++){
                totalCount +=  units.getOrDefault(now-i,0l);
            }

            body.append("<tr><td>"+metric+"</td><td>"+totalCount/mesurments+"[u/s]</td></tr>");
        }

        body.append("</table>");
        body.append("</body></html>");

        return body.toString();
    }

}
