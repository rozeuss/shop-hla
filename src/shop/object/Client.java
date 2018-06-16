package shop.object;

import hla.rti1516e.ObjectInstanceHandle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Client {
    public static final AtomicInteger count = new AtomicInteger(0);
    public static final int MAX_SHOPPING_TIME = 2;
    public static final int CLIENT_ARRIVAL_PROBABILITY = 2;
    public static final int PRIVILEGED_CLIENT_PROBABILITY = 3;

    //FOM VARIABLES
    int clientId;
    boolean isPrivileged;
    int endShoppingTime;
    //ADDITIONAL VARIABLES
    private ObjectInstanceHandle rtiHandler;
    private boolean waitingInQueue;
    private int arrivalTime = -1;

    public Client(ObjectInstanceHandle rtiHandler) {
        this.rtiHandler = rtiHandler;
    }

    public Client(int clientId, boolean isPrivileged, int endShoppingTime) {
        this.clientId = clientId;
        this.isPrivileged = isPrivileged;
        this.endShoppingTime = endShoppingTime;
    }
}
