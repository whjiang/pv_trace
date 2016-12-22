import java.util.Properties
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer08, FlinkKafkaProducer08}


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

    // configure Kafka consumer
    val props = new Properties()
    props.setProperty("zookeeper.connect", "localhost:2181") // Zookeeper default host:port
    props.setProperty("bootstrap.servers", "localhost:9092") // Broker default host:port
    props.setProperty("group.id", "myGroup")                 // Consumer group ID
    props.setProperty("auto.offset.reset", "earliest")       // Always read topic from start

    // create a Kafka consumer
    val kafkaConsumer =
      new FlinkKafkaConsumer08(
        "mars-sc-mobile-page-logger-clean",
        new AppPVSchema(),
        props)

    // create Kafka consumer data source
    val pvInput = env.addSource(kafkaConsumer)

    val pvTrace = new PVTrace()
    val outputStream = pvTrace.genPVTrace(pvInput)

    outputStream.addSink(new FlinkKafkaProducer08[UserPVTraceLog](
      "localhost:9092",      // Kafka broker host:port
      "cleansedRides",       // Topic to write to
      new UserPVLogSchema())
    );
    env.execute("PageView")
  }
}
