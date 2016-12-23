
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.windowing.triggers.ContinuousProcessingTimeTrigger
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageProtos.MobilePage
import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageTraceProtos.MobilePageWithTrace
import org.apache.flink.api.common.functions.{MapFunction, RichMapFunction}
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.time.Time


/**
  * main handling logic
  */
class PVTrace {
  def genPVTrace(dataStream: DataStream[MobilePage]): DataStream[MobilePageWithTrace] = {

    //sort PVs for each user by its user id and watermark
    val sortedStream = dataStream
      .keyBy(_.getUserid)
      .timeWindow(Time.seconds(5))
      .apply(new SortAndEmitFn)

    sortedStream
      .keyBy(_.getUserid)
        .mapWithState { (pv: MobilePage, state: Option[PVState]) =>
          val curState = state.getOrElse(PVState(null, 0, 0))

          //TODO: assign session and trace
          val pvTraceBuilder = MobilePageWithTrace.newBuilder()
          pvTraceBuilder.setPv(pv)
          (pvTraceBuilder.build(), Some(curState))
        }
  }
}

class SortAndEmitFn extends ProcessWindowFunction[MobilePage, MobilePage, String, TimeWindow] {

  override def process(userId: String, input: Iterable[MobilePage],
                       context: Context,
                       out: Collector[MobilePage]): Unit = {
//    val pvList = input.filter(PVTimestampExactor.extractTimestamp(_) <= context.watermark)
    val pvList = input
    pvList.toList.sortBy(PVTimestampExactor.extractTimestamp)

    pvList.foreach(pv => out.collect(pv))
  }
}

case class PVState(sessionId: String, index: Long, curLevel: Int)
