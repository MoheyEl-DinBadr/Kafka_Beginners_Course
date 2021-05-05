package com.mohey.twitter;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.protocol.types.Field;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class TwitterProducer {

    String consumerKey = "2ddPWympkdMst5bynyCzAuE8e";
    String consumerSecret="WRNC1z8z4M0GTRvfZ5Bc8LKXKaobUaY6k57yboxtkLGRXtsaAy";
    String token="1388971253390794754-M7d0WYgn2oHcpbTKgrVn0cXkfQP6NN";
    String secret = "phZp2byGMJ7XqeNj7OwQ54252zF3zeFWKZl0WhrdEKx1M";

    Logger log = LoggerFactory.getLogger(TwitterProducer.class.getName());



    public static void main(String[] args) {
        new TwitterProducer().run();
    }

    public void run(){
        log.info("Setup");

        //Create Twitter client
        /** Set up your blocking queues: Be sure to size these properly based on expected TPS of your stream */
        BlockingQueue<String> msgQueue = new LinkedBlockingDeque<>(100000);
        Client hosebirdClient = createTwitterClient(msgQueue);

        // Attempts to establish a connection.
        hosebirdClient.connect();
        // create Kafka Producer
        KafkaProducer<String, String> producer = createKafkaProducer();

        Runtime.getRuntime().addShutdownHook(new Thread(() ->{
            System.out.println("Stopping application...");
            System.out.println("Shutting down client from twitter...");
            hosebirdClient.stop();
            System.out.println("closing producer...");
            producer.close();
        }));

        // loop to send tweets to kafka
        // on a different thread, or multiple different threads....
        while (!hosebirdClient.isDone()) {
            String msg = null;

            try {
                msg = msgQueue.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                hosebirdClient.stop();
            }

            if(msg != null){
                System.out.println("Message = " + msg);
                String topic = "twitter";

                producer.send(new ProducerRecord<String, String>(topic,"twitter_tweets", msg), (metadata, exception)->{
                    if(exception != null){
                        System.out.println("Error Happened: " + exception);
                    }
                    System.out.println("Metadata: \n" +
                            "Topic: " + metadata.topic() + "\n" +
                            "Partition: " + metadata.partition() + "\n" +
                            "Timestamp: " + metadata.timestamp() + "\n" +
                            "Offset: " + metadata.offset());
                });
                log.debug("Message: " + msg);
            }
        }

        log.info("End of Application");

    }

    public Client createTwitterClient(BlockingQueue<String> msgQueue){


        /** Declare the host you want to connect to, the endpoint, and authentication (basic auth or oauth) */
        Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();
        // Optional: set up some followings and track terms
        List<String> terms = Lists.newArrayList("pubg","fortnite");
        hosebirdEndpoint.trackTerms(terms);

        // These secrets should be read from a config file
        Authentication hosebirdAuth = new OAuth1(consumerKey, consumerSecret, token, secret);

        ClientBuilder builder = new ClientBuilder()
                .name("Hosebird-Client-01")                              // optional: mainly for the logs
                .hosts(hosebirdHosts)
                .authentication(hosebirdAuth)
                .endpoint(hosebirdEndpoint)
                .processor(new StringDelimitedProcessor(msgQueue));                          // optional: use this if you want to process client events

        Client hosebirdClient = builder.build();

        return hosebirdClient;

    }


    private KafkaProducer<String, String> createKafkaProducer() {
        String bootStrapServer = "127.0.0.1:9092";

        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapServer);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        //Create Safe Producer
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
        properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(properties);

        return kafkaProducer;
    }
}
