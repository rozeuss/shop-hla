package shop.rti.statistic;

import hla.rti1516e.*;
import hla.rti1516e.exceptions.FederateInternalError;
import shop.utils.TimeUtils;

import java.util.Arrays;

@SuppressWarnings("Duplicates")
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

        for (int i = 0; i < federate.queues.size(); i++) {
            if (theObject.equals(federate.queues.get(i).getRtiHandler())) {
                //TODO
                this.federate.updateQueue();
            }
        }

        for (int i = 0; i < federate.checkouts.size(); i++) {
            if (theObject.equals(federate.checkouts.get(i).getRtiHandler())) {
                //TODO
                this.federate.updateCheckout();
            }
        }

        for (int i = 0; i < federate.clients.size(); i++) {
            if (theObject.equals(federate.clients.get(i).getRtiHandler())) {
                //TODO
                this.federate.updateClient();
            }
        }
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
        builder.append(" *interactionClass* ").append(interactionClass);
        builder.append(" *theParameters* ").append(theParameters);
        builder.append(" *tag* ").append(Arrays.toString(tag));
        builder.append(" *sentOrdering* ").append(sentOrdering);
        builder.append(" *theTransport* ").append(theTransport);
        builder.append(" *time* ").append(time);
        builder.append(" *receivedOrdering* ").append(receivedOrdering);
        builder.append(" *receiveInfo* ").append(receiveInfo);
        if (interactionClass.equals(federate.openCheckoutInteractionHandle)) {
            log("Checkout has been opened.");
            //TODO
        } else if (interactionClass.equals(federate.chooseQueueInteractionHandle)) {
            builder.append("chooseQueueInteractionHandle");
            //TODO
        } else if (interactionClass.equals(federate.closeCheckoutInteractionHandle)) {
            builder.append("closeCheckoutInteractionHandle");
            //TODO
        } else if (interactionClass.equals(federate.endServiceInteractionHandle)) {
            builder.append("endServiceInteractionHandle");
            //TODO
        } else {
            log(" UNDEFINED ");
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

    @Override
    public void discoverObjectInstance(ObjectInstanceHandle theObject,
                                       ObjectClassHandle theObjectClass,
                                       String objectName) throws FederateInternalError {
        log("Discoverd Object: handle=" + theObject + ", classHandle=" + theObjectClass + ", name=" + objectName);
        this.federate.instanceClassMap.put(theObject, theObjectClass);
        if (theObjectClass.equals(this.federate.clientObjectHandle)) {
            this.federate.addNewClientObject(theObject);
        }
        if (theObjectClass.equals(this.federate.checkoutObjectHandle)) {
            this.federate.addNewCheckoutObject(theObject);
        }
        if (theObjectClass.equals(this.federate.queueObjectHandle)) {
            this.federate.addNewQueueObject(theObject);
        }
    }
}
