import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageProtos.MobilePage
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks
import org.apache.flink.streaming.api.watermark.Watermark
import scala.math._

/**
  * This generator generates watermarks assuming that elements come out of order to a certain degree only.
  * The latest elements for a certain timestamp t will arrive at most n milliseconds after the earliest
  * elements for timestamp t.
  */
class PVTimestampAndWatermarkGenerator extends AssignerWithPeriodicWatermarks[MobilePage] {

  val maxOutOfOrderness = 10000L // 10 seconds

  var currentMaxTimestamp: Long = 0

  override def extractTimestamp(element: MobilePage, previousElementTimestamp: Long): Long = {
    //if event time bigger than system time, use system time instead
    //event time is set at mobile side, which may be inaccurate
    val timestamp = min(element.getTimeLocal, System.currentTimeMillis)
    currentMaxTimestamp = max(timestamp, currentMaxTimestamp)
    timestamp
  }

  override def getCurrentWatermark(): Watermark = {
    // return the watermark as current highest timestamp minus the out-of-orderness bound
    new Watermark(currentMaxTimestamp - maxOutOfOrderness);
  }
}
