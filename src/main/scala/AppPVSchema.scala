import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageProtos.MobilePage
import org.apache.flink.streaming.util.serialization.AbstractDeserializationSchema

/**
  * Created by whjiang on 2016/12/22.
  */
class AppPVSchema extends AbstractDeserializationSchema[MobilePage]{
  override def deserialize(bytes: Array[Byte]): MobilePage = {
    MobilePage.parseFrom(bytes)
  }
}
