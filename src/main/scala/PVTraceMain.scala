import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment

/**
  * Created by whjiang on 2016/12/21.
  */
object PVTraceMain {
  def main(args: Array[String]): Unit = {

    val params = ParameterTool.fromArgs(args)

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.getConfig.setGlobalJobParameters(params)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(2)

    val pvTrace = new PVTrace()

    val inputStream = null
    val outputStream = pvTrace.genPVTrace(inputStream)

    env.execute("PageView")
  }
}
