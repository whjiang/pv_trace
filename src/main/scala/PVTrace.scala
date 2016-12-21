import org.apache.flink.api.common.time.Time
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.windowing.triggers.ContinuousProcessingTimeTrigger
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperator.Context
import org.apache.flink.util.Collector

/**
  * Created by whjiang on 2016/12/21.
  */
class PVTrace {
  def genPVTrace(dataStream: DataStream[MobilePage]): DataStream[UserPVTraceLog] = {
    dataStream
      .keyBy(_.getUserid)
      .window(EventTimeSessionWindows.withGap(Time.minutes(60))
      .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(5)))
      .apply(new SortAndEmitFn)

  }

  private def getEventTime(mobilePage: MobilePage): Long = {
    mobilePage.getPageStartTime
  }
}

class SortAndEmitFn extends ProcessWindowFunction[MobilePage, UserTrajectory, String, TimeWindow] {

  override def process(userId: String, input: Iterable[MobilePage],
                       context: Context,
                       out: Collector[UserPVTraceLog]): Unit = {
    val trajectory = input.filter(getEventTime(_) <= context.watermark).toList.sortBy(getEventTime)
    out.collect(UserPVTraceLog(userId, trajectory, context.window, context.watermark))
  }
}

case class UserPVTraceLog(userId: String, parent: Int, current: MobilePage)
