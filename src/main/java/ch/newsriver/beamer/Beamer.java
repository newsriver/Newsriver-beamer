package ch.newsriver.beamer;


import ch.newsriver.executable.BatchInterruptibleWithinExecutorPool;
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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Created by eliapalme on 11/03/16.
 */
public class Beamer extends BatchInterruptibleWithinExecutorPool implements Runnable {

    private static final Logger logger = LogManager.getLogger(Beamer.class);
    private boolean run = false;
    private static final int BATCH_SIZE = 250;
    private static final int POOL_SIZE = 50;
    private static final int QUEUE_SIZE = 500;

    private static final ObjectMapper mapper = new ObjectMapper();
    Consumer<String, String> consumer;

    public List<Session> activeSessions;


    public Beamer() {

        super(POOL_SIZE, QUEUE_SIZE);
        run = true;
        activeSessions = new LinkedList<>();

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
                ConsumerRecords<String, String> records = consumer.poll(1000);
                for (ConsumerRecord<String, String> record : records) {
                    this.waitFreeBatchExecutors(BATCH_SIZE);
                    for (Session session : activeSessions) {
                        CompletableFuture<String> taks = CompletableFuture.supplyAsync(() ->{
                            try {
                                session.getBasicRemote().sendText(record.value());
                                BeamerMain.addMetric("Articles streamed", 1);
                            }catch (IOException e){
                                activeSessions.remove(session);
                            }
                            return  "ok";
                        });

                    }

                }
                Thread.sleep(1000);

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
