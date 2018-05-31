package shop.object;

import hla.rti1516e.ObjectInstanceHandle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Queue {
    public static final AtomicInteger count = new AtomicInteger(0);

    private int queueId;
    private int maxSize;
    private int currentSize;
    private ObjectInstanceHandle rtiHandler;

    public Queue(ObjectInstanceHandle rtiHandler) {
        this.rtiHandler = rtiHandler;
    }
}
