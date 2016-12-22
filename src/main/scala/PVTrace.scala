
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.windowing.triggers.ContinuousProcessingTimeTrigger
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageProtos.MobilePage
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.time.Time


/**
  * main handling logic
  */
class PVTrace {
  def genPVTrace(dataStream: DataStream[MobilePage]): DataStream[UserPVTraceLog] = {
    dataStream
      .keyBy(_.getUserid)
      .window(EventTimeSessionWindows.withGap(Time.minutes(60)))
      .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(5)))
      .apply(new SortAndEmitFn)

  }
}

class SortAndEmitFn extends ProcessWindowFunction[MobilePage, UserPVTraceLog, String, TimeWindow] {

  override def process(userId: String, input: Iterable[MobilePage],
                       context: Context,
                       out: Collector[UserPVTraceLog]): Unit = {
    val trajectory = input.filter(getEventTime(_) <= context.watermark).toList.sortBy(getEventTime)
    out.collect(UserPVTraceLog(userId, 0, trajectory(0)))
  }

  private def getEventTime(mobilePage: MobilePage): Long = {
    mobilePage.getPageStartTime
  }
}

