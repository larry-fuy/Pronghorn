package com.ociweb.pronghorn.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.ServiceObjectHolder;

public class ServerSocketWriterStage extends PronghornStage {
    
    private static Logger logger = LoggerFactory.getLogger(ServerSocketWriterStage.class);
    private ServiceObjectHolder<ServerConnection> socketHolder;    
    
    private final Pipe<NetPayloadSchema>[] dataToSend;
    
    private final ServerCoordinator coordinator;
    private final int groupIdx;
    
    private SocketChannel  writeToChannel;
    private boolean        writeDone = true;
    private int            activeMessageId;    
    private int            activePipe = 0;
    

    private ByteBuffer[] writeBuffs;
    
    public final static int UPGRADE_TARGET_PIPE_MASK     = (1<<21)-1;
 
    public final static int UPGRADE_CONNECTION_SHIFT     = 31;    
    public final static int UPGRADE_MASK                 = 1<<UPGRADE_CONNECTION_SHIFT;
    
    public final static int CLOSE_CONNECTION_SHIFT       = 30;
    public final static int CLOSE_CONNECTION_MASK        = 1<<CLOSE_CONNECTION_SHIFT;
    
    public final static int END_RESPONSE_SHIFT           = 29;//for multi message send this high bit marks the end
    public final static int END_RESPONSE_MASK            = 1<<END_RESPONSE_SHIFT;
    
    public final static int INCOMPLETE_RESPONSE_SHIFT    = 28;
    public final static int INCOMPLETE_RESPONSE_MASK     = 1<<INCOMPLETE_RESPONSE_SHIFT;
    
    
    /**
     * 
     * Writes pay-load back to the appropriate channel based on the channelId in the message.
     * 
     * + ServerResponseSchema is custom to this stage and supports all the features here
     * + Has support for upgrade redirect pipe change (Module can clear bit to prevent this if needed)
     * + Has support for closing connection after write as needed for HTTP 1.1 and 0.0
     * 
     * 
     * + Will Have support for writing same pay-load to multiple channels (subscriptions)
     * + Will Have support for order enforcement and pipelined requests
     * 
     * 
     * @param graphManager
     * @param dataToSend
     * @param coordinator
     * @param pipeIdx
     */
    public ServerSocketWriterStage(GraphManager graphManager, Pipe<NetPayloadSchema>[] dataToSend, ServerCoordinator coordinator, int pipeIdx) {
        super(graphManager, dataToSend, NONE);
        this.coordinator = coordinator;
        this.groupIdx = pipeIdx;
        this.dataToSend = dataToSend;
    }
    
    @Override
    public void startup() {
                
        socketHolder = ServerCoordinator.getSocketChannelHolder(coordinator, groupIdx);
        
    }
    
