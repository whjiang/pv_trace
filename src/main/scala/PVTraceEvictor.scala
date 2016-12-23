import java.lang.Iterable

import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageProtos.MobilePage
import org.apache.flink.streaming.api.windowing.evictors.Evictor
import org.apache.flink.streaming.api.windowing.evictors.Evictor.EvictorContext
import org.apache.flink.streaming.api.windowing.windows.Window
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue

/**
  * Created by whjiang on 2016/12/23.
  */
class PVTraceEvictor[W <: Window] extends Evictor[MobilePage, W] {
  override def evictBefore(iterable: Iterable[TimestampedValue[MobilePage]],
                           i: Int, w: W, evictorContext: EvictorContext): Unit = {}

  override def evictAfter(iterable: Iterable[TimestampedValue[MobilePage]],
                          i: Int, w: W, evictorContext: EvictorContext): Unit = {
    val watermark = evictorContext.getCurrentWatermark

    //remove all elements except the last one
    //iterable.iterator().
  }

}
