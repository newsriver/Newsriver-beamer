package ch.newsriver.beamer;


import ch.newsriver.beamer.websocket.v2.WebSocketAPIHandler;
import ch.newsriver.dao.ElasticsearchUtil;
import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import ch.newsriver.data.content.ArticleRequest;
import ch.newsriver.data.content.HighlightedArticle;
import ch.newsriver.executable.poolExecution.BatchInterruptibleWithinExecutorPool;
import ch.newsriver.util.http.HttpClientPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.node.NodeValidationException;

import javax.websocket.Session;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by eliapalme on 11/03/16.
 */
public class Beamer extends BatchInterruptibleWithinExecutorPool implements Runnable {

    //TODO: later replace this with a proper filter.
    //final static String argusDomains = "http://www.blick.ch/,http://www.tagesanzeiger.ch/,http://www.letemps.ch/,http://www.aargauerzeitung.ch/,http://www.suedostschweiz.ch/,http://www.nzz.ch/,http://www.srf.ch/,http://www.luzernerzeitung.ch/,http://www.20min.ch/,http://www.watson.ch/,http://www.sonntagszeitung.ch/,http://www.tagblatt.ch/,https://www.swissquote.ch/,http://www.rsi.ch/,http://www.rts.ch/,http://www.swissinfo.ch/,http://www.arcinfo.ch/,http://www.fuw.ch/,http://www.bilanz.ch/,http://www.finanzen.ch/,https://www.cash.ch/,http://www.handelszeitung.ch/,http://www.inside-it.ch/,http://www.annabelle.ch/,http://www.femina.ch/,http://www.computerworld.ch/,https://www.admin.ch/,https://www.migrosmagazin.ch/,http://www.aufeminin.com/,http://www.netzwoche.ch/,http://www.schweizer-illustrierte.ch/,http://www.boleromagazin.ch/";

    private static final Logger logger = LogManager.getLogger(Beamer.class);
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final ObjectMapper mapper = new ObjectMapper();
    ;
    private static int MAX_EXECUTUION_DURATION = 120;
    private static int CONSUMPTION_DELAY = 60;
    public ConcurrentMap<Session, ArticleRequest> activeSessionsStreem;
    public ConcurrentMap<Session, String> activeSessionsLookup;
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    Consumer<String, String> consumer;
    Producer<String, String> producer;
    LocalESInstance localES;
    private boolean run = false;
    private int batchSize;


    public Beamer(int poolSize, int batchSize, int queueSize, String instanceName, String localESPath) throws NodeValidationException {

        super(poolSize, queueSize, Duration.ofSeconds(MAX_EXECUTUION_DURATION));
        this.batchSize = batchSize;
        run = true;

        this.mapper.setConfig(mapper.getSerializationConfig().withView(WebSocketAPIHandler.StreemJSONView.class));


        activeSessionsStreem = new ConcurrentHashMap<>();
        activeSessionsLookup = new ConcurrentHashMap<>();

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

        //Every beamer should process all messages, therefore we add a random string to the group id.
        props.setProperty("group.id", props.getProperty("group.id") + "-" + instanceName);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        producer = new KafkaProducer(props);
        consumer = new KafkaConsumer(props);
        consumer.subscribe(Arrays.asList("processed-article", "processing-status"));

        this.localES = new LocalESInstance(localESPath);
        if (this.localES.replicateIndex("newsriver", ElasticsearchUtil.getInstance().getClient())) {
            this.localES.setLocalTTL("newsriver", "5m");
        }


        BeamerMain.addCheck("Incoming articles", ChronoUnit.MINUTES, -1, 0);
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
                ConsumerRecords<String, String> records = consumer.poll(5000);
                for (ConsumerRecord<String, String> record : records) {

                    if (record.topic().equals("processed-article")) {

                        try {
                            final Article article = mapper.readValue(record.value(), Article.class);
                            BeamerMain.addMetric("Incoming articles", 1, ChronoUnit.MINUTES);
                            String urlHash = "";
                            try {
                                MessageDigest digest = MessageDigest.getInstance("SHA-512");
                                byte[] hash = digest.digest(article.getUrl().getBytes(StandardCharsets.UTF_8));
                                urlHash = Base64.encodeBase64URLSafeString(hash);
                            } catch (NoSuchAlgorithmException e) {
                                logger.fatal("Unable to compute URL hash", e);
                            }

                            ArticleFactory.getInstance().saveArticle(article, urlHash, this.localES.getClient());

                            //TODO: implement delayd comsumption in Stream class and replace this class with a stream in the Main class of the Beamer
                            //Delaying the consumption of the records. This is done to give time to Elasticsearch to index the new document
                            //Since ES does not immediately index new documents we need to delay the search phase.
                            this.schedule(() -> {
                                for (Session session : activeSessionsStreem.keySet()) {
                                    try {
                                        //TODO:version direct
                                        ArticleRequest request = activeSessionsStreem.get(session);
                                        if (request == null || request.getQuery() == null) {
                                            //TODO: consider closing session with no request after a certain timeout
                                            return;
                                        }
                                        request.setId(article.getId());
                                        request.setSortBy("_score");

                                        //TODO: send article highlight and score
                                        for (HighlightedArticle hArticle : ArticleFactory.getInstance().searchArticles(request, this.localES.getClient())) {
                                            try {
                                                //TODO: consider using async send if too many exception are raised.
                                                //TODO: consider one thread per session. The issue is that if a websocket is slow it will slowup all other open sessions.
                                                //TODO: not sure about this synchronized maybe replace with the async
                                                synchronized (session) {
                                                    session.getBasicRemote().sendText(mapper.writeValueAsString(hArticle));
                                                }

                                                try {
                                                    UsageLogger.logDataPoint((long) session.getUserProperties().get("userId"), 1, "/v2/search-stream");
                                                } catch (Exception e) {
                                                    logger.fatal("unable to log usage", e);
                                                }

                                                BeamerMain.addMetric("Articles streamed", 1);
                                            } catch (IOException e) {
                                                logger.error("Unable to send message.", e);
                                            }
                                        }
                                    } catch (Exception ex) {
                                        logger.error("Unable to stream article in session", ex);
                                    }
                                }
                            }, 60, TimeUnit.SECONDS);
                        } catch (IOException ex) {
                            logger.fatal("Unable to deserialize article", ex);
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
                logger.warn("Beaming job interrupted", ex);
                run = false;
                return;
            } catch (BatchSizeException ex) {
                logger.fatal("Requested a batch size bigger than pool capability.");
            } catch (Exception ex) {
                logger.fatal("Exception in main Beamer loop", ex);
            }
        }
    }


}
