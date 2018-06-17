package shop.rti.statistic;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("Duplicates")
public class StatisticFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    static HashMap<InteractionClassHandle, Integer> interactionsCounter = new HashMap<>();
    static HashMap<Double, Integer> timeInteractionsNoMap = new HashMap<>();
    static HashMap<ObjectClassHandle, Integer> objectsCounter = new HashMap<>();
    protected AttributeHandle queueId;
    protected AttributeHandle clientId;
    protected AttributeHandle checkoutId;
    protected Map<ObjectInstanceHandle, ObjectClassHandle> instanceClassMap = new HashMap<>();
    protected EncoderFactory encoderFactory;
    ParameterHandle openCheckoutCheckoutId;
    InteractionClassHandle openCheckoutInteractionHandle;
    ObjectClassHandle queueObjectHandle;
    AttributeHandle queueMaxSize;
    AttributeHandle queueCurrentSize;
    ObjectClassHandle clientObjectHandle;
    AttributeHandle clientIsPrivileged;
    AttributeHandle clientEndShoppingTime;
    ObjectClassHandle checkoutObjectHandle;
    AttributeHandle checkoutIsOpened;
    AttributeHandle checkoutQueueId;
    InteractionClassHandle chooseQueueInteractionHandle;
    ParameterHandle chooseQueueCheckoutId;
    ParameterHandle chooseQueueClientId;
    InteractionClassHandle closeCheckoutInteractionHandle;
    ParameterHandle closeCheckoutCheckoutId;
    InteractionClassHandle endServiceInteractionHandle;
    ParameterHandle endServiceCheckoutId;
    ParameterHandle endServiceClientId;
    InteractionClassHandle clientExitInteractionHandle;
    ParameterHandle clientExitCheckoutIdParameter;
    ParameterHandle clientExitClientIdParameter;
    private ArrayList<Checkout> checkouts = new ArrayList<>();
    private ArrayList<Queue> queues = new ArrayList<>();
    private ArrayList<Client> clients = new ArrayList<>();
    private RTIambassador rtiamb;
    private StatisticAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;

    public static void main(String[] args) {
        String federateName = "statistic";
        if (args.length != 0) {
            federateName = args[0];
        }
        try {
            new StatisticFederate().runFederate(federateName);
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

    private void log(String message) {
        System.out.println("Statistic   : " + message);
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
        fedamb = new StatisticAmbassador(this);
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
//
        publishAndSubscribe();
        log("Published and Subscribed");

        showStatisticLegend();
        System.out.println("***************************************************" +
                "***********************************************");
        while (fedamb.running) {
            advanceTime(1.0);
            System.out.println("");
            log("Time Advanced to " + fedamb.federateTime);
            doThings();
        }
    }

    private void showStatisticLegend() {
        log("INTERACTIONS HANDLERS");
        log("---------------------------------------------------------------------------------------");
        log("CHOOSE QUEUE:   [" + chooseQueueInteractionHandle + "]");
        log("OPEN CHECKOUT:  [" + openCheckoutInteractionHandle + "]");
        log("CLOSE CHECKOUT: [" + closeCheckoutInteractionHandle + "]");
        log("END SERVICE:    [" + endServiceInteractionHandle + "]");
        log("START SERVICE:  [" + clientExitInteractionHandle + "]");
        log("---------------------------------------------------------------------------------------");
        log("OBJECTS HANDLERS");
        log("CHECKOUT     :  [" + checkoutObjectHandle + "]");
        log("QUEUE        :  [" + queueObjectHandle + "]");
        log("CLIENT       :  [" + clientObjectHandle + "]");
        log("---------------------------------------------------------------------------------------");
    }

    private void doThings() {
        log("");
        log("INTERACTIONS COUNTER");
        log(interactionsCounter.toString());
        log("OBJECTS COUNTER");
        log(objectsCounter.toString());
        Optional<Map.Entry<Double, Integer>> maxInteractionsInTime =
                timeInteractionsNoMap.entrySet().stream().max(Map.Entry.comparingByValue());
        maxInteractionsInTime.ifPresent(logicalTimeIntegerEntry ->
                log("MOST INTERACTIONS (" + logicalTimeIntegerEntry.getValue()
                        + ") OCCURRED IN " + logicalTimeIntegerEntry.getKey() + " TIME UNIT"));
        log("AVERAGE SERVICE TIME");
        //TODO
        clientStatistic();
        checkoutStatistic();
        queueStatistic();
    }

    private void clientStatistic() {
        log("");
        log("CLIENT STATISTIC");
        log("---------------------------------------------------------------------------------------");
        long privilegedClients = clients.stream().filter(Client::isPrivileged).count();
        log("              CLIENTS: (" + clients.size() + ")");
        log("   PRIVILEGED CLIENTS: (" + privilegedClients + ")");
        log(" UNPRIVILEGED CLIENTS: (" + (clients.size() - privilegedClients) + ")");
        //TODO
        double averageShoppingTime = clients.stream()
                .mapToDouble(c -> c.getEndShoppingTime() - c.getArrivalTime()).average().orElse(0);
        log("AVERAGE SHOPPING TIME: (" + (averageShoppingTime) + ")");
    }

    private void queueStatistic() {
        log("");
        log("QUEUE STATISTIC");
        log("---------------------------------------------------------------------------------------");
        log("               QUEUES: (" + queues.size() + ")");
        int maxSizeOfOpenQueue = queues.stream().mapToInt(Queue::getMaxSize).max().orElse(0);
        log("  OPEN QUEUE MAX SIZE: (" + maxSizeOfOpenQueue + ")");
        int openQueuesSizeSum = queues.stream().mapToInt(Queue::getMaxSize).sum();
        log("  OPEN QUEUE SIZE SUM: (" + openQueuesSizeSum + ")");
        int allQueuesSizeSum = queues.stream().mapToInt(Queue::getOriginalMaxSize).sum();
        log("       QUEUE SIZE SUM: (" + allQueuesSizeSum + ")");
        int currentSizeSum = queues.stream().mapToInt(Queue::getCurrentSize).sum();
        log("     CURRENT SIZE SUM: (" + currentSizeSum + ")");
        double averageQueueSize = queues.stream().mapToInt(Queue::getCurrentSize).average().orElse(0);
        log("   AVERAGE QUEUE SIZE: (" + averageQueueSize + ")");
    }

    private void checkoutStatistic() {
        log("");
        log("CHECKOUT STATISTIC");
        log("---------------------------------------------------------------------------------------");
        log("            CHECKOUTS: (" + checkouts.size() + ")");
        long openCheckouts = checkouts.stream().filter(Checkout::isOpen).count();
        log("       OPEN CHECKOUTS: (" + openCheckouts + ")");
        log("     CLOSED CHECKOUTS: (" + (checkouts.size() - openCheckouts) + ")");
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
        //otwarcie kasy
        openCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.OpenCheckout");
        openCheckoutCheckoutId = rtiamb.getParameterHandle(openCheckoutInteractionHandle, "checkoutId");
        rtiamb.subscribeInteractionClass(openCheckoutInteractionHandle);

        //wybranie kolejki
        chooseQueueInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.ChooseQueue");
        chooseQueueCheckoutId = rtiamb.getParameterHandle(chooseQueueInteractionHandle, "checkoutId");
        chooseQueueClientId = rtiamb.getParameterHandle(chooseQueueInteractionHandle, "clientId");
        rtiamb.subscribeInteractionClass(chooseQueueInteractionHandle);

        //zamkniecie kasy
        closeCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.CloseCheckout");
        closeCheckoutCheckoutId = rtiamb.getParameterHandle(closeCheckoutInteractionHandle, "checkoutId");
        rtiamb.subscribeInteractionClass(closeCheckoutInteractionHandle);

        //zakonczenie obslugi
        endServiceInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndService");
        endServiceCheckoutId = rtiamb.getParameterHandle(endServiceInteractionHandle, "checkoutId");
        endServiceClientId = rtiamb.getParameterHandle(endServiceInteractionHandle, "clientId");
        rtiamb.subscribeInteractionClass(endServiceInteractionHandle);

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

    private void advanceTime(double timestep) throws RTIexception {
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(time);
        while (fedamb.isAdvancing) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    void addNewClientObject(ObjectInstanceHandle theObject) {
        clients.add(new Client(theObject));
    }

    void addNewCheckoutObject(ObjectInstanceHandle theObject) {
        checkouts.add(new Checkout(theObject));
    }

    void addNewQueueObject(ObjectInstanceHandle theObject) {
        queues.add(new Queue(theObject));
    }

    void updateClient(ObjectInstanceHandle handle, int clientId, boolean isPrivileged, int endShoppingTime,
                      LogicalTime time) {
        Double timeInDouble = Double.valueOf(time.toString());
        for (Client client : clients) {
            if (client.getRtiHandler().equals(handle)) {
                client.setClientId(clientId);
                client.setPrivileged(isPrivileged);
                client.setEndShoppingTime(endShoppingTime);
                if (client.getArrivalTime() == -1) {
                    client.setArrivalTime(timeInDouble.intValue() - 1);
                }
            }
        }
    }

    void updateQueue(ObjectInstanceHandle handle, int queueId, int queueMaxSize, int queueCurrentSize) {
        for (Queue queue : queues) {
            if (queue.getRtiHandler().equals(handle)) {
                queue.setQueueId(queueId);
                queue.setMaxSize(queueMaxSize);
                queue.setCurrentSize(queueCurrentSize);
                if (queueMaxSize != 0) {
                    queue.setOriginalMaxSize(queueMaxSize);
                }
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

    void receiveEndServiceInteraction(InteractionClassHandle interactionClass, LogicalTime time,
                                      int checkoutId, int clientId) {
        timeInteractionsNoMap.merge(Double.valueOf(time.toString()), 1, Integer::sum);

    }

    void receiveOpenCheckoutInteraction(InteractionClassHandle interactionClass, LogicalTime time,
                                        int checkoutId) {
        timeInteractionsNoMap.merge(Double.valueOf(time.toString()), 1, Integer::sum);
    }

    void receiveCloseCheckoutInteraction(InteractionClassHandle interactionClass, LogicalTime time,
                                         int checkoutId) {
        timeInteractionsNoMap.merge(Double.valueOf(time.toString()), 1, Integer::sum);
    }

    void receiveClientExitInteraction(InteractionClassHandle interactionClass, LogicalTime time,
                                        int checkoutId, int clientId) {
        timeInteractionsNoMap.merge(Double.valueOf(time.toString()), 1, Integer::sum);
    }

    void receiveChooseQueueInteraction(InteractionClassHandle interactionClass, LogicalTime time,
                                       int queueId, int clientId) {
        timeInteractionsNoMap.merge(Double.valueOf(time.toString()), 1, Integer::sum);
    }
}