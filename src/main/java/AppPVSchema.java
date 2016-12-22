import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageProtos.MobilePage;
import org.apache.flink.streaming.util.serialization.AbstractDeserializationSchema;
import java.io.IOException;


public class AppPVSchema extends AbstractDeserializationSchema<MobilePage> {

    public MobilePage deserialize(byte[] message) throws IOException {
        MobilePage pv = MobilePage.parseFrom(message);
        return pv;
    }

    public boolean isEndOfStream(MobilePage nextElement) {
        return false;
    }
}
