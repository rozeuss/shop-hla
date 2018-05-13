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
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.time.HLAfloat64Time;
import shop.utils.TimeUtils;

public class StatisticAmbassador extends NullFederateAmbassador {
    protected double federateTime = 0.0;
    protected double grantedTime = 0.0;
    protected double federateLookahead = 1.0;

    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;

    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;
    protected boolean running = true;
    private StatisticFederate federate;

    public StatisticAmbassador(StatisticFederate federate) {
        this.federate = federate;
    }


    private void log(String message) {
        System.out.println("StatisticAmbassador: " + message);
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
        if (label.equals(StatisticFederate.READY_TO_RUN))
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized(String label, FederateHandleSet failed) {
        log("Federation Synchronized: " + label);
        if (label.equals(StatisticFederate.READY_TO_RUN))
            this.isReadyToRun = true;
    }

    /**
     * The RTI has informed us that time regulation is now enabled.
     */
    @Override
    public void timeRegulationEnabled(LogicalTime time) {
        this.federateTime = TimeUtils.convertTime(time);
        this.isRegulating = true;
    }

    @Override
    public void timeConstrainedEnabled(LogicalTime time) {
        this.federateTime = TimeUtils.convertTime(time);
        this.isConstrained = true;
    }

    @Override
    public void timeAdvanceGrant(LogicalTime time) {
        this.grantedTime = TimeUtils.convertTime(time);
        this.isAdvancing = false;
    }


    @Override
    public void reflectAttributeValues( ObjectInstanceHandle theObject,
                                        AttributeHandleValueMap theAttributes,
                                        byte[] tag,
                                        OrderType sentOrdering,
                                        TransportationTypeHandle theTransport,
                                        LogicalTime time,
                                        OrderType receivedOrdering,
                                        SupplementalReflectInfo reflectInfo )
            throws FederateInternalError {
// TODO konieczne do pracy z Obiektami
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters,
                                   byte[] tag, OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        this.receiveInteraction(interactionClass, theParameters, tag, sentOrdering, theTransport, null,
                sentOrdering, receiveInfo);
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] tag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   LogicalTime time,
                                   OrderType receivedOrdering,
                                   SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        StringBuilder builder = new StringBuilder("Interaction Received:");

//       TODO
//        if (interactionClass == federate.openCheckoutInteractionHandle) {
            builder.append(" *interactionClass* " + interactionClass);
            builder.append(" *theParameters* " + theParameters);
            builder.append(" *tag* " + tag);
            builder.append(" *sentOrdering* " + sentOrdering);
            builder.append(" *theTransport* " + theTransport);
            builder.append(" *time* " + time);
            builder.append(" *receivedOrdering* " + receivedOrdering);
            builder.append(" *receiveInfo* " + receiveInfo);
            log("Checkout has been opened.");
//        } else if (interactionClass.equals(federate.endSimulationInteractionHandle)) {
//            builder.append("END OF SIMULATION");
//        } else {
//            log( " działa ");
//        }

        log( builder.toString() );

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
