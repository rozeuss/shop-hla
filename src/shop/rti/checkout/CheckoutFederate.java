package shop.rti.checkout;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import shop.object.Checkout;
import shop.object.Client;
import shop.object.Queue;
import shop.utils.DecoderUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@SuppressWarnings("Duplicates")
public class CheckoutFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    protected EncoderFactory encoderFactory;
    protected AttributeHandle checkoutId;
    protected Map<ObjectInstanceHandle, ObjectClassHandle> instanceClassMap = new HashMap<>();
    protected AttributeHandle clientId;
    protected AttributeHandle queueId;
    InteractionClassHandle openCheckoutInteractionHandle;
    ParameterHandle openCheckoutCheckoutId;
    InteractionClassHandle closeCheckoutInteractionHandle;
    ParameterHandle closeCheckoutCheckoutId;
    InteractionClassHandle clientExitInteractionHandle;
    ParameterHandle clientExitClientId;
    ParameterHandle clientExitCheckoutId;
    InteractionClassHandle endServiceInteractionHandle;
    ParameterHandle endServiceCheckoutId;
    ParameterHandle endServiceClientId;
    ObjectClassHandle checkoutObjectHandle;
    AttributeHandle checkoutIsOpened;
    AttributeHandle checkoutQueueId;
    List<Checkout> checkouts = new ArrayList<>();
    List<Checkout> checkoutsToMake = new ArrayList<>();
    List<Client> clients = new ArrayList<>();
    List<Queue> queues = new ArrayList<>();
    ObjectClassHandle clientObjectHandle;
    AttributeHandle clientIsPrivileged;
    AttributeHandle clientEndShoppingTime;
    ObjectClassHandle queueObjectHandle;
    AttributeHandle queueMaxSize;
    AttributeHandle queueCurrentSize;
    private RTIambassador rtiamb;
    private CheckoutAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;
    private Random random = new Random();
    private Map<Integer, Double> queuesNowServicingTime = new HashMap<>();

    public static void main(String[] args) {
        String federateName = "checkout";
        if (args.length != 0) {
            federateName = args[0];
        }
        try {
            new CheckoutFederate().runFederate(federateName);
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

    private void log(String message) {
        System.out.println("Checkout   : " + message);
    }

    private void waitForUser() {
        log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (Exception e) {
            log("Error while waiting for user input: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void runFederate(String federateName) throws Exception {
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();
        log("Connecting...");
        fedamb = new CheckoutAmbassador(this);
        rtiamb.connect(fedamb, CallbackModel.HLA_EVOKED);
        log("Creating Federation...");
        try {
            URL[] modules = new URL[]{(new File("FOM.xml")).toURI().toURL()};
            rtiamb.createFederationExecution("ExampleFederation", modules);
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception loading one of the FOM modules from disk: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }
        rtiamb.joinFederationExecution(federateName, "ExampleFederation");
        log("Joined Federation as " + federateName);
        this.timeFactory = (HLAfloat64TimeFactory) rtiamb.getTimeFactory();
        rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);
        while (!fedamb.isAnnounced) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
        waitForUser();
        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (!fedamb.isReadyToRun) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
        enableTimePolicy();
        log("Time Policy Enabled");
        publishAndSubscribe();
        log("Published and Subscribed");

        registerNewCheckout(new Checkout(Checkout.count.get(), Checkout.count.getAndIncrement(), true));
        System.out.println("***************************************************" +
                "***********************************************");
        while (fedamb.running) {
            advanceTime(1.0);
            System.out.println();
            log("Time Advanced to " + fedamb.federateTime);
            doThings();
        }
    }

    private void enableTimePolicy() throws Exception {
        HLAfloat64Interval lookahead = timeFactory.makeInterval(fedamb.federateLookahead);
        this.rtiamb.enableTimeRegulation(lookahead);
        while (!fedamb.isRegulating) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
        this.rtiamb.enableTimeConstrained();
        while (!fedamb.isConstrained) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private void sendClientExitInteraction(int checkoutId, HLAfloat64Time time) throws RTIexception {
        log("CLIENT EXIT CHECKOUT: (" + checkoutId + ")");
        ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
        parameterHandleValueMap.put(clientExitCheckoutId, encoderFactory.createHLAinteger32BE(checkoutId).toByteArray());
        parameterHandleValueMap.put(clientExitClientId, encoderFactory.createHLAinteger32BE(0).toByteArray());
        rtiamb.sendInteraction(clientExitInteractionHandle, parameterHandleValueMap, generateTag(), time);
    }

    private void sendEndServiceInteraction(int checkoutId, HLAfloat64Time time) throws RTIexception {
        log("END SERVICE: (" + checkoutId + ")");
        ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
        parameterHandleValueMap.put(endServiceCheckoutId, encoderFactory.createHLAinteger32BE(checkoutId).toByteArray());
        parameterHandleValueMap.put(endServiceClientId, encoderFactory.createHLAinteger32BE(0).toByteArray());
        rtiamb.sendInteraction(endServiceInteractionHandle, parameterHandleValueMap, generateTag(), time);
    }

    private void publishAndSubscribe() throws RTIexception {
        // otwarcie kasy
        openCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.OpenCheckout");
        openCheckoutCheckoutId = rtiamb.getParameterHandle(openCheckoutInteractionHandle, "checkoutId");
        rtiamb.subscribeInteractionClass(openCheckoutInteractionHandle);

        // zamkniecie kasy
        closeCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.CloseCheckout");
        closeCheckoutCheckoutId = rtiamb.getParameterHandle(closeCheckoutInteractionHandle, "checkoutId");
        rtiamb.subscribeInteractionClass(closeCheckoutInteractionHandle);

        // rozpoczecie obslugi
        clientExitInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ClientExit");
        clientExitCheckoutId = rtiamb.getParameterHandle(clientExitInteractionHandle, "checkoutId");
        clientExitClientId = rtiamb.getParameterHandle(clientExitInteractionHandle, "clientId");
        rtiamb.publishInteractionClass(clientExitInteractionHandle);

        // zakoczenie obslugi
        endServiceInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndService");
        endServiceCheckoutId = rtiamb.getParameterHandle(endServiceInteractionHandle, "checkoutId");
        endServiceClientId = rtiamb.getParameterHandle(endServiceInteractionHandle, "clientId");
        rtiamb.publishInteractionClass(endServiceInteractionHandle);

        // register object kasa
        checkoutObjectHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Checkout");
        checkoutIsOpened = rtiamb.getAttributeHandle(checkoutObjectHandle, "isOpened");
        checkoutId = rtiamb.getAttributeHandle(checkoutObjectHandle, "checkoutId");
        checkoutQueueId = rtiamb.getAttributeHandle(checkoutObjectHandle, "queueId");
        AttributeHandleSet checkoutAttributes = rtiamb.getAttributeHandleSetFactory().create();
        checkoutAttributes.add(checkoutIsOpened);
        checkoutAttributes.add(checkoutId);
        checkoutAttributes.add(checkoutQueueId);
        rtiamb.publishObjectClassAttributes(checkoutObjectHandle, checkoutAttributes);
        rtiamb.subscribeObjectClassAttributes(checkoutObjectHandle, checkoutAttributes);

        //      discover object klient
        clientObjectHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Client");
        clientIsPrivileged = rtiamb.getAttributeHandle(clientObjectHandle, "isPrivileged");
        clientEndShoppingTime = rtiamb.getAttributeHandle(clientObjectHandle, "endShoppingTime");
        clientId = rtiamb.getAttributeHandle(clientObjectHandle, "clientId");
        AttributeHandleSet clientAttributes = rtiamb.getAttributeHandleSetFactory().create();
        clientAttributes.add(clientIsPrivileged);
        clientAttributes.add(clientEndShoppingTime);
        clientAttributes.add(clientId);
        rtiamb.subscribeObjectClassAttributes(clientObjectHandle, clientAttributes);

        // discover object kolejka
        queueObjectHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Queue");
        queueMaxSize = rtiamb.getAttributeHandle(queueObjectHandle, "maxSize");
        queueCurrentSize = rtiamb.getAttributeHandle(queueObjectHandle, "currentSize");
        queueId = rtiamb.getAttributeHandle(queueObjectHandle, "queueId");
        AttributeHandleSet queueAttributes = rtiamb.getAttributeHandleSetFactory().create();
        queueAttributes.add(queueMaxSize);
        queueAttributes.add(queueCurrentSize);
        queueAttributes.add(queueId);
        rtiamb.subscribeObjectClassAttributes(queueObjectHandle, queueAttributes);
    }

    private void updateCheckoutAttributeValues(Checkout checkout, HLAfloat64Time time) throws RTIexception {
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(3);
        attributes.put(checkoutId, DecoderUtils.encodeInt(encoderFactory, checkout.getCheckoutId()));
        attributes.put(checkoutQueueId, DecoderUtils.encodeInt(encoderFactory, checkout.getQueueId()));
        attributes.put(checkoutIsOpened, DecoderUtils.encodeBoolean(encoderFactory, checkout.isOpen()));
        rtiamb.updateAttributeValues(checkout.getRtiHandler(), attributes, generateTag(), time);
    }

    private void advanceTime(double timestep) throws RTIexception {
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(time);
        while (fedamb.isAdvancing) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private void doThings() throws RTIexception {
        checkoutsToMake.forEach(System.out::println);
        checkouts.forEach(System.out::println);
        queues.forEach(System.out::println);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);
        for (Checkout checkout : checkoutsToMake) {
            registerNewCheckout(checkout);
        }
        checkoutsToMake.clear();
        if (checkouts.size() > 0) {
            if (!queues.isEmpty()) {
                for (Checkout checkout : checkouts) {
                    Optional<Queue> q = queues.stream()
                            .filter(queue -> queue.getQueueId() == checkout.getQueueId()).findAny();
                    if (q.isPresent() && q.get().getCurrentSize() > 0) {
                        if (!queuesNowServicingTime.containsKey(q.get().getQueueId())) {
                            queuesNowServicingTime.put(q.get().getQueueId(),
                                    fedamb.federateTime + random.nextInt(6));
//                            sendClientExitInteraction(q.get().getQueueId(), time);
                        }

                    }
                }
            }
        }
        List<Integer> toDeleteList = new ArrayList<>();
        for (Map.Entry<Integer, Double> integerDoubleEntry : queuesNowServicingTime.entrySet()) {
            if (integerDoubleEntry.getValue() == fedamb.federateTime) {
                Integer key = integerDoubleEntry.getKey();
                toDeleteList.add(key);
                sendEndServiceInteraction(integerDoubleEntry.getKey(), time);
            }
        }
        for (Integer aDouble : toDeleteList) {
            queuesNowServicingTime.remove(aDouble);
        }
        for (Checkout checkout : checkouts) {
            updateCheckoutAttributeValues(checkout, time);
        }
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    private void registerNewCheckout(Checkout checkout) {
        ObjectInstanceHandle objectInstanceHandle = null;
        try {
            objectInstanceHandle = registerObject();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
        checkout.setRtiHandler(objectInstanceHandle);
        System.out.println("CREATED: " + checkout);
        checkouts.add(checkout);
    }

    private ObjectInstanceHandle registerObject() throws RTIexception {
        return rtiamb.registerObjectInstance(checkoutObjectHandle);
    }

    void discoverClient(ObjectInstanceHandle client) {
        clients.add(new Client(client));
        log("DISCOVERED NEW CLIENT " + client);
    }

    void updateClient(ObjectInstanceHandle handle, int clientId, boolean isPrivileged, int endShoppingTime) {
        for (Client client : clients) {
            if (client.getRtiHandler().equals(handle)) {
                client.setClientId(clientId);
                client.setPrivileged(isPrivileged);
                client.setEndShoppingTime(endShoppingTime);
            }
        }
//        log("Updated clients " + clients);
    }

    void updateQueue(ObjectInstanceHandle handle, int queueId, int queueMaxSize, int queueCurrentSize) {
        for (Queue queue : queues) {
            if (queue.getRtiHandler().equals(handle)) {
                queue.setQueueId(queueId);
                queue.setMaxSize(queueMaxSize);
                queue.setCurrentSize(queueCurrentSize);
            }
        }
//        log("Updated queues " + queues);
    }

    void discoverQueue(ObjectInstanceHandle queueHandle) {
        queues.add(new Queue(queueHandle));
    }

    void receiveOpenCheckoutInteraction(int checkoutId) {
        Optional<Checkout> first = checkouts.stream()
                .filter(checkout -> checkout.getCheckoutId() == checkoutId).findFirst();
        if (first.isPresent()) {
            openExistingCheckout(first);
        } else {
            openNewCheckout();
        }
    }

    private void openNewCheckout() {
        int id = Checkout.count.getAndIncrement();
        Checkout checkout = new Checkout(id, id, true);
        checkoutsToMake.add(checkout);
        System.out.println("TO CREATE: " + checkout);
    }

    private void openExistingCheckout(Optional<Checkout> first) {
        first.get().setOpen(true);
        System.out.println("OPEN EXISTING CHECKOUT (" + first.get() + ")");
    }

    void closeCheckout(int checkoutId) {
        checkouts.stream().filter(checkout -> checkout.getCheckoutId() == checkoutId).findFirst()
                .ifPresent(checkout -> checkout.setOpen(false));
    }
}