
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageProtos.MobilePage
import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageTraceProtos.MobilePageWithTrace
import org.apache.flink.api.common.functions.{MapFunction, RichMapFunction}
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.functions.co.RichCoFlatMapFunction
import java.util.Stack

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.base.StringSerializer
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows

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
    //this session is used to remove state in future time
    val sessionEndNotificationStream = sortedStream.map(pv => SessionHelper(pv.getMid+pv.getSessionId, pv.getPageStartTime))
            .keyBy(_.key).window(EventTimeSessionWindows.withGap(Time.hours(1))).max("time")

    val refSortedStream = sortedStream.connect(sessionEndNotificationStream)
      .keyBy((pv => pv.getMid+pv.getSessionId), (sh => sh.key))
        .flatMap(new SessionFn)

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

class SessionFn extends RichCoFlatMapFunction[MobilePage, SessionHelper, MobilePageWithTrace] {
  private val serialVersionUID: Long = 1L
  private val refPVSesionStateTI: TypeInformation[RefPVSesionState] = createTypeInformation[RefPVSesionState]
  private val state: ValueStateDescriptor[RefPVSesionState] =
    new ValueStateDescriptor[RefPVSesionState]("ref_page_state", refPVSesionStateTI, null)

  override def flatMap2(in2: SessionHelper, collector: Collector[MobilePageWithTrace]): Unit = {
    val valueState = getRuntimeContext.getState(state)
    //remove state
    valueState.update(null)
  }

  override def flatMap1(pv: MobilePage, collector: Collector[MobilePageWithTrace]): Unit = {
    val valueState = getRuntimeContext.getState(state)
    val stateOption = Option(valueState.value())
    val curState = stateOption.getOrElse(RefPVSesionState(0, "", "", ""))
    //assign ref_page_id related info
    val pvTraceBuilder = MobilePageWithTrace.newBuilder()
    pvTraceBuilder.setPv(pv)

    pvTraceBuilder.setIsLandingPage(stateOption.isEmpty)
    pvTraceBuilder.setReferPageId(curState.refPageId)
    pvTraceBuilder.setReferPageParam(curState.refPageParam)
    pvTraceBuilder.setReferPageType(curState.refPageType)
    pvTraceBuilder.setReferTabPageId(curState.refTabPageId)
    collector.collect(pvTraceBuilder.build())

    val newState = RefPVSesionState(pv.getPageId, pv.getPageType, pv.getPageParam, pv.getTabPageId)
    valueState.update(newState)
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

case class SessionHelper(key: String, time: Long)