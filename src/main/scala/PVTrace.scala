
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
      .keyBy(_.getPv.getUserid)
      .map(new AssignSessionAndTraceFn)
  }
}

class AssignSessionAndTraceFn extends MapFunction[MobilePageWithTrace, MobilePageWithTrace] {
  override def map(in: MobilePageWithTrace): MobilePageWithTrace = {
    in
    //TODO: assign session and trace info
  }
}

class SortAndEmitFn extends ProcessWindowFunction[MobilePage, MobilePageWithTrace, String, TimeWindow] {

  override def process(userId: String, input: Iterable[MobilePage],
                       context: Context,
                       out: Collector[MobilePageWithTrace]): Unit = {
    val trajectory = input.filter(getEventTime(_) <= context.watermark).toList.sortBy(getEventTime)
    trajectory.foreach { pv =>
      val builder = MobilePageWithTrace.newBuilder()
      builder.setPv(pv)
      out.collect(builder.build())
    }

  }

  private def getEventTime(mobilePage: MobilePage): Long = {
    mobilePage.getPageStartTime
  }
}


