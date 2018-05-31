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
package shop.rti.client;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAinteger16BE;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import shop.object.Checkout;
import shop.object.Client;
import shop.utils.HandlersHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("Duplicates")
public class ClientFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
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
    Random random = new Random();
    private RTIambassador rtiamb;
    private ClientAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;
    private AttributeHandle clientIsPrivileged;
    private AttributeHandle clientNumberOfProducts;
    private AttributeHandle clientId;
    private List<Client> clients = new ArrayList<>();
    private List<Checkout> checkouts = new ArrayList<>();

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
//        enableTimePolicy();
//        log("Time Policy Enabled"); // TODO enableTimePolicy i advanceTime (evokeMultipleCallbacks) do synchro czasowej

        publishAndSubscribe();
        log("Published and Subscribed");

        for (int i = 0; i < 4; i++) {
            createClientObject();
        }

        while (fedamb.running) {
            sendChooseQueueInteraction();

//            advanceTime(1.0); //TODO
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
        clientNumberOfProducts = rtiamb.getAttributeHandle(clientObjectHandle, "numberOfProducts");
        clientId = rtiamb.getAttributeHandle(clientObjectHandle, "clientId");
        log("clientHandle ID: " + clientObjectHandle);
        log("clientHandle hashCode: " + clientObjectHandle.hashCode());
        AttributeHandleSet clientAttributes = rtiamb.getAttributeHandleSetFactory().create();
        clientAttributes.add(clientIsPrivileged);
        clientAttributes.add(clientNumberOfProducts);
        clientAttributes.add(clientId);
        rtiamb.publishObjectClassAttributes(clientObjectHandle, clientAttributes);
//        rtiamb.subscribeObjectClassAttributes(clientObjectHandle, clientAttributes);
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
        log("checkoutObjectClassHandle ID: " + checkoutObjectHandle);
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
    }

    private ObjectInstanceHandle registerObject() throws RTIexception {
        System.out.println(clientObjectHandle);
        return rtiamb.registerObjectInstance(clientObjectHandle);
    }

    protected void updateClientAttributeValues(Client client) throws RTIexception {
        log("Aktualizacja obiektu CLIENT");
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(3);
        rtiamb.updateAttributeValues( client.getRtiHandler(), attributes, generateTag());
    }

    protected void updateCheckout(Checkout checkout) throws RTIexception {
        log("Aktualizacja obiektu CHECKOUT");
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

    private void sendChooseQueueInteraction() throws RTIexception, InterruptedException {
        TimeUnit.SECONDS.sleep(5);
        log("WYBIERAM KOLEJKE");
        ParameterHandleValueMap parameterHandleValueMap = rtiamb.getParameterHandleValueMapFactory().create(2);
        // TODO ktory klient wybiera ktora kolejke
        parameterHandleValueMap.put(chooseQueueCheckoutId, encoderFactory.createHLAinteger32BE(999).toByteArray());
        parameterHandleValueMap.put(chooseQueueClientId, encoderFactory.createHLAinteger32BE(999).toByteArray());
        rtiamb.sendInteraction(chooseQueueInteractionHandle, parameterHandleValueMap, generateTag());
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

    private short getTimeAsShort() {
        return (short) fedamb.federateTime;
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
        log(clientInstanceHandle.toString());
        client.setRtiHandler(clientInstanceHandle);
        client.setClientId(Client.count.incrementAndGet());
        client.setNumberOfProducts(random.nextInt(Client.MAX_PRODUCTS) + 1);
        client.setPrivileged(random.nextBoolean());
        log(client.toString());
        clients.add(client);
    }

    public void addNewCheckout(ObjectInstanceHandle checkoutHandle) {
        checkouts.add(new Checkout(checkoutHandle));
    }

    public void serviceClient(int checkoutId, int clientId) {
        //TODO co z checkoutId
        clients.remove(clients.get(clientId));
    }
}