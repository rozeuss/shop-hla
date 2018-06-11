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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class CheckoutFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    protected EncoderFactory encoderFactory;
    protected InteractionClassHandle openCheckoutInteractionHandle;
    protected ParameterHandle openCheckoutCheckoutId;
    protected InteractionClassHandle closeCheckoutInteractionHandle;
    protected ParameterHandle closeCheckoutCheckoutId;
    protected InteractionClassHandle startServiceInteractionHandle;
    protected ParameterHandle startServiceClientId;
    protected ParameterHandle startServiceCheckoutId;
    protected InteractionClassHandle endServiceInteractionHandle;
    protected ParameterHandle endServiceCheckoutId;
    protected ParameterHandle endServiceClientId;
    protected ObjectClassHandle checkoutObjectHandle;
    protected AttributeHandle checkoutIsOpened;
    protected AttributeHandle checkoutId;
    protected AttributeHandle checkoutQueueId;
    protected List<Checkout> checkouts = new ArrayList<>();
    protected List<Client> clients = new ArrayList<>();
    protected List<Queue> queues = new ArrayList<>();
    protected Map<ObjectInstanceHandle, ObjectClassHandle> instanceClassMap = new HashMap<>();
    protected ObjectClassHandle clientObjectHandle;
    protected AttributeHandle clientIsPrivileged;
    protected AttributeHandle clientNumberOfProducts;
    protected AttributeHandle clientId;
    protected ObjectClassHandle queueObjectHandle;
    protected AttributeHandle queueMaxSize;
    protected AttributeHandle queueCurrentSize;
    protected AttributeHandle queueId;
    private RTIambassador rtiamb;
    private CheckoutAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;

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

    public void runFederate(String federateName) throws Exception {
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

        createNewCheckout();

        while (fedamb.running) {
//            TimeUnit.SECONDS.sleep(3);
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

    private void sendStartServiceInteraction(int queueId, HLAfloat64Time time) throws RTIexception {
        log("START SERVICE " + "@TODO");
        ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
        parameterHandleValueMap.put(startServiceCheckoutId, encoderFactory.createHLAinteger32BE(queueId).toByteArray());
        parameterHandleValueMap.put(startServiceClientId, encoderFactory.createHLAinteger32BE(0).toByteArray());
        rtiamb.sendInteraction(startServiceInteractionHandle, parameterHandleValueMap, generateTag(), time);
    }

    private void sendEndServiceInteraction(int queueId, HLAfloat64Time time) throws RTIexception {
        log("END SERVICE " + "@TODO");
        ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
        parameterHandleValueMap.put(endServiceCheckoutId, encoderFactory.createHLAinteger32BE(queueId).toByteArray());
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
        startServiceInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.StartService");
        startServiceCheckoutId = rtiamb.getParameterHandle(startServiceInteractionHandle, "checkoutId");
        startServiceClientId = rtiamb.getParameterHandle(startServiceInteractionHandle, "clientId");
        rtiamb.publishInteractionClass(startServiceInteractionHandle);

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
        clientNumberOfProducts = rtiamb.getAttributeHandle(clientObjectHandle, "numberOfProducts");
        clientId = rtiamb.getAttributeHandle(clientObjectHandle, "clientId");
        AttributeHandleSet clientAttributes = rtiamb.getAttributeHandleSetFactory().create();
        clientAttributes.add(clientIsPrivileged);
        clientAttributes.add(clientNumberOfProducts);
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

    private void deleteObject(ObjectInstanceHandle handle) throws RTIexception {
        rtiamb.deleteObjectInstance(handle, generateTag());
    }

    private void doThings() throws RTIexception {
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);
//        for (Checkout checkout : checkouts) {
//            updateCheckoutAttributeValues(checkout, time);
//        }
        if (checkouts.size() > 0) {
            List<Checkout> openCheckouts = checkouts.stream().filter(Checkout::isOpen).collect(Collectors.toList());
            log("open checkouts = " + openCheckouts);
            if (!openCheckouts.isEmpty()) {

                if (!queues.isEmpty()) {
                    for (Checkout checkout : openCheckouts) {
                        Queue existingQueue = queues.get(checkout.getQueueId());
                        if (existingQueue != null && existingQueue.getCurrentSize() > 1) {
                            // TODO konieczne > 1 bo najpierw wykonuje swoje dzialania
                            // a dopiero potem robi update od obcych federatow
                            try {
                                TimeUnit.SECONDS.sleep(2);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            sendStartServiceInteraction(existingQueue.getQueueId(), time);
                            try {
                                TimeUnit.SECONDS.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            sendEndServiceInteraction(existingQueue.getQueueId(), time);
                        }

                    }
                }
            }
        }
        for (Checkout checkout : checkouts) {
            updateCheckoutAttributeValues(checkout, time);
        }
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    public void createNewCheckout() {
        ObjectInstanceHandle objectInstanceHandle = null;
        try {
            objectInstanceHandle = registerObject();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
        Checkout checkout = new Checkout(Checkout.count.getAndIncrement(), 0, true, objectInstanceHandle);
        checkout.setQueueId(checkout.getQueueId());
        checkouts.add(checkout);
        log("Created new checkout");
        log("" + checkouts);
    }

    private ObjectInstanceHandle registerObject() throws RTIexception {
        return rtiamb.registerObjectInstance(checkoutObjectHandle);
    }

    protected void discoverClient(ObjectInstanceHandle client) {
        clients.add(new Client(client));
        log("DISCOVERED NEW CLIENT " + client);
    }

    protected void updateClient(ObjectInstanceHandle handle, int clientId, boolean isPrivileged, int numberOfProducts) {
        for (Client client : clients) {
            if (client.getRtiHandler().equals(handle)) {
                client.setClientId(clientId);
                client.setPrivileged(isPrivileged);
                client.setNumberOfProducts(numberOfProducts);
            }
        }
//        log("Updated clients " + clients);
    }

    public void updateQueue(ObjectInstanceHandle handle, int queueId, int queueMaxSize, int queueCurrentSize) {
        for (Queue queue : queues) {
            if (queue.getRtiHandler().equals(handle)) {
                queue.setQueueId(queueId);
                queue.setMaxSize(queueMaxSize);
                queue.setCurrentSize(queueCurrentSize);
            }
        }
//        log("Updated queues " + queues);
    }

    public void discoverQueue(ObjectInstanceHandle queueHandle) {
        queues.add(new Queue(queueHandle));
    }

}