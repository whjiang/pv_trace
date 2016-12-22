import java.util.Properties
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer08, FlinkKafkaProducer08}
import scala.collection.JavaConversions._

object PVTraceMain {
  def main(args: Array[String]): Unit = {

    val params = ParameterTool.fromArgs(args)

    // set up streaming execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.getConfig.setGlobalJobParameters(params)

    // configure event-time characteristics
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    // generate a Watermark every second
    env.getConfig.setAutoWatermarkInterval(1000)

    val fullProp = new Properties()
    val in = getClass().getResourceAsStream("foo.properties")
    fullProp.load(in)
    in.close()

    // configure Kafka consumer
    val inProps = new Properties()
    fullProp.stringPropertyNames().filter(_.startsWith("source.")).foreach { key =>
      inProps.put(key.substring("source.".length), fullProp.getProperty(key))
    }

    // create a Kafka consumer
    val kafkaConsumer =
      new FlinkKafkaConsumer08(
        inProps.getProperty("topic_name"),
        new AppPVSchema(),
        inProps)

    // create Kafka consumer data source
    val pvInput = env.addSource(kafkaConsumer)

    val pvTrace = new PVTrace()
    val outputStream = pvTrace.genPVTrace(pvInput)

    outputStream.addSink(new FlinkKafkaProducer08[UserPVTraceLog](
      fullProp.getProperty("dest.bootstrap.servers"),      // Kafka broker host:port
      fullProp.getProperty("dest.topic_name"),       // Topic to write to
      new UserPVLogSchema())
    );
    env.execute("PageView")
  }
}
