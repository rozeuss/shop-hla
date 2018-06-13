package shop.rti.finish;

import hla.rti.*;
import hla.rti.jlc.NullFederateAmbassador;
import hla.rti.jlc.RtiFactoryFactory;

import java.io.File;
import java.net.MalformedURLException;

public class FinishFederate {

	private RTIambassador rtiamb;
	private FederateAmbassador fedamb;
	
	public static void main (String[] args) {
		FinishFederate ff = new FinishFederate();
		
		try {
			ff.runFederate();
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	public void runFederate() throws Exception {
		init();
		publishAndSubscribe();
		
		sendInteraction();
		log ("Wysłano interakcję kończącą");
		rtiamb.tick();
		
		Thread.sleep(1000);
		
		rtiamb.resignFederationExecution(ResignAction.NO_ACTION );
	}
	
	private void init() throws CouldNotOpenFED, ErrorReadingFED, RTIinternalError, ConcurrentAccessAttempted, FederateAlreadyExecutionMember, FederationExecutionDoesNotExist, SaveInProgress, RestoreInProgress {
		
		rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

		try
		{
			File fom = new File( "testfom.fed" );
			rtiamb.createFederationExecution( "ExampleFederation",
			                                  fom.toURI().toURL() );
			log( "Created Federation" );
		}
		catch( FederationExecutionAlreadyExists exists )
		{
			log( "Didn't create federation, it already existed" );
		}
		catch( MalformedURLException urle )
		{
			log( "Exception processing fom: " + urle.getMessage() );
			urle.printStackTrace();
			return;
		}
		
		fedamb = new NullFederateAmbassador();
		rtiamb.joinFederationExecution( "FinishFederate", "ExampleFederation", fedamb );
		log( "Joined Federation as " + "FinishFederate" );
		
	}
	
	private void publishAndSubscribe() throws RTIexception
	{
		int interactionHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.Finish" );
		
		rtiamb.publishInteractionClass( interactionHandle );
		
		log("Zapisano do interakcji InteractionRoot.Finish");
	}
	
	private void log( String message )
	{
		System.out.println( "FederateAmbassador: " + message );
	}
	
	private void sendInteraction() throws RTIinternalError, NameNotFound, FederateNotExecutionMember, InteractionClassNotDefined, InteractionClassNotPublished, InteractionParameterNotDefined, SaveInProgress, RestoreInProgress, ConcurrentAccessAttempted {
		SuppliedParameters parameters = RtiFactoryFactory.getRtiFactory()
				.createSuppliedParameters();

		int classHandle = rtiamb.getInteractionClassHandle("InteractionRoot.Finish");

		rtiamb.sendInteraction(classHandle, parameters, "Koniec".getBytes());

	}
	
}
