package ch.newsriver.beamer;


import ch.newsriver.dao.JDBCPoolUtil;
import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import ch.newsriver.data.content.ArticleRequest;
import ch.newsriver.executable.poolExecution.BatchInterruptibleWithinExecutorPool;
import ch.newsriver.util.http.HttpClientPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.websocket.Session;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Created by eliapalme on 11/03/16.
 */
public class Beamer extends BatchInterruptibleWithinExecutorPool implements Runnable {

    //TODO: later replace this with a proper filter.
    final static String argusDomains = "http://www.blick.ch/,http://www.tagesanzeiger.ch/,http://www.letemps.ch/,http://www.aargauerzeitung.ch/,http://www.suedostschweiz.ch/,http://www.nzz.ch/,http://www.srf.ch/,http://www.luzernerzeitung.ch/,http://www.20min.ch/,http://www.watson.ch/,http://www.sonntagszeitung.ch/,http://www.tagblatt.ch/,https://www.swissquote.ch/,http://www.rsi.ch/,http://www.rts.ch/,http://www.swissinfo.ch/,http://www.arcinfo.ch/,http://www.fuw.ch/,http://www.bilanz.ch/,http://www.finanzen.ch/,https://www.cash.ch/,http://www.handelszeitung.ch/,http://www.inside-it.ch/,http://www.annabelle.ch/,http://www.femina.ch/,http://www.computerworld.ch/,https://www.admin.ch/,https://www.migrosmagazin.ch/,http://www.aufeminin.com/,http://www.netzwoche.ch/,http://www.schweizer-illustrierte.ch/,http://www.boleromagazin.ch/";
    private static final Logger logger = LogManager.getLogger(Beamer.class);
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final ObjectMapper mapper = new ObjectMapper();
    private static int MAX_EXECUTUION_DURATION = 120;
    public Map<Session, ArticleRequest> activeSessionsStreem;
    public Map<Session, String> activeSessionsLookup;
    Consumer<String, String> consumer;
    Producer<String, String> producer;
    private boolean run = false;
    private int batchSize;


    public Beamer(int poolSize, int batchSize, int queueSize) {

        super(poolSize, queueSize, Duration.ofSeconds(MAX_EXECUTUION_DURATION));
        this.batchSize = batchSize;
        run = true;
        activeSessionsStreem = new HashMap<>();
        activeSessionsLookup = new HashMap<>();

        Properties props = new Properties();
        InputStream inputStream = null;
        try {

            String propFileName = "kafka.properties";
            inputStream = Beamer.class.getClassLoader().getResourceAsStream(propFileName);
            if (inputStream != null) {
                props.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
            }
        } catch (Exception e) {
            logger.error("Unable to load kafka properties", e);
        } finally {
            try {
                inputStream.close();
            } catch (Exception e) {
            }
        }

        producer = new KafkaProducer(props);
        consumer = new KafkaConsumer(props);
        consumer.subscribe(Arrays.asList("raw-article", "processing-status"));


    }

    public void stop() {
        run = false;
        HttpClientPool.shutdown();
        this.shutdown();
        consumer.close();
        producer.close();
    }


    public void run() {

        while (run) {

            try {
                this.waitFreeBatchExecutors(this.batchSize);
                ConsumerRecords<String, String> records = consumer.poll(60000);
                for (ConsumerRecord<String, String> record : records) {

                    if (record.topic().equals("raw-article")) {
                        try {
                            Article article = mapper.readValue(record.value(), Article.class);

                            //TODO: this is a temporary solution to identify Argus tests articles and store them in the db.
                            try {

                                URI articleURI = new URI(article.getUrl());
                                String domain = null;
                                if (article.getWebsite() != null) {
                                    domain = article.getWebsite().getDomainName();
                                }

                                if (argusDomains.contains(articleURI.getHost()) || (domain != null && argusDomains.contains(domain))) {

                                    String sql = "INSERT IGNORE INTO Newsriver.river (riverId,articleId,insertDate,discoverDate,publicationDate,host,url,title,text,json) VALUES (1,?,NOW(),?,?,?,?,?,?,?)";

                                    try (Connection conn = JDBCPoolUtil.getInstance().getConnection(JDBCPoolUtil.DATABASES.Sources); PreparedStatement stmt = conn.prepareStatement(sql);) {


                                        stmt.setString(1, article.getId());
                                        stmt.setString(2, article.getDiscoverDate());
                                        if (article.getPublishDate() != null) {
                                            stmt.setString(3, article.getPublishDate());
                                        } else {
                                            stmt.setNull(3, Types.VARCHAR);
                                        }
                                        stmt.setString(4, articleURI.getHost());
                                        stmt.setString(5, article.getUrl());
                                        stmt.setString(6, article.getTitle());
                                        stmt.setString(7, article.getText());
                                        stmt.setString(8, record.value());

                                        stmt.executeUpdate();


                                    } catch (SQLException e) {
                                        logger.error("Unable to insert article to river table", e);
                                    }

                                }
                            } catch (URISyntaxException e) {
                                logger.error("Invalid article URL", e);
                            }


                            for (Session session : activeSessionsStreem.keySet()) {
                                ArticleRequest request = activeSessionsStreem.get(session);
                                if (request == null) {
                                    continue;
                                }
                                request.setId(article.getId());
                                if (!ArticleFactory.getInstance().searchArticles(request).isEmpty()) {
                                    CompletableFuture<String> taks = CompletableFuture.supplyAsync(() -> {
                                        try {
                                            session.getBasicRemote().sendText(record.value());
                                            BeamerMain.addMetric("Articles streamed", 1);
                                        } catch (IOException e) {
                                            activeSessionsStreem.remove(session);
                                        }
                                        return "ok";
                                    });
                                }
                            }
                        } catch (IOException e) {
                            logger.fatal("Unable to deserialize articles", e);
                        }
                    }
                    if (record.topic().equals("processing-status")) {
                        for (Session session : activeSessionsLookup.keySet()) {
                            if (!session.getId().equals(record.key())) {
                                continue;
                            }

                            try {
                                session.getBasicRemote().sendText(record.value());
                                BeamerMain.addMetric("Status streamed", 1);
                            } catch (IOException e) {
                                activeSessionsLookup.remove(session);
                            }


                        }
                    }
                }
            } catch (InterruptedException ex) {
                logger.warn("Miner job interrupted", ex);
                run = false;
                return;
            } catch (BatchSizeException ex) {
                logger.fatal("Requested a batch size bigger than pool capability.");
            }
            continue;
        }


    }

}
