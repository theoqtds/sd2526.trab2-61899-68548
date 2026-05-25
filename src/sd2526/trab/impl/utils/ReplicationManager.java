package sd2526.trab.impl.utils;

import com.google.gson.Gson;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import sd2526.trab.impl.kafka.KafkaPublisher;
import sd2526.trab.impl.kafka.KafkaSubscriber;
import sd2526.trab.impl.kafka.KafkaUtils;
import sd2526.trab.impl.kafka.RecordProcessor;

import java.util.List;

public class ReplicationManager {
    private static ReplicationManager instance;
    private KafkaPublisher kafkaPublisher;
    private KafkaSubscriber kafkaSubscriber;
    private SyncPoint syncPoint;

    private final String topic;
    private final String kafkaAddress;

    private ReplicationManager(String topic, String kafkaAddress) {
        this.topic = topic;
        this.kafkaAddress = kafkaAddress;

        KafkaUtils.createTopic(topic); //Attempts to create a topic (might already exist)

        kafkaPublisher = KafkaPublisher.createPublisher(kafkaAddress);
        kafkaSubscriber = KafkaSubscriber.createSubscriber(kafkaAddress, List.of(topic));
        kafkaSubscriber.start( new RecordProcessor() {
            @Override
            public void onReceive(ConsumerRecord<String, String> r) {
                onRecord(r);
            }
        });

        syncPoint = SyncPoint.getSyncPoint();
    }

    public static void init(String topic, String kafkaAddress) {
        instance = new ReplicationManager(topic, kafkaAddress);
    }

    public static ReplicationManager getInstance() {
        return instance;
    }

    public long getVersion() {
        return syncPoint.getVersion();
    }

    public void waitForVersion(long n) {
        syncPoint.waitForVersion(n);
    }

    public long publish(String operation, List<String> parameters) {
        LocalOperation op = new LocalOperation(operation, parameters);
        String json = new Gson().toJson(op);
        return kafkaPublisher.publish(topic, json);
    }

    public void onRecord(ConsumerRecord<String, String> record) {
        LocalOperation op = new Gson().fromJson(record.value(), LocalOperation.class);
        String result = op.execute();
        syncPoint.setResult(record.offset(), result);
    }

}
