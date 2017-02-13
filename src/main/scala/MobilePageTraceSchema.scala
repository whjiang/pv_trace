import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageTraceProtos.MobilePageTrace
import org.apache.flink.streaming.util.serialization.SerializationSchema

/**
  * Created by whjiang on 2016/12/22.
  */
class MobilePageTraceSchema extends SerializationSchema[MobilePageTrace] {
  override def serialize(t: MobilePageTrace): Array[Byte] = {
    t.toByteArray
  }
}
