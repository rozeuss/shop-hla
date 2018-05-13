package shop.object;

import hla.rti1516e.ObjectInstanceHandle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Client {

    public static final int MAX_PRODUCTS = 50;

    int clientId;
    boolean isPrivileged;
    int numberOfProducts;
    private ObjectInstanceHandle rtiHandler;

    public Client (ObjectInstanceHandle rtiHandler) {
        this.rtiHandler = rtiHandler;
    }

}
