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
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import lombok.Getter;
import shop.object.Client;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SuppressWarnings("Duplicates")
public class ClientFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    protected EncoderFactory encoderFactory;
    protected ObjectClassHandle clientHandle;
    private RTIambassador rtiamb;
    private ClientAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory;
    private AttributeHandle isPrivilegedHandle;
    private AttributeHandle numberOfProductsHandle;
    private AttributeHandle clientIdHandle;

    @Getter
    private List<Client> objectsList = new ArrayList<>();

    public static void main(String[] args) {
        // get a federate name, use "exampleFederate" as default
        String federateName = "client";
        if (args.length != 0) {
            federateName = args[0];
        }

        try {
            // run the example federate
            new ClientFederate().runFederate(federateName);
        } catch (Exception rtie) {
            // an exception occurred, just log the information and exit
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

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////// Helper Methods //////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

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

        // cache the time factory for easy access
        this.timeFactory = (HLAfloat64TimeFactory) rtiamb.getTimeFactory();

        ////////////////////////////////
        // 5. announce the sync point //
        ////////////////////////////////
        // announce a sync point to get everyone on the same page. if the point
        // has already been registered, we'll get a callback saying it failed,
        // but we don't care about that, as long as someone registered it
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

        ///////////////////////////////////////////////////////
        // 6. achieve the point and wait for synchronization //
        ///////////////////////////////////////////////////////
        // tell the RTI we are ready to move past the sync point and then wait
        // until the federation has synchronized on
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
//        log("Time Policy Enabled");

        //////////////////////////////
        // 8. publish and subscribe //
        //////////////////////////////
        // in this section we tell the RTI of all the data we are going to
        // produce, and all the data we want to know about
        publishAndSubscribe();

        log("Published and Subscribed");


        /////////////////////////////////////
        // 9. register an object to update //
        /////////////////////////////////////
//        ObjectInstanceHandle objectHandle = registerObject();
//        log("Registered Object, handle=" + objectHandle);

        createClientObject();

        while (fedamb.running) {
            sendInteraction();
            advanceTime( 1.0 );
        }
    }

    /**
     * This method will attempt to enable the various time related properties for
     * the federate
     */
    private void enableTimePolicy() throws Exception {
        // NOTE: Unfortunately, the LogicalTime/LogicalTimeInterval create code is
        //       Portico specific. You will have to alter this if you move to a
        //       different RTI implementation. As such, we've isolated it into a
        //       method so that any change only needs to happen in a couple of spots
        HLAfloat64Interval lookahead = timeFactory.makeInterval(fedamb.federateLookahead);

        ////////////////////////////
        // enable time regulation //
        ////////////////////////////
        this.rtiamb.enableTimeRegulation(lookahead);

        // tick until we get the callback
        while (fedamb.isRegulating == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        /////////////////////////////
        // enable time constrained //
        /////////////////////////////
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
        this.clientHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Client");
        this.isPrivilegedHandle = rtiamb.getAttributeHandle(clientHandle, "isPrivileged");
        this.numberOfProductsHandle = rtiamb.getAttributeHandle(clientHandle, "numberOfProducts");
        this.clientIdHandle = rtiamb.getAttributeHandle(clientHandle, "clientId");

        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(isPrivilegedHandle);
        attributes.add(numberOfProductsHandle);
        attributes.add(clientIdHandle);

        // do the actual publication
        rtiamb.publishObjectClassAttributes(clientHandle, attributes);

    }

    /**
     * This method will register an instance of the Soda class and will
     * return the federation-wide unique handle for that instance. Later in the
     * simulation, we will update the attribute values for this instance
     */
    private ObjectInstanceHandle registerObject() throws RTIexception {
        return rtiamb.registerObjectInstance(clientHandle);
    }

    /**
     * This method will update all the values of the given object instance. It will
     * set the flavour of the soda to a random value from the options specified in
     * the FOM (Cola - 101, Orange - 102, RootBeer - 103, Cream - 104) and it will set
     * the number of cups to the same value as the current time.
     * <p/>
     * Note that we don't actually have to update all the attributes at once, we
     * could update them individually, in groups or not at all!
     */
    private void updateAttributeValues(ObjectInstanceHandle objectHandle) throws RTIexception {

    }

    /**
     * This method will send out an interaction of the type FoodServed.DrinkServed. Any
     * federates which are subscribed to it will receive a notification the next time
     * they tick(). This particular interaction has no parameters, so you pass an empty
     * map, but the process of encoding them is the same as for attributes.
     */
    private void sendInteraction() throws RTIexception {
        //////////////////////////
        // send the interaction //
        //////////////////////////
//		ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(0);
//		rtiamb.sendInteraction( servedHandle, parameters, generateTag() );
//
//		// if you want to associate a particular timestamp with the
//		// interaction, you will have to supply it to the RTI. Here
//		// we send another interaction, this time with a timestamp:
//		HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
//		rtiamb.sendInteraction( servedHandle, parameters, generateTag(), time );
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
//    private void deleteObject(ObjectInstanceHandle handle) throws RTIexception {
//        rtiamb.deleteObjectInstance(handle, generateTag());
//    }
    private short getTimeAsShort() {
        return (short) fedamb.federateTime;
    }

    //----------------------------------------------------------
    //                     STATIC METHODS
    //----------------------------------------------------------

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    private void createClientObject() {
        Random random = new Random();
        Client client = new Client();
        ObjectInstanceHandle clientInstanceHandle = null;
        try {
            clientInstanceHandle = registerObject();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
        log(clientInstanceHandle.toString());
        client.setRtiHandler(clientInstanceHandle);
        client.setClientId(1);
        client.setNumberOfProducts(5);
        client.setPrivileged(false);
        log(client.toString());
        objectsList.add(client);
    }
}