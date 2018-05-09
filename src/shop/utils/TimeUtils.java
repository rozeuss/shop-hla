package shop.utils;

import hla.rti1516e.LogicalTime;
import hla.rti1516e.LogicalTimeInterval;
import org.portico.impl.hla1516e.types.time.DoubleTime;
import org.portico.impl.hla1516e.types.time.DoubleTimeInterval;


public class TimeUtils {

    private TimeUtils() {
    }

    public static LogicalTime convertTime(double time) {
        return new DoubleTime(time);
    }

    public static LogicalTimeInterval convertInterval(double time) {
        return new DoubleTimeInterval(time);
    }

    public static double convertTime(LogicalTime logicalTime) {
        return ((DoubleTime) logicalTime).getTime();
    }
}
