import org.apache.flink.streaming.util.serialization.SerializationSchema

/**
  * Created by whjiang on 2016/12/22.
  */
class UserPVLogSchema extends SerializationSchema[UserPVTraceLog] {
  override def serialize(t: UserPVTraceLog): Array[Byte] = {
    null
  }
}
