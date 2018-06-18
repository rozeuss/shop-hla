package shop.rti.statistic;

import hla.rti1516e.*;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.time.HLAfloat64Time;
import shop.utils.DecoderUtils;

@SuppressWarnings("Duplicates")
public class StatisticAmbassador extends NullFederateAmbassador {
    protected double federateTime = 0.0;
    protected double federateLookahead = 1.0;

    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;

    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;
    protected boolean running = true;
    private StatisticFederate federate;

    StatisticAmbassador(StatisticFederate federate) {
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
        reflectAttributeValues(theObject, theAttributes, tag, sentOrder, transport, null, sentOrder, reflectInfo);
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
            this.federate.updateClient(theObject, clientId, isPrivileged, endShoppingTime, time);
        } else if (federate.instanceClassMap.get(theObject).equals(federate.queueObjectHandle)) {
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
        } else if (federate.instanceClassMap.get(theObject).equals(federate.checkoutObjectHandle)) {
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
            federate.updateCheckout(theObject, checkoutId, checkoutIsOpened, checkoutQueueId);
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
        StatisticFederate.interactionsCounter.merge(interactionClass, 1, Integer::sum);
        if (interactionClass.equals(federate.openCheckoutInteractionHandle)) {
            builder.append("openCheckoutInteractionHandle");
            int checkoutId = 0;
            for (ParameterHandle parameterHandle : theParameters.keySet()) {
                builder.append("\tparameter=");
                if (parameterHandle.equals(federate.openCheckoutCheckoutId)) {
                    builder.append(parameterHandle);
                    builder.append(" checkoutId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle)));
                    checkoutId = DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle));
                }
            }
            federate.receiveOpenCheckoutInteraction(interactionClass, time, checkoutId);
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
            federate.receiveChooseQueueInteraction(interactionClass, time, queueId, clientId);
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
            federate.receiveCloseCheckoutInteraction(interactionClass, time, checkoutId);
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
            federate.receiveEndServiceInteraction(interactionClass, time, checkoutId, clientId);
        } else if (interactionClass.equals(federate.clientExitInteractionHandle)) {
            builder.append("START SERVICE.");
            int checkoutId = 0;
            int clientId = 0;
            for (ParameterHandle parameterHandle : theParameters.keySet()) {
                builder.append("\tparameter=");
                if (parameterHandle.equals(federate.clientExitCheckoutIdParameter)) {
                    builder.append(parameterHandle);
                    builder.append(" checkoutId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle)));
                    checkoutId = DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle));

                } else if (parameterHandle.equals(federate.clientExitClientIdParameter)) {
                    builder.append(parameterHandle);
                    builder.append(" clientId:");
                    builder.append(DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle)));
                    clientId = DecoderUtils.decodeInt(federate.encoderFactory, theParameters.getValueReference(parameterHandle));
                }
            }
            federate.receiveClientExitInteraction(interactionClass, time, checkoutId, clientId);
        }

    }

    @Override
    public void removeObjectInstance(ObjectInstanceHandle theObject,
                                     byte[] tag,
                                     OrderType sentOrdering,
                                     SupplementalRemoveInfo removeInfo) throws FederateInternalError {
        log("XD " + theObject);
    }

    @Override
    public void discoverObjectInstance(ObjectInstanceHandle theObject,
                                       ObjectClassHandle theObjectClass,
                                       String objectName) throws FederateInternalError {
        this.federate.instanceClassMap.put(theObject, theObjectClass);
        StatisticFederate.objectsCounter.merge(theObjectClass, 1, Integer::sum);
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
