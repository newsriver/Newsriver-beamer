package ch.newsriver.beamer;


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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.websocket.Session;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Created by eliapalme on 11/03/16.
 */
public class Beamer extends BatchInterruptibleWithinExecutorPool implements Runnable {

    private static final Logger logger = LogManager.getLogger(Beamer.class);
    private boolean run = false;
    private static int  MAX_EXECUTUION_DURATION = 120;
    private int batchSize;


    private static final ObjectMapper mapper = new ObjectMapper();
    Consumer<String, String> consumer;

    public Map<Session,ArticleRequest> activeSessions;


    public Beamer(int poolSize, int batchSize, int queueSize) {

        super(poolSize, queueSize, Duration.ofSeconds(MAX_EXECUTUION_DURATION));
        this.batchSize = batchSize;
        run = true;
        activeSessions = new HashMap<>();

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


        consumer = new KafkaConsumer(props);
        consumer.subscribe(Arrays.asList("raw-article"));


    }

    public void stop() {
        run = false;
        HttpClientPool.shutdown();
        this.shutdown();
        consumer.close();
    }


    public void run() {

        while (run) {

            try {
                this.waitFreeBatchExecutors(this.batchSize);
                ConsumerRecords<String, String> records = consumer.poll(60000);
                for (ConsumerRecord<String, String> record : records) {

                    try {
                        Article article =  mapper.readValue(record.value(),Article.class);
                        for (Session session : activeSessions.keySet()) {
                            ArticleRequest request = activeSessions.get(session);
                            if(request==null){
                                continue;
                            }
                            request.setId(article.getId());
                            if(!ArticleFactory.getInstance().searchArticles(request).isEmpty()) {
                                CompletableFuture<String> taks = CompletableFuture.supplyAsync(() -> {
                                    try {
                                        session.getBasicRemote().sendText(record.value());
                                        BeamerMain.addMetric("Articles streamed", 1);
                                    } catch (IOException e) {
                                        activeSessions.remove(session);
                                    }
                                    return "ok";
                                });
                            }
                        }
                    } catch (IOException e) {
                        logger.fatal("Unable to deserialize articles", e);
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
