package shop.object;

import hla.rti1516e.ObjectInstanceHandle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Checkout {
    public static final AtomicInteger count = new AtomicInteger(0);

    //FOM VARIABLES
    int checkoutId;
    int queueId;
    boolean open;
    //ADDITIONAL VARIABLES
    private ObjectInstanceHandle rtiHandler;

    public Checkout(ObjectInstanceHandle rtiHandler) {
        this.rtiHandler = rtiHandler;
    }

    public Checkout(int checkoutId, int queueId, boolean open) {
        this.checkoutId = checkoutId;
        this.queueId = queueId;
        this.open = open;
    }
}
