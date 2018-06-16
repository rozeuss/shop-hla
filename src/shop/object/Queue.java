package shop.object;

import hla.rti1516e.ObjectInstanceHandle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Queue {
    public static final AtomicInteger count = new AtomicInteger(0);
    public static final int MAX_SIZE = 8;

    //FOM VARIABLES
    int queueId;
    int maxSize;
    int currentSize = 0;
    //ADDITIONAL VARIABLES
    private int originalMaxSize;
    private ObjectInstanceHandle rtiHandler;
    private List<Client> clients = new LinkedList<>();

    public Queue(ObjectInstanceHandle rtiHandler) {
        this.rtiHandler = rtiHandler;
    }

    public Queue(int queueId, int maxSize) {
        this.queueId = queueId;
        this.maxSize = maxSize;
        this.originalMaxSize = maxSize;
    }
}
