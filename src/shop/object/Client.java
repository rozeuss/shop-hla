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

    public enum State {SHOPPING, IN_CHECKOUT, END}

    public static final int MAX_SHOPPING_TIME = 3;
    public static final AtomicInteger count = new AtomicInteger(0);

    int clientId;
    boolean isPrivileged;
    int endShoppingTime;
    private ObjectInstanceHandle rtiHandler;
    boolean waitingInQueue;
    int chosenCheckoutNumber = -999; //TODO

    public Client(ObjectInstanceHandle rtiHandler) {
        this.rtiHandler = rtiHandler;
    }

    public Client(int clientId, boolean isPrivileged, int endShoppingTime) {
        this.clientId = clientId;
        this.isPrivileged = isPrivileged;
        this.endShoppingTime = endShoppingTime;
    }
}
