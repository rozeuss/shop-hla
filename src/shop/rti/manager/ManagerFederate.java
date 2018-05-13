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
package shop.rti.manager;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import lombok.Getter;
import shop.object.Checkout;
import shop.object.Client;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

public class ManagerFederate {


    public static final String READY_TO_RUN = "ReadyToRun";
    protected EncoderFactory encoderFactory;     // set when we join
    //    protected InteractionClassHandle discoverClientHandle;
    protected ObjectClassHandle discoverClientHandle;
    protected ArrayList<Client> clients = new ArrayList<>();
    protected ArrayList<Checkout> checkouts = new ArrayList<>();
    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    private RTIambassador rtiamb;
    private ManagerAmbassador fedamb;  // created when we connect
    private HLAfloat64TimeFactory timeFactory; // set when we join
    @Getter
    private ObjectClassHandle clientHandle;
    @Getter
    private AttributeHandle isPrivilegedHandle;
    @Getter
    private AttributeHandle numberOfProductsHandle;
    @Getter
    private AttributeHandle clientIdHandle;
    private InteractionClassHandle openCheckoutInteractionHandle;
    private ParameterHandle checkoutIdHandle;

    //----------------------------------------------------------
    //                     STATIC METHODS
    //----------------------------------------------------------
    public static void main(String[] args) {
        // get a federate name, use "exampleFederate" as default
        String federateName = "manager";
        if (args.length != 0) {
            federateName = args[0];
        }

        try {
            // run the example federate
            new ManagerFederate().runFederate(federateName);
        } catch (Exception rtie) {
            // an exception occurred, just log the information and exit
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

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////// Helper Methods //////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    public void runFederate(String federateName) throws Exception {
        /////////////////////////////////////////////////
        // 1 & 2. create the RTIambassador and Connect //
        /////////////////////////////////////////////////
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
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

        // cache the time factory for easy access
        this.timeFactory = (HLAfloat64TimeFactory) rtiamb.getTimeFactory();


        rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);
        // wait until the point is announced
        while (fedamb.isAnnounced == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        // WAIT FOR USER TO KICK US OFF
        // So that there is time to add other federates, we will wait until the
        // user hits enter before proceeding. That was, you have time to start
        // other federates.
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


        /////////////////////////////////////
        // 10. do the main simulation loop //
        /////////////////////////////////////
        // here is where we do the meat of our work. in each iteration, we will
        // update the attribute values of the object we registered, and will
        // send an interaction.
        while (fedamb.running) {
            sendInteraction();
            advanceTime(1.0);
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

        // tick until we get the callback
        while (fedamb.isConstrained == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    /**
     * This method will inform the RTI about the types of data that the federate will
     * be creating, and the types of data we are interested in hearing about as other
     * federates produce it.
     */
    private void publishAndSubscribe() throws RTIexception {
        clientHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Client");
//		TODO czy atrybuty potrzebne?
        isPrivilegedHandle = rtiamb.getAttributeHandle(clientHandle, "isPrivileged");
        numberOfProductsHandle = rtiamb.getAttributeHandle(clientHandle, "numberOfProducts");
        clientIdHandle = rtiamb.getAttributeHandle(clientHandle, "clientId");

        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(isPrivilegedHandle);
        attributes.add(numberOfProductsHandle);
        attributes.add(clientIdHandle);
        rtiamb.subscribeObjectClassAttributes(clientHandle, attributes);

        openCheckoutInteractionHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.OpenCheckout");
        checkoutIdHandle = rtiamb.getParameterHandle(openCheckoutInteractionHandle, "checkoutId");

        rtiamb.publishInteractionClass(openCheckoutInteractionHandle);


    }

    private void updateAttributeValues(ObjectInstanceHandle objectHandle) throws RTIexception {

    }

    /**
     * This method will request a time advance to the current time, plus the given
     * timestep. It will then wait until a notification of the time advance grant
     * has been received.
     */
    private void advanceTime(double timestep) throws RTIexception {
        // request the advance
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(time);

        // wait for the time advance to be granted. ticking will tell the
        // LRC to start delivering callbacks to the federate
        while (fedamb.isAdvancing) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    /**
     * This method will attempt to delete the object instance of the given
     * handle. We can only delete objects we created, or for which we own the
     * privilegeToDelete attribute.
     */
    private void deleteObject(ObjectInstanceHandle handle) throws RTIexception {
        rtiamb.deleteObjectInstance(handle, generateTag());
    }

    private void sendInteraction() throws RTIexception {
        if (!clients.isEmpty()) {
            for (Client client : clients) {
                log(client.toString());
            }

            checkouts.add(new Checkout(2));

            ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
            InteractionClassHandle openCheckout = rtiamb.getInteractionClassHandle("HLAinteractionRoot.OpenCheckout");
            ParameterHandle idHandle = rtiamb.getParameterHandle( openCheckout, "checkoutId" );
            parameters.put(idHandle, encoderFactory.createHLAinteger32BE(0).toByteArray());

            rtiamb.sendInteraction(openCheckout, parameters, "TODO TEST".getBytes());
            log("SEND INTERACTION: Open Checkout");
//            this.fedamb.running = false;
        }
    }

    private short getTimeAsShort() {
        return (short) fedamb.federateTime;
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    public void discoverClient(ObjectInstanceHandle client) {
        log("TODO " + client.toString());
        clients.add(new Client(client));
        clients.stream().forEach(System.out::println);
    }

    public void updateClient(ObjectInstanceHandle client, int id) {
        log(client.toString());
    }

}