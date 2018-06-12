package shop.rti.client;

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
import shop.utils.HandlersHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ClientFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    public static final int CLIENT_ARRIVAL_PROBABILITY = 2;
    protected EncoderFactory encoderFactory;
    protected ObjectClassHandle clientObjectHandle;
    protected InteractionClassHandle chooseQueueInteractionHandle;
    protected ParameterHandle chooseQueueClientId;
    protected ParameterHandle chooseQueueCheckoutId;
    protected InteractionClassHandle startServiceInteractionHandle;
    protected ParameterHandle startServiceCheckoutIdParameter;
    protected ParameterHandle startServiceClientIdParameter;
    protected ObjectClassHandle checkoutObjectHandle;
    protected AttributeHandle checkoutIsOpened;
    protected AttributeHandle checkoutId;
    protected AttributeHandle checkoutQueueId;
    protected List<Client> clients = new ArrayList<>();
    protected List<Checkout> checkouts = new ArrayList<>();
    protected List<Queue> queues = new ArrayList<>();
    protected Map<ObjectInstanceHandle, ObjectClassHandle> instanceClassMap = new HashMap<>();
    protected AttributeHandle clientIsPrivileged;
    protected AttributeHandle clientEndShoppingTime;
    protected AttributeHandle clientId;
    protected ObjectClassHandle queueObjectHandle;
    protected AttributeHandle queueMaxSize;
    protected AttributeHandle queueCurrentSize;
    protected AttributeHandle queueId;
    boolean hasClientArrived = false;
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

    public void runFederate(String federateName) throws Exception {
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
        while (fedamb.isAnnounced == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        /////////////////////////////
        // 7. enable time policies //
        /////////////////////////////
        // in this section we enable/disable all time policies
        // note that this step is optional!
        enableTimePolicy();
        log("Time Policy Enabled"); // TODO enableTimePolicy i advanceTime (evokeMultipleCallbacks) do synchro czasowej

        publishAndSubscribe();
        log("Published and Subscribed");

//        for (int i = 0; i < 4; i++) {
//            createClientObject();
//        }

        while (fedamb.running) {
            TimeUnit.SECONDS.sleep(1);
            advanceTime(1.0);
            log("Time Advanced to " + fedamb.federateTime);

            doThings();

//            rtiamb.evokeMultipleCallbacks(0.1, 0.2);

        }
    }

    private void enableTimePolicy() throws Exception {
        HLAfloat64Interval lookahead = timeFactory.makeInterval(fedamb.federateLookahead);
        this.rtiamb.enableTimeRegulation(lookahead);

        while (fedamb.isRegulating == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        this.rtiamb.enableTimeConstrained();

        while (fedamb.isConstrained == false) {
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
        HandlersHelper.addObjectClassHandler("HLAobjectRoot.Client", clientObjectHandle.hashCode());


        // wybranie kolejki
        chooseQueueInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ChooseQueue");
        chooseQueueCheckoutId = rtiamb.getParameterHandle(chooseQueueInteractionHandle, "checkoutId");
        chooseQueueClientId = rtiamb.getParameterHandle(chooseQueueInteractionHandle, "clientId");
        rtiamb.publishInteractionClass(chooseQueueInteractionHandle);
        HandlersHelper.addInteractionClassHandler("HLAinteractionRoot.ChooseQueue",
                chooseQueueInteractionHandle.hashCode());


        // rozpoczecie obslugi
        startServiceInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.StartService");
        startServiceCheckoutIdParameter = rtiamb.getParameterHandle(startServiceInteractionHandle, "checkoutId");
        startServiceClientIdParameter = rtiamb.getParameterHandle(startServiceInteractionHandle, "clientId");
        rtiamb.subscribeInteractionClass(startServiceInteractionHandle);
        HandlersHelper.addInteractionClassHandler("HLAinteractionRoot.StartService",
                startServiceInteractionHandle.hashCode());


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
        HandlersHelper.addObjectClassHandler("HLAobjectRoot.Checkout",
                checkoutObjectHandle.hashCode());

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

    protected void updateClientAttributeValues(Client client, HLAfloat64Time time) throws RTIexception {
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(3);
        attributes.put(clientId, DecoderUtils.encodeInt(encoderFactory, client.getClientId()));
        attributes.put(clientEndShoppingTime, DecoderUtils.encodeInt(encoderFactory, client.getEndShoppingTime()));
        attributes.put(clientIsPrivileged, DecoderUtils.encodeBoolean(encoderFactory, client.isPrivileged()));
        rtiamb.updateAttributeValues(client.getRtiHandler(), attributes, generateTag(), time);
    }

    protected void updateCheckout(Checkout checkout) throws RTIexception {
        int index = -1;
        for (int i = 0; i < checkouts.size(); i++) {
            if (checkouts.get(i).getRtiHandler().equals(checkout.getRtiHandler()))
                index = i;
        }
        if (index != -1) {
            Checkout checkoutToUpdate = checkouts.get(index);
            if (checkoutToUpdate.getCheckoutId() == 0 || checkoutToUpdate.getCheckoutId() == checkout.getCheckoutId()) {
                checkoutToUpdate.setCheckoutId(checkout.getCheckoutId());
                checkoutToUpdate.setQueueId(checkout.getQueueId());
                checkoutToUpdate.setOpen(checkout.isOpen());
            }
            if (checkout.getQueueId() == -1)
                checkouts.remove(checkoutToUpdate);
        }

    }

    private void doThings() throws RTIexception {
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);
        Queue queue = queues.stream().min(Comparator.comparingInt(Queue::getCurrentSize)).orElse(null);
        List<Client> shoppingClients = clients.stream()
                .filter(((Predicate<Client>) Client::isWaitingInQueue).negate())
                .collect(Collectors.toList());
        for (Client client : shoppingClients) {
            if (hasClientFinishedShopping(client.getEndShoppingTime())) {
                if (queue != null) {
                    if (queue.getCurrentSize() < queue.getMaxSize()) {
                        if (!shoppingClients.isEmpty()) {
                            sendChooseQueueInteraction(queue, client, time);
                            client.setWaitingInQueue(true);
                            System.out.println(client);
                        }
                    }
                }

            }
        }
        hasClientArrived = random.nextInt(CLIENT_ARRIVAL_PROBABILITY) == 0;
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
        log("WYBIERAM KOLEJKE");
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

    private short getTimeAsShort() {
        return (short) fedamb.federateTime;
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    private void createClientObject() {
        log("New client arrived");
        Client client = new Client();
        ObjectInstanceHandle clientInstanceHandle = null;
        try {
            clientInstanceHandle = registerObject();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
        client.setRtiHandler(clientInstanceHandle);
        client.setClientId(Client.count.getAndIncrement());
        client.setEndShoppingTime(random.nextInt(Client.MAX_PRODUCTS) + 1 + (int) fedamb.federateTime);
        client.setPrivileged(random.nextBoolean());
        log(client.toString());
        clients.add(client);
    }

    public void addNewCheckout(ObjectInstanceHandle checkoutHandle) {
        checkouts.add(new Checkout(checkoutHandle));
    }

    public void addNewQueue(ObjectInstanceHandle queueHandle) {
        queues.add(new Queue(queueHandle));
    }

    public void serviceClient(int checkoutId, int clientId, LogicalTime time) {
        //TODO co z checkoutId
        Client client = clients.get(clientId);
//        try {
//            deleteObject(client.getRtiHandler(), time);
//        } catch (RTIexception rtIexception) {
//            rtIexception.printStackTrace();
//        }
        clients.remove(client);
    }

    public void updateQueue(ObjectInstanceHandle handle, int queueId, int queueMaxSize, int queueCurrentSize) {
        for (Queue queue : queues) {
            if (queue.getRtiHandler().equals(handle)) {
                queue.setQueueId(queueId);
                queue.setMaxSize(queueMaxSize);
                queue.setCurrentSize(queueCurrentSize);
            }
        }
    }
}