import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageTraceProtos.MobilePageWithTrace
import org.apache.flink.streaming.util.serialization.SerializationSchema

/**
  * Created by whjiang on 2016/12/22.
  */
class MobilePageTraceSchema extends SerializationSchema[MobilePageWithTrace] {
  override def serialize(t: MobilePageWithTrace): Array[Byte] = {
    t.toByteArray
  }
}
