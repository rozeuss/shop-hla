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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class QueueFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    protected EncoderFactory encoderFactory;
    private RTIambassador rtiamb;
    private QueueAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;
    private InteractionClassHandle openCheckoutInteractionHandle;
    private ParameterHandle openCheckoutCheckoutId;
    private InteractionClassHandle chooseQueueInteractionHandle;
    private ParameterHandle chooseQueueCheckoutId;
    private ParameterHandle chooseQueueClientId;
    private InteractionClassHandle endServiceInteractionHandle;
    private ParameterHandle endServiceCheckoutId;
    private ParameterHandle endServiceClientId;
    private ParameterHandle closeCheckoutCheckoutId;
    private InteractionClassHandle closeCheckoutInteractionHandle;

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


//		enableTimePolicy();
//		log( "Time Policy Enabled" );


        publishAndSubscribe();
        log("Published and Subscribed");

        while (fedamb.running) {
            sendInteraction();
            advanceTime(1.0); // TODO
//            log( "Time Advanced to " + fedamb.federateTime );

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
    }

    private short getTimeAsShort() {
        return (short) fedamb.federateTime;
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

}