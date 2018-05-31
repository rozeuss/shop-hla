package shop.rti.manager;

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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("Duplicates")
public class ManagerFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    protected EncoderFactory encoderFactory;
    protected ArrayList<Client> clients = new ArrayList<>();
    protected ArrayList<Checkout> checkouts = new ArrayList<>();
    protected ObjectClassHandle clientObjectHandle;
    protected AttributeHandle clientIsPrivileged;
    protected AttributeHandle clientNumberOfProducts;
    protected AttributeHandle clientId;
    protected InteractionClassHandle openCheckoutInteractionHandle;
    protected ParameterHandle openCheckoutCheckoutId;
    protected InteractionClassHandle closeCheckoutInteractionHandle;
    protected ParameterHandle closeCheckoutCheckoutId;
    private RTIambassador rtiamb;
    private ManagerAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;

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


//		enableTimePolicy();
//		log( "Time Policy Enabled" );


        publishAndSubscribe();
        log("Published and Subscribed");


        while (fedamb.running) {
            TimeUnit.SECONDS.sleep(2);
            log("XD?");
            sendInteraction();
//            advanceTime(1.0); // TODO
//            log( "Time Advanced to " + fedamb.federateTime );
        }
    }

    /**
     * This method will attempt to enable the various time related properties for
     * the federate
     */
    private void enableTimePolicy() throws Exception {

        HLAfloat64Interval lookahead = timeFactory.makeInterval(fedamb.federateLookahead);


        this.rtiamb.enableTimeRegulation(lookahead);

        // tick until we get the callback
        while (fedamb.isRegulating == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }


        this.rtiamb.enableTimeConstrained();


        while (fedamb.isConstrained == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private void publishAndSubscribe() throws RTIexception {
//      discover object klient
        clientObjectHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Client");
        log("clientHandle id: " + clientObjectHandle);
        clientIsPrivileged = rtiamb.getAttributeHandle(clientObjectHandle, "isPrivileged");
        clientNumberOfProducts = rtiamb.getAttributeHandle(clientObjectHandle, "numberOfProducts");
        clientId = rtiamb.getAttributeHandle(clientObjectHandle, "clientId");
        AttributeHandleSet clientAttributes = rtiamb.getAttributeHandleSetFactory().create();
        clientAttributes.add(clientIsPrivileged);
        clientAttributes.add(clientNumberOfProducts);
        clientAttributes.add(clientId);
        rtiamb.subscribeObjectClassAttributes(clientObjectHandle, clientAttributes);

//      otwarcie kasy
        openCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.OpenCheckout");
        openCheckoutCheckoutId = rtiamb.getParameterHandle(openCheckoutInteractionHandle, "checkoutId");
        rtiamb.publishInteractionClass(openCheckoutInteractionHandle);

//      zamkniecie kasy
        closeCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.CloseCheckout");
        closeCheckoutCheckoutId = rtiamb.getParameterHandle(closeCheckoutInteractionHandle, "checkoutId");
        rtiamb.publishInteractionClass(closeCheckoutInteractionHandle);
    }

    private void updateAttributeValues(ObjectInstanceHandle objectHandle) throws RTIexception {

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

    private void sendInteraction() throws RTIexception {
//        sendOpenCheckoutInteraction(999);
        int openedCheckouts = checkouts.size();
        for (Client client : clients) {
            log(client.toString());
        }
        // 10 ludzi na 1 kolejke
        if (openedCheckouts * 10 < clients.size()) {
            Optional<Checkout> closedCheckout = checkouts.stream().filter(checkout -> !checkout.isOpen()).findFirst();
            if (!closedCheckout.isPresent()) {
                Checkout checkout = new Checkout(Checkout.count.incrementAndGet(),
                        Queue.count.incrementAndGet(), true, null);
                checkouts.add(checkout);
                sendOpenCheckoutInteraction(checkout.getCheckoutId());
                log("SEND INTERACTION: Open Checkout (new checkout)");
            } else {
                Checkout checkout = checkouts.get(closedCheckout.get().getCheckoutId());
                checkout.setOpen(true);
                sendOpenCheckoutInteraction(closedCheckout.get().getQueueId());
            }
        } else if (openedCheckouts > 0){
            Checkout checkout = checkouts.get(Checkout.count.get());
            checkout.setOpen(false);
            sendCloseCheckoutInteraction(checkout.getCheckoutId());
            log("SEND INTERACTION: Open Checkout");
        }
    }

    private void sendCloseCheckoutInteraction(int checkoutId) throws RTIexception {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
        ParameterHandle idHandle = rtiamb.getParameterHandle(closeCheckoutInteractionHandle, "checkoutId");
        parameters.put(idHandle, encoderFactory.createHLAinteger32BE(checkoutId).toByteArray());
        rtiamb.sendInteraction(closeCheckoutInteractionHandle, parameters, "CLOSE CHECKOUT".getBytes());
    }

    private void sendOpenCheckoutInteraction(int checkoutId) throws RTIexception {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
        ParameterHandle idHandle = rtiamb.getParameterHandle(openCheckoutInteractionHandle, "checkoutId");
        parameters.put(idHandle, encoderFactory.createHLAinteger32BE(checkoutId).toByteArray());
        rtiamb.sendInteraction(openCheckoutInteractionHandle, parameters, "OPEN CHECKOUT".getBytes());
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    protected void discoverClient(ObjectInstanceHandle client) {
        log("TODO " + client.toString());
        log("ROZCZYTAJ DANE KLIENTA");

        clients.add(new Client(client));
        clients.stream().forEach(System.out::println);
    }

    protected void updateClient(ObjectInstanceHandle client, int id) {
        log(client.toString());
    }

}