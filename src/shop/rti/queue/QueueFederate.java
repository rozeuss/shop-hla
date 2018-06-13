package shop.rti.queue;

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
public class QueueFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    protected EncoderFactory encoderFactory;
    InteractionClassHandle openCheckoutInteractionHandle;
    ParameterHandle openCheckoutCheckoutId;
    InteractionClassHandle chooseQueueInteractionHandle;
    ParameterHandle chooseQueueCheckoutId;
    ParameterHandle chooseQueueClientId;
    InteractionClassHandle endServiceInteractionHandle;
    ParameterHandle endServiceCheckoutId;
    ParameterHandle endServiceClientId;
    ParameterHandle closeCheckoutCheckoutId;
    InteractionClassHandle closeCheckoutInteractionHandle;
    List<Queue> queues = new ArrayList<>();
    List<Queue> queuesToMake = new ArrayList<>();
    List<Client> clients = new ArrayList<>();
    Map<ObjectInstanceHandle, ObjectClassHandle> instanceClassMap = new HashMap<>();
    ObjectClassHandle queueObjectHandle;
    AttributeHandle queueMaxSize;
    AttributeHandle queueCurrentSize;
    AttributeHandle queueId;
    ObjectClassHandle clientObjectHandle;
    AttributeHandle clientIsPrivileged;
    AttributeHandle clientEndShoppingTime;
    AttributeHandle clientId;
    private Random random = new Random();
    private RTIambassador rtiamb;
    private QueueAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;

    public static void main(String[] args) {
        String federateName = "queue";
        if (args.length != 0) {
            federateName = args[0];
        }
        try {
            new QueueFederate().runFederate(federateName);
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

    private void log(String message) {
        System.out.println("Queue   : " + message);
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
        fedamb = new QueueAmbassador(this);
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

//        receiveOpenCheckoutInteraction(checkoutId);
        registerNewQueue(new Queue(Queue.count.getAndIncrement(), random.nextInt(Queue.MAX_SIZE) + 1));
        System.out.println("*************************************************");
        while (fedamb.running) {
            advanceTime(1.0);
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

    private void publishAndSubscribe() throws RTIexception {
        // register object kolejka
        queueObjectHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Queue");
        queueMaxSize = rtiamb.getAttributeHandle(queueObjectHandle, "maxSize");
        queueCurrentSize = rtiamb.getAttributeHandle(queueObjectHandle, "currentSize");
        queueId = rtiamb.getAttributeHandle(queueObjectHandle, "queueId");
        AttributeHandleSet queueAttributes = rtiamb.getAttributeHandleSetFactory().create();
        queueAttributes.add(queueMaxSize);
        queueAttributes.add(queueCurrentSize);
        queueAttributes.add(queueId);
        rtiamb.publishObjectClassAttributes(queueObjectHandle, queueAttributes);
        rtiamb.subscribeObjectClassAttributes(queueObjectHandle, queueAttributes);

        // otwarcie kasy
        openCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.OpenCheckout");
        openCheckoutCheckoutId = rtiamb.getParameterHandle(openCheckoutInteractionHandle, "checkoutId");
        rtiamb.subscribeInteractionClass(openCheckoutInteractionHandle);

        // wybranie kolejki
        chooseQueueInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ChooseQueue");
        chooseQueueCheckoutId = rtiamb.getParameterHandle(chooseQueueInteractionHandle, "checkoutId");
        chooseQueueClientId = rtiamb.getParameterHandle(chooseQueueInteractionHandle, "clientId");
        rtiamb.subscribeInteractionClass(chooseQueueInteractionHandle);

        // zakoczenie obslugi
        endServiceInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndService");
        endServiceCheckoutId = rtiamb.getParameterHandle(endServiceInteractionHandle, "checkoutId");
        endServiceClientId = rtiamb.getParameterHandle(endServiceInteractionHandle, "clientId");
        rtiamb.subscribeInteractionClass(endServiceInteractionHandle);

        // zamkniecie kasy
        closeCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.CloseCheckout");
        closeCheckoutCheckoutId = rtiamb.getParameterHandle(closeCheckoutInteractionHandle, "checkoutId");
        rtiamb.subscribeInteractionClass(closeCheckoutInteractionHandle);

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
    }

    private void updateQueueAttributeValues(Queue queue, HLAfloat64Time time) throws RTIexception {
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(3);
        attributes.put(queueId, DecoderUtils.encodeInt(encoderFactory, queue.getQueueId()));
        attributes.put(queueCurrentSize, DecoderUtils.encodeInt(encoderFactory, queue.getCurrentSize()));
        attributes.put(queueMaxSize, DecoderUtils.encodeInt(encoderFactory, queue.getMaxSize()));
        rtiamb.updateAttributeValues(queue.getRtiHandler(), attributes, generateTag(), time);
    }

    private void advanceTime(double timestep) throws RTIexception {
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(time);
        while (fedamb.isAdvancing) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private void deleteObject(ObjectInstanceHandle handle) throws RTIexception {
        rtiamb.deleteObjectInstance(handle, generateTag());
    }

    private void doThings() throws RTIexception {
        queues.forEach(System.out::println);
//        clients.forEach(System.out::println);
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);
        for (Queue queue : queuesToMake) {
            registerNewQueue(queue);
        }
        queuesToMake.clear();
        for (Queue queue : queues) {
            updateQueueAttributeValues(queue, time);
        }
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    void receiveOpenCheckoutInteraction(int checkoutId) {
        Optional<Queue> first = queues.stream().filter(queue -> queue.getQueueId() == checkoutId).findFirst();
        if (first.isPresent()) {
            openExistingQueue(first);
        } else {
            openNewQueue();
        }
    }

    private void openExistingQueue(Optional<Queue> first) {
        first.get().setMaxSize(first.get().getOriginalMaxSize());
        System.out.println("OPEN EXISTING QUEUE (" + first.get().getQueueId() + ")");
    }

    private void openNewQueue() {
        Queue queue = new Queue(Queue.count.getAndIncrement(), random.nextInt(Queue.MAX_SIZE) + 1);
        queue.setOriginalMaxSize(queue.getMaxSize());
        queuesToMake.add(queue);
        System.out.println("TO CREATE: " + queuesToMake);
    }

    private void registerNewQueue(Queue queue) {
        ObjectInstanceHandle objectInstanceHandle = null;
        try {
            objectInstanceHandle = registerObject();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
        queue.setRtiHandler(objectInstanceHandle);
        System.out.println("CREATED: " + queue);
        queues.add(queue);
    }

    private ObjectInstanceHandle registerObject() throws RTIexception {
        return rtiamb.registerObjectInstance(queueObjectHandle);
    }

    void addNewClientToQueue(int queueId, int clientId) {
        System.out.println("ADD NEW CLIENT TO QUEUE: (" + clientId + ")");
        System.out.println(queues.get(queueId));
        Optional<Client> client = clients.stream().filter(c -> c.getClientId() == clientId).findFirst();
        if (client.isPresent()) {
            Queue queue = queues.get(queueId);
            if (!client.get().isPrivileged()) {
                queue.setCurrentSize(queue.getCurrentSize() + 1);
                queue.getClients().add(client.get());
            } else {
                queue.setCurrentSize(queue.getCurrentSize() + 1);
                queue.getClients().add(0, client.get());
            }
        }
        System.out.println(queues.get(queueId));
    }

    void removeClientFromQueue(int queueId, int clientId) {
        System.out.println("REMOVE CLIENT (" + clientId + ") FROM QUEUE (" + queueId + ")");
        Queue queue = queues.get(queueId);
        System.out.println(queue);
//         queue.getClients().removeIf(client -> client.getClientId() == clientId);
        Client removed = queue.getClients().remove(0); //TODO czy na pewno poprawnie?
        if (removed != null) {
            queue.setCurrentSize(queue.getCurrentSize() - 1);
        }
        System.out.println(queues.get(queueId));
    }

    void closeCheckout(int queueId) {
        Optional<Queue> first = queues.stream().filter(queue -> queue.getQueueId() == queueId).findFirst();
        first.ifPresent(queue -> queue.setMaxSize(0));
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

    void discoverClient(ObjectInstanceHandle client) {
        clients.add(new Client(client));
    }
}