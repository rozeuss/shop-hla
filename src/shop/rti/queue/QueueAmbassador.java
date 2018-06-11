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

import hla.rti.jlc.EncodingHelpers;
import hla.rti1516e.*;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.time.HLAfloat64Time;
import shop.utils.DecoderUtils;

import java.util.Arrays;

@SuppressWarnings("Duplicates")
public class QueueAmbassador extends NullFederateAmbassador {

    protected boolean running = true;
    protected double federateTime = 0.0;
    protected double federateLookahead = 1.0;
    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;
    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;
    private QueueFederate federate;


    public QueueAmbassador(QueueFederate federate) {
        this.federate = federate;
    }

    private void log(String message) {
        System.out.println("QueueAmbassador: " + message);
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
        if (label.equals(QueueFederate.READY_TO_RUN))
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized(String label, FederateHandleSet failed) {
        log("Federation Synchronized: " + label);
        if (label.equals(QueueFederate.READY_TO_RUN))
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
                                       String objectName) throws FederateInternalError {
        log("Discovered Object: handle=" + theObject + ", classHandle=" +
                theObjectClass + ", name=" + objectName);
        this.federate.instanceClassMap.put(theObject, theObjectClass);
        if (theObjectClass.equals(this.federate.clientObjectHandle)) {
            this.federate.discoverClient(theObject);
        }
    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject,
                                       AttributeHandleValueMap theAttributes,
                                       byte[] tag,
                                       OrderType sentOrder,
                                       TransportationTypeHandle transport,
                                       SupplementalReflectInfo reflectInfo)
            throws FederateInternalError {
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
    public void reflectAttributeValues(ObjectInstanceHandle theObject, AttributeHandleValueMap theAttributes,
                                       byte[] tag, OrderType sentOrdering, TransportationTypeHandle theTransport,
                                       LogicalTime time, OrderType receivedOrdering,
                                       SupplementalReflectInfo reflectInfo)
            throws FederateInternalError {
        //TODO EncodingHelpers nie jest ze standardu ieee
        String decodedTag = EncodingHelpers.decodeString(tag);


        if (federate.instanceClassMap.get(theObject).equals(federate.clientObjectHandle)) {

            for (int i = 0; i < federate.clients.size(); i++) {
                if (theObject.equals(federate.clients.get(i).getRtiHandler())) {
                    int clientId = 0;
                    int numberOfProducts = 0;
                    boolean isPrivileged = false;
                    StringBuilder builder = new StringBuilder("Reflection for object:");
                    builder.append(" handle=" + theObject);
                    builder.append(", attributeCount=" + theAttributes.size());
                    builder.append("\n");
                    for (AttributeHandle attributeHandle : theAttributes.keySet()) {
                        builder.append("\tattributeHandle=");
                        if (attributeHandle.equals(federate.clientId)) {
                            builder.append(attributeHandle);
                            builder.append(" id:");
                            int val = DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle));
                            builder.append(val);
                            clientId = val;
                        } else if (attributeHandle.equals(federate.clientNumberOfProducts)) {
                            builder.append(attributeHandle);
                            builder.append(" numberOfProducts:");
                            int val = DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle));
                            builder.append(val);
                            numberOfProducts = val;
                        } else if (attributeHandle.equals(federate.clientIsPrivileged)) {
                            builder.append(attributeHandle);
                            builder.append(" isPrivileged:");
                            boolean val = DecoderUtils.decodeBoolean(federate.encoderFactory, theAttributes.getValueReference(attributeHandle));
                            builder.append(val);
                            isPrivileged = val;
                        } else {
                            builder.append(attributeHandle);
                            builder.append(" (Unknown)   ");
                        }

                        builder.append("\n");
                    }
//                    log(builder.toString());
                    this.federate.updateClient(theObject, clientId, isPrivileged, numberOfProducts);
                }
            }
        }
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters,
                                   byte[] tag, OrderType sentOrdering, TransportationTypeHandle theTransport,
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
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
    public void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters,
                                   byte[] tag, OrderType sentOrdering, TransportationTypeHandle theTransport,
                                   LogicalTime time, OrderType receivedOrdering, SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        StringBuilder builder = new StringBuilder("Interaction Received:");
        builder.append(" *interactionClass* ").append(interactionClass);
        builder.append(" *theParameters* ").append(theParameters);
        builder.append(" *tag* ").append(Arrays.toString(tag));
        builder.append(" *sentOrdering* ").append(sentOrdering);
        builder.append(" *theTransport* ").append(theTransport);
        builder.append(" *time* ").append(time);
        builder.append(" *receivedOrdering* ").append(receivedOrdering);
        builder.append(" *receiveInfo* ").append(receiveInfo);
        if (interactionClass.equals(federate.openCheckoutInteractionHandle)) {
            builder.append("openCheckoutInteractionHandle");
            federate.createNewQueue();
        } else if (interactionClass.equals(federate.chooseQueueInteractionHandle)) {
            builder.append("chooseQueueInteractionHandle");
            int queueId = 0;
            int clientId = 0;
            for (ParameterHandle parameterHandle : theParameters.keySet()) {
                builder.append("\tparameter=");
                if (parameterHandle.equals(federate.chooseQueueClientId)) {
                    builder.append(parameterHandle);
                    builder.append(" clientId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle)));
                    clientId = DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle));
                } else if (parameterHandle.equals(federate.chooseQueueCheckoutId)) {
                    builder.append(parameterHandle);
                    builder.append(" checkoutId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle)));
                    queueId = DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle));
                }
            }
            federate.addNewClientToQueue(queueId, clientId);
        } else if (interactionClass.equals(federate.closeCheckoutInteractionHandle)) {
            builder.append("closeCheckoutInteractionHandle");
            int checkoutId = 0;
            for (ParameterHandle parameterHandle : theParameters.keySet()) {
                builder.append("\tparameter=");
                if (parameterHandle.equals(federate.closeCheckoutCheckoutId)) {
                    builder.append(parameterHandle);
                    builder.append(" checkoutId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle)));
                    checkoutId = DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle));
                }
            }
            federate.closeCheckout(checkoutId);
        } else if (interactionClass.equals(federate.endServiceInteractionHandle)) {
            builder.append("endServiceInteractionHandle");
            int checkoutId = 0;
            int clientId = 0;
            for (ParameterHandle parameterHandle : theParameters.keySet()) {
                builder.append("\tparameter=");
                if (parameterHandle.equals(federate.endServiceCheckoutId)) {
                    builder.append(parameterHandle);
                    builder.append(" checkoutId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle)));
                    checkoutId = DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle));

                } else if (parameterHandle.equals(federate.endServiceClientId)) {
                    builder.append(parameterHandle);
                    builder.append(" clientId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle)));
                    clientId = DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle));
                }
            }
            federate.removeClientFromQueue(checkoutId, clientId);
        } else {
            log(" UNDEFINED ");
        }
//        log(builder.toString());
    }

    @Override
    public void removeObjectInstance(ObjectInstanceHandle theObject, byte[] tag, OrderType sentOrdering,
                                     SupplementalRemoveInfo removeInfo)
            throws FederateInternalError {
        log("Object Removed: handle=" + theObject);
    }
}
