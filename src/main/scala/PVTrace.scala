
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageProtos.MobilePage
import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageTraceProtos.MobilePageWithTrace
import org.apache.flink.api.common.functions.{MapFunction, RichMapFunction}
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.time.Time
import java.util.Stack

/**
  * main handling logic
  */
class PVTrace {
  def genPVTrace(dataStream: DataStream[MobilePage]): DataStream[MobilePageWithTrace] = {

    //sort PVs for each user by its user id and watermark
    val sortedStream = dataStream.keyBy(_.getMid)
      .timeWindow(Time.seconds(5))
      .apply(new SortAndEmitFn)

    //assign ref_page_id related info as it is session based instead of mid based
    //TODO: remove states
    val refSortedStream: DataStream[MobilePageWithTrace] = dataStream.keyBy(pv => pv.getMid+pv.getSessionId)
      .mapWithState { (pv: MobilePage, state: Option[RefPVSesionState]) =>
        val curState = state.getOrElse(RefPVSesionState(0, "", "", ""))
        //assign ref_page_id related info
        val pvTraceBuilder = MobilePageWithTrace.newBuilder()
        pvTraceBuilder.setPv(pv)

        pvTraceBuilder.setIsLandingPage(state.isEmpty)
        pvTraceBuilder.setReferPageId(curState.refPageId)
        pvTraceBuilder.setReferPageParam(curState.refPageParam)
        pvTraceBuilder.setReferPageType(curState.refPageType)
        pvTraceBuilder.setReferTabPageId(curState.refTabPageId)

        val newState = RefPVSesionState(pv.getPageId, pv.getPageType, pv.getPageParam, pv.getTabPageId)
        (pvTraceBuilder.build(), Some(newState))
      }

    //assign path id and trace, this is mid/cookie_id based
    refSortedStream.keyBy(_.getPv.getMid)
        .mapWithState { (pv: MobilePageWithTrace, state: Option[PVState]) =>
          //previous state
          val curState = state.getOrElse(PVState(0, 0, 0, null))
          var pathId = curState.pathId
          val stack = Option(curState.stack).getOrElse(new Stack[MobilePageWithTrace])

          if(pv.getReferPageId==0) {
            pathId += pathId + 1
            if(stack.empty()) {
              pathId = 0
            }
          }
          val index = 0
          val curLevel = 0
          val newState = PVState(pathId, index, curLevel, stack)
          (pv, Some(newState))
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

case class RefPVSesionState(refPageId: Long, refPageType: String, refPageParam: String, refTabPageId: String)

case class PVState(pathId: Long, index: Long, curLevel: Int, stack: Stack[MobilePageWithTrace] )
