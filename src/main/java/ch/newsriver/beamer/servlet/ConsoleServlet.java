package ch.newsriver.beamer.servlet;


import ch.newsriver.beamer.BeamerMain;
import ch.newsriver.executable.Main;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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
    public Response console() {
        StringBuilder body = new StringBuilder();
        body.append("<html><head><meta http-equiv=\"refresh\" content=\"1\"></head><body style='font-family: monospace'>");
        body.append(Main.getManifest().replace("\n", "<br/>").replace(" ", "&nbsp;"));
        body.append("<br/><table border='1'>");
        body.append("<tr><td><b>Metric</b></td><td>&nbsp;</td></tr>");

        HashMap<Main.Metric, Long> avgs = new HashMap<Main.Metric, Long>();
        for (Main.Metric metric : BeamerMain.getMetric().keySet()) {
            SortedMap<Long, Long> units = BeamerMain.getMetric().get(metric);

            int mesurments = 30;
            Long totalCount = 0l;


            long now = 0;
            String unit = "";
            if (metric.getUnit() == ChronoUnit.SECONDS) {
                now = Duration.ofNanos(System.nanoTime()).getSeconds();
                unit = "[u/s]";
            } else if (metric.getUnit() == ChronoUnit.MINUTES) {
                now = Duration.ofNanos(System.nanoTime()).toMinutes();
                unit = "[u/m]";
            }

            for (int i = 1; i <= mesurments; i++) {
                totalCount += units.getOrDefault(now - i, 0l);
            }

            long measure = totalCount / mesurments;
            avgs.put(metric, measure);
            body.append("<tr><td>" + metric.getName() + "</td><td>" + measure + unit + "</td></tr>");
        }


        boolean status = true;
        for (Main.Metric metric : BeamerMain.getChecks().keySet()) {
            Long avg = avgs.get(metric);
            if (avg == null) {
                status = false;
                break;
            }
            Main.Check check = BeamerMain.getChecks().get(metric);
            if (check.getToComapre().compareTo(avg) != check.getExpected()) {
                status = false;
                break;
            }
        }
        

        body.append("</table>");
        body.append("</body></html>");
        if (status) {
            return Response.ok(body.toString(), MediaType.TEXT_HTML).build();
        } else {
            return Response.serverError().entity(body.toString()).build();
        }
    }

}