    @Override
    public void run() {
       
        //NOTE: TODO: BBB For the websockets,  add subscription support, eg N channels get the same message (should be in config already)
             //need list of channels off the one Id  requires new schema update

        boolean done = publish(writeToChannel);
        assert(!done || Pipe.contentRemaining(dataToSend[activePipe])>=0);
        
        int c = dataToSend.length;     
        
        while (done && --c>= 0) {
            while (done && Pipe.hasContentToRead(dataToSend[activePipe])) {
                
            	activeMessageId = Pipe.takeMsgIdx(dataToSend[activePipe]);
            	
         
            	if ( (NetPayloadSchema.MSG_PLAIN_210 == activeMessageId) ||
            	     (NetPayloadSchema.MSG_ENCRYPTED_200 == activeMessageId) ) {
            		
            		assert(Pipe.contentRemaining(dataToSend[activePipe])>=0);
            		
            		loadPayloadForXmit();            		
            		done = publish(writeToChannel);
            		
            		assert(!done || Pipe.contentRemaining(dataToSend[activePipe])>=0);
            		            		
            	} else if (NetPayloadSchema.MSG_DISCONNECT_203 == activeMessageId) {
            	
            		final long channelId = Pipe.takeLong(dataToSend[activePipe]);
            		closeChannel(socketHolder.get(channelId).getSocketChannel()); 
            		
                    Pipe.confirmLowLevelRead(dataToSend[activePipe], Pipe.sizeOf(dataToSend[activePipe], activeMessageId));
                    Pipe.releaseReadLock(dataToSend[activePipe]);
                    assert(Pipe.contentRemaining(dataToSend[activePipe])>=0);
            		            		
            	} else if (NetPayloadSchema.MSG_UPGRADE_207 == activeMessageId) {
            		
            		final long channelId = Pipe.takeLong(dataToSend[activePipe]);
            		final int newRoute = Pipe.takeInt(dataToSend[activePipe]);
            		
            	    //set the pipe for any further communications
                    ServerCoordinator.setTargetUpgradePipeIdx(coordinator, groupIdx, channelId, newRoute);
                    
                    Pipe.confirmLowLevelRead(dataToSend[activePipe], Pipe.sizeOf(dataToSend[activePipe], activeMessageId));
                    Pipe.releaseReadLock(dataToSend[activePipe]);
                    assert(Pipe.contentRemaining(dataToSend[activePipe])>=0);
                                      
            	} else if (activeMessageId < 0) {
                    
            		Pipe.confirmLowLevelRead(dataToSend[activePipe], Pipe.EOF_SIZE);
                    Pipe.releaseReadLock(dataToSend[activePipe]);
                    assert(Pipe.contentRemaining(dataToSend[activePipe])>=0);
                    
                    requestShutdown();
                    return;
                }
            }

            if (done) {
                nextPipe();
            }
        }  
    }

    private void nextPipe() {
        if (--activePipe < 0) {
            activePipe = dataToSend.length-1;
        }
    }

    

    private void loadPayloadForXmit() {
        
        Pipe<NetPayloadSchema> pipe = dataToSend[activePipe];
        final long channelId = Pipe.takeLong(pipe);
        writeToChannel = socketHolder.get(channelId).getSocketChannel(); //ChannelId or SubscriptionId
 
        //byteVector is payload
        int meta = Pipe.takeRingByteMetaData(pipe); //for string and byte array
        int len = Pipe.takeRingByteLen(pipe);
        
        writeBuffs= Pipe.wrappedReadingBuffers(pipe, meta, len);
        writeDone = false;
                
    }

    private boolean publish(SocketChannel channel) {
        if (writeDone) {
        	///logger.info("A");
            //do nothing if already done 
            return true;
        } else {            
            if (null!=channel && channel.isOpen()) { 
            	//logger.info("B");
            	return writeToChannel(channel);                
            } else {          
            	//logger.info("C");
                //if channel is closed drop the data 
                markDoneAndRelease();  
                return true;
            }
        }
    }

    private boolean writeToChannel(SocketChannel channel) {
        
        try {                
            
        	channel.write(writeBuffs);
                       
        	if (writeBuffs[0].hasRemaining() || writeBuffs[1].hasRemaining()) {
        		logger.warn("no room to write to channel");
        		return false;        		
        	} else {
        	    markDoneAndRelease();
        	    return true;
        	}
        } catch (IOException e) {
            //unable to write to this socket, treat as closed
            markDoneAndRelease();
            logger.warn("unable to write to channel",e);
            closeChannel(channel);
            return true;
        }
    }

    private void closeChannel(SocketChannel channel) {
        try {
            channel.close();
        } catch (IOException e1) {
            logger.warn("unable co close channel",e1);
        }
    }

    private void markDoneAndRelease() {
    	
        writeDone = true;
        Pipe.confirmLowLevelRead(dataToSend[activePipe], Pipe.sizeOf(dataToSend[activePipe], activeMessageId));
        Pipe.releaseReadLock(dataToSend[activePipe]);
        
        //logger.info("done and release message {}",pipe);
    }

    
}
