package ch.newsriver.beamer;

import ch.newsriver.dao.JDBCPoolUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by eliapalme on 06.01.17.
 */
public class UsageLogger {

    private final static ExecutorService service = Executors.newFixedThreadPool(10);
    private static final Logger log = LogManager.getLogger(UsageLogger.class);


    public static Future<Boolean> logQuery(final long userId, final String query, final long resultCount, String endpoint) {

        return service.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {

                try {
                    io.intercom.api.User user = new io.intercom.api.User()
                            .setUserId("" + userId)
                            .setUpdateLastRequestAt(true)
                            .setNewSession(true);
                    io.intercom.api.User.update(user);
                } catch (Exception e) {
                }
                ;

                final String sqlQuery = "INSERT INTO logQuery     (userId,queryHash,count,query,lastExecution,cumulatedResults) VALUES (?,SHA2(?,512),1,?,now(),?) ON DUPLICATE KEY UPDATE  count=count+1,lastExecution=NOW(),cumulatedResults=cumulatedResults+?";

                try (Connection conn = JDBCPoolUtil.getInstance().getConnection(JDBCPoolUtil.DATABASES.Sources);
                     PreparedStatement stmtQuery = conn.prepareStatement(sqlQuery)) {

                    stmtQuery.setLong(1, userId);
                    stmtQuery.setString(2, query);
                    stmtQuery.setString(3, query);
                    stmtQuery.setLong(4, resultCount);
                    stmtQuery.setLong(5, resultCount);
                    stmtQuery.executeUpdate();


                } catch (SQLException e) {
                    log.fatal("Unable to log query", e);
                    return false;
                }

                return true;
            }

        });
    }


    public static Future<Boolean> logDataPoint(final long userId, final long newsCount, String endpoint) {

        return service.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {


                final String sqlCount = "INSERT INTO logDataPoint (userId,endpoint,day,count) VALUES (?,?,NOW(),?) ON DUPLICATE KEY UPDATE  count=count+?";

                try (Connection conn = JDBCPoolUtil.getInstance().getConnection(JDBCPoolUtil.DATABASES.Sources);
                     PreparedStatement stmtCount = conn.prepareStatement(sqlCount)) {

                    stmtCount.setLong(1, userId);
                    stmtCount.setString(2, endpoint);
                    stmtCount.setLong(3, newsCount);
                    stmtCount.setLong(4, newsCount);
                    stmtCount.executeUpdate();

                } catch (SQLException e) {
                    log.fatal("Unable to log data point usage", e);
                    return false;
                }

                return true;
            }

        });
    }

    public static Future<Boolean> logAPIcall(final long userId, String endpoint) {

        return service.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {


                final String sqlCount = "INSERT INTO logAPIcalls (userId,endpoint,day,count) VALUES (?,?,NOW(),?) ON DUPLICATE KEY UPDATE  count=count+?";

                try (Connection conn = JDBCPoolUtil.getInstance().getConnection(JDBCPoolUtil.DATABASES.Sources);
                     PreparedStatement stmtCount = conn.prepareStatement(sqlCount)) {

                    stmtCount.setLong(1, userId);
                    stmtCount.setString(2, endpoint);
                    stmtCount.setLong(3, 1);
                    stmtCount.setLong(4, 1);
                    stmtCount.executeUpdate();

                } catch (SQLException e) {
                    log.fatal("Unable to log api call", e);
                    return false;
                }

                return true;
            }

        });
    }
}
