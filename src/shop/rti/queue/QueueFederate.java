/*
 *   Copyright 2012 The Portico Project
 *
 *   This file is part of portico.
 *
 *   portico is free software; you can redistribute it and/or modify
 *   it under the terms of the Common Developer and Distribution License (CDDL) 
 *   as published by Sun Microsystems. For more information see the LICENSE file.
 *   
 *   Use of this software is strictly AT YOUR OWN RISK!!!
 *   If something bad happens you do not have permission to come crying to me.
 *   (that goes for your lawyer as well)
 *
 */
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
    protected InteractionClassHandle openCheckoutInteractionHandle;
    protected ParameterHandle openCheckoutCheckoutId;
    protected InteractionClassHandle chooseQueueInteractionHandle;
    protected ParameterHandle chooseQueueCheckoutId;
    protected ParameterHandle chooseQueueClientId;
    protected InteractionClassHandle endServiceInteractionHandle;
    protected ParameterHandle endServiceCheckoutId;
    protected ParameterHandle endServiceClientId;
    protected ParameterHandle closeCheckoutCheckoutId;
    protected InteractionClassHandle closeCheckoutInteractionHandle;
    protected List<Checkout> checkouts = new ArrayList<>();
    protected List<Queue> queues = new ArrayList<>();
    protected List<Client> clients = new ArrayList<>();
    protected Map<ObjectInstanceHandle, ObjectClassHandle> instanceClassMap = new HashMap<>();
    protected ObjectClassHandle queueObjectHandle;
    protected AttributeHandle queueMaxSize;
    protected AttributeHandle queueCurrentSize;
    protected AttributeHandle queueId;
    protected ObjectClassHandle clientObjectHandle;
    protected AttributeHandle clientIsPrivileged;
    protected AttributeHandle clientNumberOfProducts;
    protected AttributeHandle clientId;
    Random random = new Random();
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

    public void runFederate(String federateName) throws Exception {

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

        createNewQueue();

        while (fedamb.running) {
//            TimeUnit.SECONDS.sleep(3);

            advanceTime(1.0); // TODO
            log("Time Advanced to " + fedamb.federateTime);

            doThings();
//            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
//            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );

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

        //      discover object klient
        clientObjectHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Client");
        clientIsPrivileged = rtiamb.getAttributeHandle(clientObjectHandle, "isPrivileged");
        clientNumberOfProducts = rtiamb.getAttributeHandle(clientObjectHandle, "numberOfProducts");
        clientId = rtiamb.getAttributeHandle(clientObjectHandle, "clientId");
        AttributeHandleSet clientAttributes = rtiamb.getAttributeHandleSetFactory().create();
        clientAttributes.add(clientIsPrivileged);
        clientAttributes.add(clientNumberOfProducts);
        clientAttributes.add(clientId);
        rtiamb.subscribeObjectClassAttributes(clientObjectHandle, clientAttributes);
    }

    protected void updateQueueAttributeValues(Queue queue, HLAfloat64Time time) throws RTIexception {
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
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);
//        for (Queue queue : queues) {
//            updateQueueAttributeValues(queue, time);
//        }
        for (Queue queue : queues) {
            updateQueueAttributeValues(queue, time);
        }
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    public void createNewQueue() {
        ObjectInstanceHandle objectInstanceHandle = null;
        try {
            objectInstanceHandle = registerObject();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
//        Queue queue = new Queue(Queue.count.incrementAndGet(),
//                random.nextInt(Queue.MAX_SIZE) + 1,
//                Queue.INITIAL_SIZE,
//                new ArrayList<>(),
//                objectInstanceHandle);
        Queue queue = new Queue(Queue.count.getAndIncrement(), random.nextInt(Queue.MAX_SIZE) + 1, Queue.INITIAL_SIZE);
        queue.setRtiHandler(objectInstanceHandle);
        queues.add(queue);
        log("Created new queue");
        System.out.println(queues);
    }

    private ObjectInstanceHandle registerObject() throws RTIexception {
        return rtiamb.registerObjectInstance(queueObjectHandle);
    }

    public void addNewClientToQueue(int queueId, int clientId) {
        //TODO uprzywilejowany poprawic
        System.out.println("ADD NEW CLIENT TO QUEUE: (" + clientId + ")");
        System.out.println(queues.get(0));
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
        System.out.println(queues.get(0));
    }

    public void removeClientFromQueue(int checkoutId, int clientId) {
        System.out.println("REMOVE CLIENT FROM QUEUE");
        System.out.println("REMOVE CLIENT ID: " + clientId);
        System.out.println(queues.get(0));
        Queue queue = queues.get(checkoutId);
//        queue.getClients().removeIf(client -> client.getClientId() == clientId);
        Client removed = queue.getClients().remove(0);
        if (removed != null) {
            queue.setCurrentSize(queue.getCurrentSize() - 1);
        }
        System.out.println(queues.get(0));
    }

    public void closeCheckout(int checkoutId) {
//        while (queues.isEmpty())
// TODO
    }

    protected void updateClient(ObjectInstanceHandle handle, int clientId, boolean isPrivileged, int numberOfProducts) {
        for (Client client : clients) {
            if (client.getRtiHandler().equals(handle)) {
                client.setClientId(clientId);
                client.setPrivileged(isPrivileged);
                client.setNumberOfProducts(numberOfProducts);
            }
        }
    }


    protected void discoverClient(ObjectInstanceHandle client) {
        clients.add(new Client(client));
    }
}