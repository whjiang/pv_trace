import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageSessionProtos.MobilePageWithSession
import org.apache.flink.streaming.util.serialization.SerializationSchema

/**
  * Created by whjiang on 2017/2/13.
  */
class MobilePageSessionSchema extends SerializationSchema[MobilePageWithSession] {
  override def serialize(t: MobilePageWithSession): Array[Byte] = {
    t.toByteArray
  }
}
