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
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import shop.utils.TimeUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

public class StatisticFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    private final double timeStep = 10.0;
    protected ParameterHandle checkoutId;
    protected InteractionClassHandle endSimulationInteractionHandle;
    protected InteractionClassHandle openCheckoutInteractionHandle;

    protected ObjectClassHandle queueObjectHandle;
    protected AttributeHandle maxSizeAttributeHandle;
    protected AttributeHandle currentSizeAttributeHandle;
    protected AttributeHandle queueIdAttributeHandle;

    protected ObjectClassHandle clientObjectHandle;
    protected AttributeHandle isPrivilegedAttributeHandle;
    protected AttributeHandle numberOfProductsAttributeHandle;
    protected AttributeHandle clientIdAttributeHandle;

    protected ObjectClassHandle checkoutObjectHandle;
    protected AttributeHandle isOpenAttributeHandle;
    protected AttributeHandle checkoutIdAttributeHandle;
    protected AttributeHandle checkoutQueueIdAttributeHandle;


    protected EncoderFactory encoderFactory;
    //    TODO object -> Checkout
    protected ArrayList<Object> checkouts = new ArrayList<>();
    protected ArrayList<Object> queues = new ArrayList<>();
    protected ArrayList<Object> clients = new ArrayList<>();

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
//            rtiamb.tick();
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
//            rtiamb.tick();
        }

        enableTimePolicy();
        log("Time Policy Enabled");

        publishAndSubscribe();
        log("Published and Subscribed");

        while (fedamb.isRunning) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
//            TODO !!!!
//            showStatistics();
//            advanceTime(1.0);
        }

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

    public void addNewOpenedCheckout(int idKlient, boolean czyUprzywilejowany) {
//        TODO
    }


    private void enableTimePolicy() throws Exception {
        LogicalTime currentTime = TimeUtils.convertTime(fedamb.federateTime);
        LogicalTimeInterval lookahead = TimeUtils.convertInterval(fedamb.federateLookahead);

//        this.rtiamb.enableTimeRegulation(currentTime, lookahead);

        while (fedamb.isRegulating == false) {
//            rtiamb.tick();
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);

        }

        this.rtiamb.enableTimeConstrained();

        while (fedamb.isConstrained == false) {
//            rtiamb.tick();
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);

        }
    }

    private void publishAndSubscribe() throws RTIexception {
        openCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.OpenCheckout");
        checkoutId = rtiamb.getParameterHandle(openCheckoutInteractionHandle, "checkoutId");
        rtiamb.subscribeInteractionClass(openCheckoutInteractionHandle);
    }

    private void advanceTime(double timestep) throws RTIexception {
        fedamb.isAdvancing = true;
        LogicalTime logicalTime = TimeUtils.convertTime(timestep);
        rtiamb.timeAdvanceRequest(logicalTime);
        while (fedamb.isAdvancing) {
//            rtiamb.tick();
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
}