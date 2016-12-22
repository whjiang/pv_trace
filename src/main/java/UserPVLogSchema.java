import org.apache.flink.streaming.util.serialization.SerializationSchema;


public class UserPVLogSchema implements SerializationSchema<UserPVTraceLog> {
    @Override
    public byte[] serialize(UserPVTraceLog userPVTraceLog) {
        //TODO
        return new byte[0];
    }
}
