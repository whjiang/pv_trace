
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageProtos.{MobilePage, SourceFrom}
import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageSessionProtos.MobilePageWithSession
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.functions.co.RichCoFlatMapFunction
import java.util.Stack

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows

/**
  * main handling logic
  */
class PVTrace {
  def genPVSession(dataStream: DataStream[MobilePage]): DataStream[MobilePageWithSession] = {
    //sort PVs for each user by its user id and watermark
    val sortedStream = dataStream.keyBy(_.getMid)
      .timeWindow(Time.seconds(5))
      .apply(new SortAndEmitFn).uid("resorted")

    //assign ref_page_id related info as it is session based instead of mid based
    //this session is used to remove state in future time
    val sessionEndNotificationStream = sortedStream.map(pv => SessionHelper(pv.getMid+pv.getSessionId, pv.getPageStartTime))
      .keyBy(_.key).window(EventTimeSessionWindows.withGap(Time.hours(1))).max("time").uid("session-end")

    val refSortedStream = sortedStream.connect(sessionEndNotificationStream)
      .keyBy((pv => pv.getMid+pv.getSessionId), (sh => sh.key))
      .flatMap(new SessionFn).uid("session")

    refSortedStream
  }

  def genPVTrace(refSortedStream: DataStream[MobilePageWithSession]): DataStream[TraceItem] = {
    //assign path id and trace, this is mid/cookie_id based
    val sessionEndNotificationStream2 = refSortedStream.map(pv => SessionHelper(pv.getPv.getMid, pv.getPv.getPageStartTime))
      .keyBy(_.key).window(EventTimeSessionWindows.withGap(Time.hours(1))).max("time").uid("session-end2")

    refSortedStream.connect(sessionEndNotificationStream2).keyBy(_.getPv.getMid, _.key)
          .flatMap(new TraceFn).uid("trace")
  }
}

/** Reorder the events in one event-based window.
  * After this operation, we can regard the messages under each key is sorted by event time. */
class SortAndEmitFn extends WindowFunction[MobilePage, MobilePage, String, TimeWindow] {

  override def apply(userId: String, window: TimeWindow,
                     input: Iterable[MobilePage],
                     out: Collector[MobilePage]): Unit = {
    //    val pvList = input.filter(PVTimestampExactor.extractTimestamp(_) <= context.watermark)
    val pvList = input
    pvList.toList.sortBy(PVTimestampExactor.extractTimestamp)

    pvList.foreach(pv => out.collect(pv))
  }
}

/** Assign session info for each PV */
class SessionFn extends RichCoFlatMapFunction[MobilePage, SessionHelper, MobilePageWithSession] {
  private val serialVersionUID: Long = 1L
  private val refPVSesionStateTI: TypeInformation[RefPVSesionState] = createTypeInformation[RefPVSesionState]
  private val state: ValueStateDescriptor[RefPVSesionState] =
    new ValueStateDescriptor[RefPVSesionState]("ref_page_state", refPVSesionStateTI, null)

  override def flatMap2(in2: SessionHelper, collector: Collector[MobilePageWithSession]): Unit = {
    val valueState = getRuntimeContext.getState(state)
    //remove state
    valueState.update(null)
  }

  override def flatMap1(pv: MobilePage, collector: Collector[MobilePageWithSession]): Unit = {
    val valueState = getRuntimeContext.getState(state)
    val stateOption = Option(valueState.value())
    val curState = stateOption.getOrElse(RefPVSesionState(0, "", "", ""))
    //assign ref_page_id related info
    val pvTraceBuilder = MobilePageWithSession.newBuilder()
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

/** Compute path info for each PV */
class TraceFn extends RichCoFlatMapFunction[MobilePageWithSession, SessionHelper, TraceItem] {
  private val serialVersionUID: Long = 1L
  private val pvStateTI: TypeInformation[PVState] = createTypeInformation[PVState]
  private val state: ValueStateDescriptor[PVState] =
    new ValueStateDescriptor[PVState]("page_trace", pvStateTI, null)

  override def flatMap2(in2: SessionHelper, collector: Collector[TraceItem]): Unit = {
    val valueState = getRuntimeContext.getState(state)
    //remove state
    valueState.update(null)
  }

  override def flatMap1(pv: MobilePageWithSession, collector: Collector[TraceItem]): Unit = {
    val valueState = getRuntimeContext.getState(state)
    val stateOption = Option(valueState.value())
    val curState = stateOption.getOrElse(PVState(0, 0, 0, null))
    var pathId = curState.pathId
    val stack = Option(curState.stack).getOrElse(new Stack[TraceItem])

    val thisPV = TraceItem(dimFirstSource=null, dimSecSource=null, dimThirdSource=null,
            mid=pv.getPv.getMid, pathId=0, pathNo=0, sessionId=pv.getPv.getSessionId, startTime=pv.getPv.getPageStartTime,
            pageId=pv.getPv.getPageId, pageParam=pv.getPv.getPageParam, isLandingPage=pv.getIsLandingPage,
            pageType=pv.getPv.getPageType, brandId=pv.getPv.getBrandId, goodsId=pv.getPv.getGoodsId,
            activityName=null, pageOrigin=pv.getPv.getPageOrigin,
            tabPageId=pv.getPv.getTabPageId, sourceFrom=pv.getPv.getSourceFrom, appVersion=pv.getPv.getAppVersion)

    if(pv.getReferPageId==0) {
      pathId += pathId + 1
      if(stack.empty()) {
        pathId = 0
      }
    }
    val index = 0
    val curLevel = 0

    //collector.collect(pv)

    val newState = PVState(pathId, index, curLevel, stack)
    if(stack.size() >= 3000)
      valueState.update(null)
    else
      valueState.update(newState)
  }
}


case class RefPVSesionState(refPageId: Long, refPageType: String, refPageParam: String, refTabPageId: String)

case class PVState(pathId: Long, index: Long, curLevel: Int, stack: Stack[TraceItem] )

case class SessionHelper(key: String, time: Long)

case class TraceItem(dimFirstSource : String, dimSecSource: String, dimThirdSource: String,
                     mid: String, pathId: Long, pathNo: Long, sessionId: String, startTime: Long,
                     pageId: Long, pageParam: String, isLandingPage: Boolean, pageType: String,
                     brandId: Long, goodsId: Long, activityName: String, pageOrigin: String,
                     tabPageId: String, sourceFrom: SourceFrom, appVersion: String)

