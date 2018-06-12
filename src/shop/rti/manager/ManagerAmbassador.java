package shop.rti.manager;

import hla.rti1516e.*;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Time;
import shop.object.Checkout;
import shop.utils.DecoderUtils;

@SuppressWarnings("Duplicates")
public class ManagerAmbassador extends NullFederateAmbassador {


    protected boolean running = true;
    protected double federateTime = 0.0;
    protected double federateLookahead = 1.0;
    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;
    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;

    private ManagerFederate federate;


    public ManagerAmbassador(ManagerFederate federate) {
        this.federate = federate;
    }

    private void log(String message) {
        System.out.println("ManagerAmbassador: " + message);
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
        if (label.equals(ManagerFederate.READY_TO_RUN))
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized(String label, FederateHandleSet failed) {
        log("Federation Synchronized: " + label);
        if (label.equals(ManagerFederate.READY_TO_RUN))
            this.isReadyToRun = true;
    }

    /**
     * The RTI has informed us that time regulation is now enabled.
     */
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
        this.federate.instanceClassMap.put(theObject, theObjectClass);
        if (theObjectClass.equals(this.federate.clientObjectHandle)) {
            this.federate.discoverClient(theObject);
        } else if (theObjectClass.equals(this.federate.queueObjectHandle)) {
            this.federate.discoverQueue(theObject);
        } else if (theObjectClass.equals(this.federate.checkoutObjectHandle)) {
            this.federate.discoverCheckout(theObject);
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
        if (federate.instanceClassMap.get(theObject).equals(federate.clientObjectHandle)) {

            for (int i = 0; i < federate.clients.size(); i++) {
                if (theObject.equals(federate.clients.get(i).getRtiHandler())) {
                    int clientId = 0;
                    int endShoppingTime = 0;
                    boolean isPrivileged = false;
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
                        } else if (attributeHandle.equals(federate.clientEndShoppingTime)) {
                            builder.append(attributeHandle);
                            builder.append(" endShoppingTime:");
                            int val = DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle));
                            builder.append(val);
                            endShoppingTime = val;
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

                    this.federate.updateClient(theObject, clientId, isPrivileged, endShoppingTime);
                }
            }

        } else if (federate.instanceClassMap.get(theObject).equals(federate.queueObjectHandle)) {
            for (int i = 0; i < federate.queues.size(); i++) {
                if (theObject.equals(federate.queues.get(i).getRtiHandler())) {
                    int queueId = 0;
                    int queueCurrentSize = 0;
                    int queueMaxSize = 0;
                    builder.append("CHECKOUT");
                    builder.append(" handle=" + theObject);
                    builder.append(", attributeCount=" + theAttributes.size());
                    builder.append("\n");
                    for (AttributeHandle attributeHandle : theAttributes.keySet()) {
                        builder.append("\tattributeHandle=");
                        if (attributeHandle.equals(federate.queueId)) {
                            builder.append(attributeHandle);
                            builder.append(" queueId:");
                            builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle)));
                            queueId = DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle));
                        } else if (attributeHandle.equals(federate.queueCurrentSize)) {
                            builder.append(attributeHandle);
                            builder.append(" queueCurrentSize:");
                            builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle)));
                            queueCurrentSize = DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle));
                        } else if (attributeHandle.equals(federate.queueMaxSize)) {
                            builder.append(attributeHandle);
                            builder.append(" queueMaxSize:");
                            builder.append(DecoderUtils.decodeBoolean(federate.encoderFactory, theAttributes.getValueReference(attributeHandle)));
                            queueMaxSize = DecoderUtils.decodeInt(federate.encoderFactory, theAttributes.getValueReference(attributeHandle));
                        } else {
                            builder.append(attributeHandle);
                            builder.append(" (Unknown)   ");
                        }
                        builder.append("\n");
                    }
                    federate.updateQueue(theObject, queueId, queueMaxSize, queueCurrentSize);

                }
            }
        } else if (federate.instanceClassMap.get(theObject).equals(federate.checkoutObjectHandle)) {
            for (int i = 0; i < federate.checkouts.size(); i++) {
                if (theObject.equals(federate.checkouts.get(i).getRtiHandler())) {
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
            }
        }

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
        StringBuilder builder = new StringBuilder("Interaction Received:");
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
