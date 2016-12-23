import com.voop.data.cleaning.logic.mars.mobile.page.MobilePageProtos.MobilePage

import scala.math._

/**
  * Created by whjiang on 2016/12/23.
  */
object PVTimestampExactor {

  def extractTimestamp(pv: MobilePage): Long = {
    //if event time bigger than system time, use system time instead
    //event time is set at mobile side, which may be inaccurate
    min(pv.getTimeLocal, System.currentTimeMillis)
  }
}
