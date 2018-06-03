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
import shop.utils.TimeUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

//TODO mozna stworzyc klase Federate gdzie beda te duplikaty
@SuppressWarnings("Duplicates")
public class StatisticFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    private final double timeStep = 10.0;
    protected ParameterHandle openCheckoutCheckoutId;
    protected InteractionClassHandle openCheckoutInteractionHandle;

    protected ObjectClassHandle queueObjectHandle;
    protected AttributeHandle queueMaxSize;
    protected AttributeHandle queueCurrentSize;
    protected AttributeHandle queueId;

    protected ObjectClassHandle clientObjectHandle;
    protected AttributeHandle clientIsPrivileged;
    protected AttributeHandle clientNumberOfProducts;
    protected AttributeHandle clientId;

    protected ObjectClassHandle checkoutObjectHandle;
    protected AttributeHandle checkoutIsOpen;
    protected AttributeHandle checkoutId;
    protected AttributeHandle checkoutQueueId;

    protected EncoderFactory encoderFactory;
    protected ArrayList<Checkout> checkouts = new ArrayList<>();
    protected ArrayList<Queue> queues = new ArrayList<>();
    protected ArrayList<Client> clients = new ArrayList<>();
    protected InteractionClassHandle chooseQueueInteractionHandle;
    protected ParameterHandle chooseQueueCheckoutId;
    protected ParameterHandle chooseQueueClientId;
    protected InteractionClassHandle closeCheckoutInteractionHandle;
    protected ParameterHandle closeCheckoutCheckoutId;
    protected InteractionClassHandle endServiceInteractionHandle;
    protected ParameterHandle endServiceCheckoutId;
    protected ParameterHandle endServiceClientId;
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

    public void runFederate(String federateName) throws Exception {
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
//
        publishAndSubscribe();
        log("Published and Subscribed");

        while (fedamb.running) {
//            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
            TimeUnit.SECONDS.sleep(3);

            advanceTime(1.0);
            doThings();
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);

//hla.rti1516e.exceptions.LogicalTimeAlreadyPassed: org.portico.lrc.compat.JFederationTimeAlreadyPassed: Time 1.0 has already passed

//            TODO !!!!
//            showStatistics();
        }

    }

    private void doThings() {
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + fedamb.federateLookahead);
    }

    public void addNewOpenedCheckout(int idKlient, boolean czyUprzywilejowany) {
//        TODO
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


        //TODO obiekty Checkout, Client, Queue

    }

    private void advanceTime(double timestep) throws RTIexception {
        fedamb.isAdvancing = true;
        LogicalTime logicalTime = TimeUtils.convertTime(timestep);
        rtiamb.timeAdvanceRequest(logicalTime);
        while (fedamb.isAdvancing) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private void deleteObject(ObjectInstanceHandle handle) throws RTIexception {
        rtiamb.deleteObjectInstance(handle, generateTag());
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    public void addNewClientObject(ObjectInstanceHandle theObject) {
        clients.add(new Client(theObject));
    }

    public void addNewCheckoutObject(ObjectInstanceHandle theObject) {
        checkouts.add(new Checkout(theObject));
    }

    public void addNewQueueObject(ObjectInstanceHandle theObject) {
        queues.add(new Queue(theObject));
    }

    public void updateClient() {
        //TODO
    }

    public void updateCheckout() {
        //TODO
    }

    public void updateQueue() {
        //TODO
    }
}