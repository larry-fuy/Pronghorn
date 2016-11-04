package com.ociweb.pronghorn.stage.network;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.pronghorn.network.ClientConnectionManager;
import com.ociweb.pronghorn.network.HTTPClientRequestStage;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.schema.NetParseAckSchema;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.network.schema.NetRequestSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.scheduling.ThreadPerStageScheduler;
import com.ociweb.pronghorn.stage.test.PipeCleanerStage;

public class ClientHTTPSPipelineTest {

	
	@Ignore //Test // do not disable it requires interet access to run, we will make a better test soon.
	public void buildPipelineTest() {

		//only build minimum for the pipeline
		
		GraphManager gm = new GraphManager();

		final int inputsCount = 2;
		
		int base2SimultaniousConnections = 1;//TODO: bug, why can we not set this to larger nubmer??
		final int outputsCount = 2;//must be < connections
		int maxPartialResponses = 2;
		final int maxListeners = 1<<base2SimultaniousConnections;

		GraphManager.addDefaultNota(gm, GraphManager.SCHEDULE_RATE, 20_000);
		
		ClientConnectionManager ccm = new ClientConnectionManager(base2SimultaniousConnections,inputsCount);
		IntHashTable listenerPipeLookup = new IntHashTable(base2SimultaniousConnections+2);
		
		System.out.println("listeners "+maxListeners);
		int i = maxListeners;
		while (--i>=0) {
			IntHashTable.setItem(listenerPipeLookup, i, i);//put this key on that pipe
			
		}
		
		//IntHashTable.setItem(listenerPipeLookup, 42, 0);//put on pipe 0
		
		
		PipeConfig<NetRequestSchema> netREquestConfig = new PipeConfig<NetRequestSchema>(NetRequestSchema.instance, 30,1<<9);
		PipeConfig<NetPayloadSchema> clientNetRequestConfig = new PipeConfig<NetPayloadSchema>(NetPayloadSchema.instance,4,16000); 
		PipeConfig<NetParseAckSchema> parseAckConfig = new PipeConfig<NetParseAckSchema>(NetParseAckSchema.instance, 4);
		
		PipeConfig<NetPayloadSchema> clientNetResponseConfig = new PipeConfig<NetPayloadSchema>(NetPayloadSchema.instance, 10, 1<<16); 		
		PipeConfig<NetResponseSchema> netResponseConfig = new PipeConfig<NetResponseSchema>(NetResponseSchema.instance, 10, 1<<15); //if this backs up we get an error TODO: fix

		
		//holds new requests
		Pipe<NetRequestSchema>[] input = new Pipe[inputsCount];		
		//responses from the server	
		Pipe<NetResponseSchema>[] toReactor = new Pipe[maxListeners];	
		
		
		int m = maxListeners;
		while (--m>=0) {
			toReactor[m] = new Pipe<NetResponseSchema>(netResponseConfig);
		}
		
		
		int k = inputsCount;
		while (--k>=0) {
            input[k] = new Pipe<NetRequestSchema>(netREquestConfig);	
		}	
		
		Pipe<NetPayloadSchema>[] clientRequests = new Pipe[outputsCount];
		int r = outputsCount;
		while (--r>=0) {
			clientRequests[r] = new Pipe<NetPayloadSchema>(clientNetRequestConfig);		
		}
		HTTPClientRequestStage requestStage = new HTTPClientRequestStage(gm, ccm, input, clientRequests);
		
		
		NetGraphBuilder.buildHTTPClientGraph(gm, outputsCount, maxPartialResponses, ccm, listenerPipeLookup, clientNetRequestConfig,
											parseAckConfig, clientNetResponseConfig, clientRequests, toReactor);
		
		i = toReactor.length;
		PipeCleanerStage[] cleaners = new PipeCleanerStage[i];
		while (--i>=0) {
			cleaners[i] = new PipeCleanerStage<>(gm, toReactor[i]); 
		}

		
		

		//GraphManager.exportGraphDotFile(gm, "httpClientPipeline");
		
		//MonitorConsoleStage.attach(gm);
		//GraphManager.enableBatching(gm);
		
		//TODO: why is this scheduler taking so long to stop.
		//StageScheduler scheduler = new FixedThreadsScheduler(gm, 5);
		StageScheduler scheduler = new ThreadPerStageScheduler(gm);
		
		
		
		scheduler.startup();		


		
		final int MSG_SIZE = 6;
		
		int testSize = 200; 
		
		int requests = testSize;
		
		
		long timeout = System.currentTimeMillis()+(testSize*1500); //reasonable timeout
		
		long start = System.currentTimeMillis();
		int d = 0;
		while (requests>0 && System.currentTimeMillis()<timeout) {
						
			Pipe<NetRequestSchema> pipe = input[0];
			if (PipeWriter.tryWriteFragment(pipe, NetRequestSchema.MSG_HTTPGET_100)) {
				PipeWriter.writeUTF8(pipe, NetRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2, "encrypted.google.com");
				PipeWriter.writeInt(pipe, NetRequestSchema.MSG_HTTPGET_100_FIELD_LISTENER_10,  requests%maxListeners);
				PipeWriter.writeUTF8(pipe, NetRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3, "/");
				PipeWriter.writeInt(pipe, NetRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1, 443);
				PipeWriter.publishWrites(pipe);

				requests--;
			
				d+=MSG_SIZE;
				
				Thread.yield();
			}
		}
		

		//count total messages, we know the parser will only send 1 message for each completed event, it does not yet have streaming.

		System.out.println("watching for responses");
		
		int expected = MSG_SIZE*(testSize);
		int count = 0;
		int lastCount = 0;
		do {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				break;
			}
			
			count = doneCount(cleaners);
			
			if (count!=lastCount) {				
				lastCount = count;
				
				if (System.currentTimeMillis()-start>20_000) {				
					System.err.println("pct "+((100f*lastCount)/(float)expected));
				}
			}

		} while (count<expected && System.currentTimeMillis()<timeout);

		long duration = System.currentTimeMillis()-start;
		//59 65 67 63
		
		//74 69 74
		System.out.println("shutdown");
		scheduler.shutdown();
		scheduler.awaitTermination(2, TimeUnit.SECONDS);
			
		System.out.println("duration: "+duration);
		System.out.println("ms per call: "+(duration/(float)testSize));
		
		assertEquals(expected, lastCount);

		
		
		
	}

	private int doneCount(PipeCleanerStage[] cleaners) {
		int count;
		{
		int k = cleaners.length;
		int c=0;
		while (--k>=0) {
			c+=cleaners[k].getTotalSlabCount();
			
		}
		
		count = c;
		}
		return count;
	}
	
}
