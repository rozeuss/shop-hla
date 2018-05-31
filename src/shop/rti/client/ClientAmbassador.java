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
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Time;
import shop.object.Checkout;
import shop.utils.DecoderUtils;

public class ClientAmbassador extends NullFederateAmbassador {
    protected boolean running = true;
    protected double federateTime = 0.0;
    protected double federateLookahead = 1.0;
    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;
    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;
    private ClientFederate federate;

    public ClientAmbassador(ClientFederate federate) {
        this.federate = federate;
    }


    private void log(String message) {
        System.out.println("ClientAmbassador: " + message);
    }


    @Override
    public void synchronizationPointRegistrationFailed(String label,
                                                       SynchronizationPointFailureReason reason) {
        log("Failed to register sync point: " + label + ", reason=" + reason);
    }

    @Override
    public void synchronizationPointRegistrationSucceeded(String label) {
        log("Successfully registered sync point: " + label);
    }

    @Override
    public void announceSynchronizationPoint(String label, byte[] tag) {
        log("Synchronization point announced: " + label);
        if (label.equals(ClientFederate.READY_TO_RUN))
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized(String label, FederateHandleSet failed) {
        log("Federation Synchronized: " + label);
        if (label.equals(ClientFederate.READY_TO_RUN))
            this.isReadyToRun = true;
    }

    @Override
    public void timeRegulationEnabled(LogicalTime time) {
        this.federateTime = ((HLAfloat64Time) time).getValue();
        this.isRegulating = true;
    }

    @Override
    public void timeConstrainedEnabled(LogicalTime time) {
        this.federateTime = ((HLAfloat64Time) time).getValue();
        this.isConstrained = true;
    }

    @Override
    public void timeAdvanceGrant(LogicalTime time) {
        this.federateTime = ((HLAfloat64Time) time).getValue();
        this.isAdvancing = false;
    }

    @Override
    public void discoverObjectInstance(ObjectInstanceHandle theObject,
                                       ObjectClassHandle theObjectClass,
                                       String objectName)
            throws FederateInternalError {
        StringBuilder builder = new StringBuilder("Discover object:");

        if (theObject == federate.checkoutObjectHandle) {
            builder.append("CHECKOUT");
            log("Discoverd Object: handle=" + theObject + ", classHandle=" +
                    theObjectClass + ", name=" + objectName);
            builder.append(" handle=" + theObject);
            builder.append("\n");
            federate.addNewCheckout(theObject);
        }

//        if (theObject == federate.clientObjectHandle) {
//            builder.append("CLIENT");
//            log("Discoverd Object: handle=" + theObject + ", classHandle=" +
//                    theObjectClass + ", name=" + objectName);
//            builder.append(" handle=" + theObject);
//            builder.append("\n");
//             TODO ?
//        }
        log(builder.toString());

    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject,
                                       AttributeHandleValueMap theAttributes,
                                       byte[] tag,
                                       OrderType sentOrder,
                                       TransportationTypeHandle transport,
                                       SupplementalReflectInfo reflectInfo)
            throws FederateInternalError {
        // just pass it on to the other method for printing purposes
        // passing null as the time will let the other method know it
        // it from us, not from the RTI
        reflectAttributeValues(theObject,
                theAttributes,
                tag,
                sentOrder,
                transport,
                null,
                sentOrder,
                reflectInfo);
    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject,
                                       AttributeHandleValueMap theAttributes,
                                       byte[] tag,
                                       OrderType sentOrdering,
                                       TransportationTypeHandle theTransport,
                                       LogicalTime time,
                                       OrderType receivedOrdering,
                                       SupplementalReflectInfo reflectInfo)
            throws FederateInternalError {


        StringBuilder builder = new StringBuilder("Reflection for object:");

        if (theObject == federate.checkoutObjectHandle) {
            int checkoutId = 0;
            int checkoutQueueId = 0;
            boolean checkoutIsOpened = false;
            builder.append("CHECKOUT");
            builder.append(" handle=" + theObject);
            builder.append(", attributeCount=" + theAttributes.size());
            builder.append("\n");
            for (AttributeHandle attributeHandle : theAttributes.keySet()) {
                builder.append("\tattributeHandle=");
                if (attributeHandle.equals(federate.checkoutId)) {
                    builder.append(attributeHandle);
                    builder.append(" checkoutId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle)));
                    checkoutId = DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle));
                } else if (attributeHandle.equals(federate.checkoutQueueId)) {
                    builder.append(attributeHandle);
                    builder.append(" checkoutQueueId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle)));
                    checkoutQueueId = DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle));
                } else if (attributeHandle.equals(federate.checkoutIsOpened)) {
                    builder.append(attributeHandle);
                    builder.append(" checkoutIsOpened:");
                    builder.append(DecoderUtils.decodeBoolean(federate.encoderFactory, theAttributes.getValueReference(attributeHandle)));
                    checkoutIsOpened = DecoderUtils.decodeBoolean(federate.encoderFactory, theAttributes.getValueReference(attributeHandle));
                } else {
                    builder.append(attributeHandle);
                    builder.append(" (Unknown)   ");
                }
                builder.append("\n");
            }
            try {
                federate.updateCheckout(new Checkout(checkoutId, checkoutQueueId, checkoutIsOpened, theObject));
            } catch (RTIexception rtIexception) {
                rtIexception.printStackTrace();
            }
        }

//        if (theObject == federate.clientObjectHandle) {
//            builder.append("CLIENT");
//            builder.append(" handle=" + theObject);
//            builder.append(", attributeCount=" + theAttributes.size());
//            builder.append("\n");
//            try {
//                federate.updateClientAttributeValues(theObject);
//            } catch (RTIexception rtIexception) {
//                rtIexception.printStackTrace();
//            }
//        }

        log(builder.toString());
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] tag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        // just pass it on to the other method for printing purposes
        // passing null as the time will let the other method know it
        // it from us, not from the RTI
        this.receiveInteraction(interactionClass,
                theParameters,
                tag,
                sentOrdering,
                theTransport,
                null,
                sentOrdering,
                receiveInfo);
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] tag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   LogicalTime time,
                                   OrderType receivedOrdering,
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {

        StringBuilder builder = new StringBuilder("Interaction Received: ");

        if (interactionClass == federate.startServiceInteractionHandle) {
            builder.append("START SERVICE.");
            int checkoutId = 0;
            int clientId = 0;
            for (ParameterHandle parameterHandle : theParameters.keySet()) {
                builder.append("\tparameter=");
                if (parameterHandle.equals(federate.startServiceCheckoutIdParameter)) {
                    builder.append(parameterHandle);
                    builder.append(" checkoutId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle)));
                    checkoutId = DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle));

                } else if (parameterHandle.equals(federate.startServiceClientIdParameter)) {
                    builder.append(parameterHandle);
                    builder.append(" clientId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle)));
                    clientId = DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle));
                }
            }
            federate.serviceClient(checkoutId, clientId);
        }

        log(builder.toString());

    }

    @Override
    public void removeObjectInstance(ObjectInstanceHandle theObject,
                                     byte[] tag,
                                     OrderType sentOrdering,
                                     SupplementalRemoveInfo removeInfo)
            throws FederateInternalError {
        log("Object Removed: handle=" + theObject);
    }

}
