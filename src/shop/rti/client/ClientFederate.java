package shop.rti.client;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.*;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static shop.object.Client.CLIENT_ARRIVAL_PROBABILITY;
import static shop.object.Client.PRIVILEGED_CLIENT_PROBABILITY;

@SuppressWarnings("Duplicates")
public class ClientFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    protected EncoderFactory encoderFactory;
    AttributeHandle queueId;
    ObjectClassHandle clientObjectHandle;
    InteractionClassHandle chooseQueueInteractionHandle;
    ParameterHandle chooseQueueClientId;
    ParameterHandle chooseQueueCheckoutId;
    InteractionClassHandle clientExitInteractionHandle;
    ParameterHandle clientExitCheckoutIdParameter;
    ParameterHandle clientExitClientIdParameter;
    ObjectClassHandle checkoutObjectHandle;
    AttributeHandle checkoutIsOpened;
    AttributeHandle checkoutId;
    AttributeHandle checkoutQueueId;
    List<Client> clients = new ArrayList<>();
    List<Client> clientsToDelete = new ArrayList<>();
    List<Checkout> checkouts = new ArrayList<>();
    List<Queue> queues = new ArrayList<>();
    Map<ObjectInstanceHandle, ObjectClassHandle> instanceClassMap = new HashMap<>();
    AttributeHandle clientIsPrivileged;
    AttributeHandle clientEndShoppingTime;
    AttributeHandle clientId;
    ObjectClassHandle queueObjectHandle;
    AttributeHandle queueMaxSize;
    AttributeHandle queueCurrentSize;
    private Random random = new Random();
    private RTIambassador rtiamb;
    private ClientAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;

    public static void main(String[] args) {
        String federateName = "client";
        if (args.length != 0) {
            federateName = args[0];
        }
        try {
            new ClientFederate().runFederate(federateName);
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

    private void log(String message) {
        System.out.println("Client   : " + message);
    }

    private void waitForUser() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runFederate(String federateName) throws Exception {
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();
        log("Connecting...");
        fedamb = new ClientAmbassador(this);
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

        System.out.println("***************************************************" +
                "***********************************************");
        while (fedamb.running) {
            TimeUnit.MILLISECONDS.sleep(500 * 2);
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
        // register object klient
        clientObjectHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Client");
        clientIsPrivileged = rtiamb.getAttributeHandle(clientObjectHandle, "isPrivileged");
        clientEndShoppingTime = rtiamb.getAttributeHandle(clientObjectHandle, "endShoppingTime");
        clientId = rtiamb.getAttributeHandle(clientObjectHandle, "clientId");
        AttributeHandleSet clientAttributes = rtiamb.getAttributeHandleSetFactory().create();
        clientAttributes.add(clientIsPrivileged);
        clientAttributes.add(clientEndShoppingTime);
        clientAttributes.add(clientId);
        rtiamb.publishObjectClassAttributes(clientObjectHandle, clientAttributes);
        rtiamb.subscribeObjectClassAttributes(clientObjectHandle, clientAttributes);

        // wybranie kolejki
        chooseQueueInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ChooseQueue");
        chooseQueueCheckoutId = rtiamb.getParameterHandle(chooseQueueInteractionHandle, "checkoutId");
        chooseQueueClientId = rtiamb.getParameterHandle(chooseQueueInteractionHandle, "clientId");
        rtiamb.publishInteractionClass(chooseQueueInteractionHandle);

        // rozpoczecie obslugi
        clientExitInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ClientExit");
        clientExitCheckoutIdParameter = rtiamb.getParameterHandle(clientExitInteractionHandle, "checkoutId");
        clientExitClientIdParameter = rtiamb.getParameterHandle(clientExitInteractionHandle, "clientId");
        rtiamb.subscribeInteractionClass(clientExitInteractionHandle);

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
    }

    private ObjectInstanceHandle registerObject() throws RTIexception {
        return rtiamb.registerObjectInstance(clientObjectHandle);
    }

    private void updateClientAttributeValues(Client client, HLAfloat64Time time) throws RTIexception {
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(3);
        attributes.put(clientId, DecoderUtils.encodeInt(encoderFactory, client.getClientId()));
        attributes.put(clientEndShoppingTime, DecoderUtils.encodeInt(encoderFactory, client.getEndShoppingTime()));
        attributes.put(clientIsPrivileged, DecoderUtils.encodeBoolean(encoderFactory, client.isPrivileged()));
        rtiamb.updateAttributeValues(client.getRtiHandler(), attributes, generateTag(), time);
    }

    private void doThings() throws RTIexception {
        queues.forEach(System.out::println);
//        clients.forEach(System.out::println);
        checkouts.forEach(System.out::println);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);
        for (Client client : clientsToDelete) {
            deleteObject(client.getRtiHandler(), time);
            clients.stream().filter(c -> c.equals(client)).findFirst().ifPresent(c -> clients.remove(c));
        }
        clientsToDelete.clear();
        List<Client> shoppingClients = clients.stream()
                .filter(((Predicate<Client>) Client::isWaitingInQueue).negate())
                .collect(Collectors.toList());
        List<Client> endShoppingClients = shoppingClients.stream()
                .filter(client -> hasClientFinishedShopping(client.getEndShoppingTime()))
                .collect(Collectors.toList());
        for (Client client : endShoppingClients) {
            List<Queue> openQueues = queues.stream()
                    .filter(queue -> queue.getMaxSize() > 0)
                    .sorted(Comparator.comparing(Queue::getCurrentSize))
                    .collect(Collectors.toList());
            for (Queue queue : openQueues) {
                if (queue.getMaxSize() > queue.getCurrentSize() + 1) {
                    sendChooseQueueInteraction(queue, client, time);
                    queue.setCurrentSize(queue.getCurrentSize() + 1);
                    client.setWaitingInQueue(true);
                    break;
                }
            }
        }
        boolean hasClientArrived = random.nextInt(CLIENT_ARRIVAL_PROBABILITY) == 0;
        if (hasClientArrived) {
            createClientObject();
        }
        for (Client client : clients) {
            updateClientAttributeValues(client, time);
        }
    }

    private boolean hasClientFinishedShopping(int endShoppingTime) {
        return endShoppingTime <= fedamb.federateTime;
    }

    private void sendChooseQueueInteraction(Queue queue, Client client, HLAfloat64Time time) throws RTIexception {
        log("CLIENT (" + client.getClientId() + ") " + "CHOOSING QUEUE (" + queue.getQueueId() + ")" + " " + client);
        ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
        parameterHandleValueMap.put(chooseQueueCheckoutId, encoderFactory.createHLAinteger32BE(queue.getQueueId()).toByteArray());
        parameterHandleValueMap.put(chooseQueueClientId, encoderFactory.createHLAinteger32BE(client.getClientId()).toByteArray());
        rtiamb.sendInteraction(chooseQueueInteractionHandle, parameterHandleValueMap, generateTag(), time);
    }

    private void advanceTime(double timestep) throws RTIexception {
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(time);
        while (fedamb.isAdvancing) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private void deleteObject(ObjectInstanceHandle handle, LogicalTime time) throws RTIexception {
        log("Client (" + handle + ") deleted");
        rtiamb.deleteObjectInstance(handle, generateTag(), time);
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    private void createClientObject() {
        Client client = new Client();
        ObjectInstanceHandle clientInstanceHandle = null;
        try {
            clientInstanceHandle = registerObject();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
        client.setRtiHandler(clientInstanceHandle);
        client.setClientId(Client.count.getAndIncrement());
        client.setEndShoppingTime(random.nextInt(Client.MAX_SHOPPING_TIME) + 1 + (int) fedamb.federateTime);
        if (random.nextInt(PRIVILEGED_CLIENT_PROBABILITY) == 0) {
            client.setPrivileged(true);
        }
        log("NEW CLIENT ARRIVED: " + client.toString());
        clients.add(client);
    }

    void addNewCheckout(ObjectInstanceHandle checkoutHandle) {
        System.out.println("NEW CHECKOUT");
        checkouts.add(new Checkout(checkoutHandle));
    }

    void addNewQueue(ObjectInstanceHandle queueHandle) {
        System.out.println("NEW QUEUE");
        queues.add(new Queue(queueHandle));
    }

    void serviceClient(int checkoutId, int clientId, LogicalTime time) {
//        log("CLIENT SERVICED (" + clientId + ")");
        Optional<Client> first = clients.stream()
                .filter(client -> client.getClientId() == clientId)
                .findFirst();
//        log("CZY ISTNIEJE " + first.get());
        first.ifPresent(client -> clientsToDelete.add(client));
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
}