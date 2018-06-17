package shop.rti.manager;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import shop.object.Checkout;
import shop.object.Client;
import shop.object.Queue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ManagerFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    protected EncoderFactory encoderFactory;
    protected List<Client> clients = new ArrayList<>();
    protected Map<ObjectInstanceHandle, ObjectClassHandle> instanceClassMap = new HashMap<>();
    List<Checkout> checkouts = new ArrayList<>();
    List<Queue> queues = new ArrayList<>();
    ObjectClassHandle clientObjectHandle;
    AttributeHandle clientIsPrivileged;
    AttributeHandle clientEndShoppingTime;
    AttributeHandle clientId;
    InteractionClassHandle openCheckoutInteractionHandle;
    ParameterHandle openCheckoutCheckoutId;
    InteractionClassHandle closeCheckoutInteractionHandle;
    ParameterHandle closeCheckoutCheckoutId;
    ObjectClassHandle checkoutObjectHandle;
    AttributeHandle checkoutIsOpened;
    AttributeHandle checkoutQueueId;
    AttributeHandle checkoutId;
    ObjectClassHandle queueObjectHandle;
    AttributeHandle queueMaxSize;
    AttributeHandle queueCurrentSize;
    AttributeHandle queueId;
    InteractionClassHandle endServiceInteractionHandle;
    ParameterHandle endServiceCheckoutId;
    ParameterHandle endServiceClientId;
    List<Queue> queuesToClose = new ArrayList<>();
    private RTIambassador rtiamb;
    private ManagerAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;
    private int servicedClientsNo = 0;

    public static void main(String[] args) {
        String federateName = "manager";
        if (args.length != 0) {
            federateName = args[0];
        }

        try {
            new ManagerFederate().runFederate(federateName);
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

    private void log(String message) {
        System.out.println("Manager   : " + message);
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

    public void runFederate(String federateName) throws Exception {
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();
        log("Connecting...");
        fedamb = new ManagerAmbassador(this);
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
        while (fedamb.isAnnounced == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
        waitForUser();
        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
        enableTimePolicy();
        log("Time Policy Enabled");
        publishAndSubscribe();
        log("Published and Subscribed");

        System.out.println("***************************************************" +
                "***********************************************");
        while (fedamb.running) {
            advanceTime(1.0);
            System.out.println();
            log("Time Advanced to " + fedamb.federateTime);
            doThings();
        }
        cleanUpAfterSimulation();
    }

    private void cleanUpAfterSimulation() throws InvalidResignAction, OwnershipAcquisitionPending, FederateOwnsAttributes, FederateNotExecutionMember, NotConnected, CallNotAllowedFromWithinCallback, RTIinternalError {
        rtiamb.resignFederationExecution(ResignAction.DELETE_OBJECTS);
        log("Resigned from Federation");
        try {
            rtiamb.destroyFederationExecution("ExampleFederation");
            log("Destroyed Federation");
        } catch (FederationExecutionDoesNotExist dne) {
            log("No need to destroy federation, it doesn't exist");
        } catch (FederatesCurrentlyJoined fcj) {
            log("Didn't destroy federation, federates still joined");
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

    private void publishAndSubscribe() throws RTIexception {
        // discover object klient
        clientObjectHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Client");
        clientIsPrivileged = rtiamb.getAttributeHandle(clientObjectHandle, "isPrivileged");
        clientEndShoppingTime = rtiamb.getAttributeHandle(clientObjectHandle, "endShoppingTime");
        clientId = rtiamb.getAttributeHandle(clientObjectHandle, "clientId");
        AttributeHandleSet clientAttributes = rtiamb.getAttributeHandleSetFactory().create();
        clientAttributes.add(clientIsPrivileged);
        clientAttributes.add(clientEndShoppingTime);
        clientAttributes.add(clientId);
        rtiamb.subscribeObjectClassAttributes(clientObjectHandle, clientAttributes);

        // otwarcie kasy
        openCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.OpenCheckout");
        openCheckoutCheckoutId = rtiamb.getParameterHandle(openCheckoutInteractionHandle, "checkoutId");
        rtiamb.publishInteractionClass(openCheckoutInteractionHandle);

        // zamkniecie kasy
        closeCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.CloseCheckout");
        closeCheckoutCheckoutId = rtiamb.getParameterHandle(closeCheckoutInteractionHandle, "checkoutId");
        rtiamb.publishInteractionClass(closeCheckoutInteractionHandle);

        // discover object kasa
        checkoutObjectHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Checkout");
        checkoutIsOpened = rtiamb.getAttributeHandle(checkoutObjectHandle, "isOpened");
        checkoutQueueId = rtiamb.getAttributeHandle(checkoutObjectHandle, "queueId");
        checkoutId = rtiamb.getAttributeHandle(checkoutObjectHandle, "checkoutId");
        AttributeHandleSet checkoutAttributes = rtiamb.getAttributeHandleSetFactory().create();
        checkoutAttributes.add(checkoutIsOpened);
        checkoutAttributes.add(checkoutId);
        checkoutAttributes.add(checkoutQueueId);
        rtiamb.subscribeObjectClassAttributes(checkoutObjectHandle, checkoutAttributes);

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

        // zakoczenie obslugi
        endServiceInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndService");
        endServiceCheckoutId = rtiamb.getParameterHandle(endServiceInteractionHandle, "checkoutId");
        endServiceClientId = rtiamb.getParameterHandle(endServiceInteractionHandle, "clientId");
        rtiamb.subscribeInteractionClass(endServiceInteractionHandle);
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
        queues.forEach(System.out::println);
        checkouts.forEach(System.out::println);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);
        boolean hasBeenOpen = false;
        int queuesMaxSizeSum = queues.stream().mapToInt(Queue::getMaxSize).sum();
        if (queuesMaxSizeSum < (clients.size() - servicedClientsNo)) {
            Optional<Checkout> closedCheckout = checkouts.stream().filter(checkout -> !checkout.isOpen()).findFirst();
            if (!closedCheckout.isPresent()) {
                sendOpenCheckoutInteraction(Checkout.count.incrementAndGet(), time);
                log("SEND INTERACTION: OPEN CHECKOUT (new checkout) (" + Checkout.count.get() + ")");
                hasBeenOpen = true;
            } else {
                closedCheckout.get().setOpen(true);
                log("SEND INTERACTION: OPEN CHECKOUT (existing)");
                sendOpenCheckoutInteraction(closedCheckout.get().getQueueId(), time);
                hasBeenOpen = true;
            }
        }
        List<Checkout> collect = checkouts.stream().filter(Checkout::isOpen).collect(Collectors.toList());
        if (collect.size() > 1) {
            if (!hasBeenOpen) {
                List<Queue> emptyQueues = queues.stream()
                        .filter(queue -> queue.getCurrentSize() < 1).collect(Collectors.toList());
                for (Checkout checkout : checkouts) {
                    for (Queue emptyQueue : emptyQueues) {
                        if (checkout.getQueueId() == emptyQueue.getQueueId()) {
                            if (checkout.isOpen() && (queuesMaxSizeSum < (clients.size() - emptyQueue.getMaxSize()))) {
                                sendCloseCheckoutInteraction(emptyQueue.getQueueId(), time);
                                emptyQueue.setMaxSize(0);
                            }
                        }
                    }
                }
            }
        }
    }

    private void sendCloseCheckoutInteraction(int checkoutId, HLAfloat64Time time) throws RTIexception {
        log("SEND INTERACTION: CLOSE CHECKOUT (" + checkoutId + ")");
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
        ParameterHandle idHandle = rtiamb.getParameterHandle(closeCheckoutInteractionHandle, "checkoutId");
        parameters.put(idHandle, encoderFactory.createHLAinteger32BE(checkoutId).toByteArray());
        rtiamb.sendInteraction(closeCheckoutInteractionHandle, parameters, generateTag(), time);
    }

    private void sendOpenCheckoutInteraction(int checkoutId, HLAfloat64Time time) throws RTIexception {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
        ParameterHandle idHandle = rtiamb.getParameterHandle(openCheckoutInteractionHandle, "checkoutId");
        parameters.put(idHandle, encoderFactory.createHLAinteger32BE(checkoutId).toByteArray());
        rtiamb.sendInteraction(openCheckoutInteractionHandle, parameters, generateTag(), time);
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    void discoverClient(ObjectInstanceHandle clientHandle) {
        Client client = new Client(clientHandle);
        clients.add(client);
        log("ARRIVED CLIENT");
//        log("ARRIVED CLIENT size: " + clients.size() +
//                " minus " + servicedClientsNo + " = " + (clients.size() - servicedClientsNo));
    }

    void discoverQueue(ObjectInstanceHandle queueHandle) {
        queues.add(new Queue(queueHandle));
    }

    void discoverCheckout(ObjectInstanceHandle queueHandle) {
        checkouts.add(new Checkout(queueHandle));
    }

    void updateClient(ObjectInstanceHandle handle, int clientId, boolean isPrivileged, int endShoppingTime) {
        for (Client client : clients) {
            if (client.getRtiHandler().equals(handle)) {
                client.setClientId(clientId);
                client.setPrivileged(isPrivileged);
                client.setEndShoppingTime(endShoppingTime);
            }
        }
    }

    void updateQueue(ObjectInstanceHandle handle, int queueId, int queueMaxSize, int queueCurrentSize) {
        for (Queue queue : queues) {
            if (queue.getRtiHandler().equals(handle)) {
                queue.setQueueId(queueId);
                queue.setMaxSize(queueMaxSize);
                queue.setCurrentSize(queueCurrentSize);
            }
        }
    }

    void updateCheckout(ObjectInstanceHandle handle, int checkoutId, boolean open, int queueId) {
        for (Checkout queue : checkouts) {
            if (queue.getRtiHandler().equals(handle)) {
                queue.setQueueId(queueId);
                queue.setOpen(open);
                queue.setCheckoutId(checkoutId);
            }
        }
    }

    void receiveEndServiceInteraction(int checkoutId, int clientId) {
        servicedClientsNo++;
    }
}