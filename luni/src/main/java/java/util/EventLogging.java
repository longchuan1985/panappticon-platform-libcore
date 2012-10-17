package java.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.System;

import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
/**
 * @hide
*/
public class EventLogging {
  private static EventLogging instance = new EventLogging();

  //Event types
  public static final int MSG_ENQUEUE = 0;/* enqueue a message*/
  public static final int MSG_ENQUEUE_DELAYED = 1;/* dequeue a message*/
  public static final int MSG_DEQUEUE = 2;/* dequeue a message*/
  public static final int UI_TRAVERSAL = 3;/* trigger a UI update*/
  public static final int UI_INPUT = 4;/* a new user input*/
  public static final int EVENT_FOREGROUND = 5;/* start a foreground app*/
  public static final int EVENT_EXIT_FOREGROUND = 6;/* exit a foreground app*/
  public static final int EVENT_SUBMIT_ASYNCTASK = 7;/* submit an asynctask from one thread */ 
  public static final int EVENT_CONSUME_ASYNCTASK = 8; /* consume an asynctask on another thread */
  public static final int EVENT_UPLOAD_TRACE = 9; /* start to upload a trace from the collector */
  public static final int EVENT_UPLOAD_DONE = 10;/* finish the uploading */
  public static final int EVENT_WRITE_TRACE = 11;/* start write trace to sdcard*/
  public static final int EVENT_WRITE_DONE = 12;/* write trace done */
  public static final int MSG_POLL_NATIVE = 13;/* before poll message from the native queue */
  public static final int MSG_POLL_DONE = 14;/* poll message done */
  public static final int EVENT_SWITCH_CONFIG = 15;/* the client program changes the core/dvfs config of the device */

  //Queue size parameters
  private static final int EVENT_BYTES = 8 + 4*4;// 24 bytes total for each event
  private static final int QUEUE_SIZE = EVENT_BYTES * 5000; //Number of bytes in each buffer, 120K bytes total	
  
  private static final int COLLECTOR_PORT = 1234;

  public static EventLogging getInstance(){
    return instance;
  }
  
  private ByteBuffer [] EventQueue;
  private int [] QueueLength;
  private int CurQueue;

  private EventLogging(){
    EventQueue = new ByteBuffer [2];
    EventQueue[0] = ByteBuffer.allocate(QUEUE_SIZE);
    EventQueue[1] = ByteBuffer.allocate(QUEUE_SIZE);
    EventQueue[0].order(ByteOrder.LITTLE_ENDIAN);
    EventQueue[1].order(ByteOrder.LITTLE_ENDIAN);
    QueueLength = new int [2];
    QueueLength[0] = 0;
    QueueLength[1] = 0;
    CurQueue = 0;
  }

  public void addEvent(int eventtype, int qid, int mid){
     long timestamp = getTime();
     int tid = getMyTid();
     synchronized (this){
     	EventQueue[CurQueue].putLong(timestamp).putInt(eventtype).putInt(tid).putInt(qid).putInt(mid);
	QueueLength[CurQueue] += EVENT_BYTES;
        if (QueueLength[CurQueue] + EVENT_BYTES >= QUEUE_SIZE){
	  exportQueue();
	}
      }
  }

  public void addEvent(int eventtype, int runnable_code){
     long timestamp = getTime();
     int tid = getMyTid();
     synchronized (this){
     	EventQueue[CurQueue].putLong(timestamp).putInt(eventtype).putInt(tid).putInt(runnable_code).putInt(0);
	QueueLength[CurQueue] += EVENT_BYTES;
        if (QueueLength[CurQueue] + EVENT_BYTES >= QUEUE_SIZE){
	  exportQueue();
	}
      }
     
  }

 public void addEvent(int eventtype){
    long timestamp = getTime();
    int tid = getMyTid();
     synchronized (this){
     	EventQueue[CurQueue].putLong(timestamp).putInt(eventtype).putInt(tid).putInt(0).putInt(0);
	QueueLength[CurQueue] += EVENT_BYTES;
        if (QueueLength[CurQueue] + EVENT_BYTES >= QUEUE_SIZE){
	  exportQueue();
	}
     }
    
  }
  public void onPauseExport(){
    synchronized(this){
	exportQueue();
    }
  }

  public void exportQueue(){
     byte [] cur_array = new byte [QueueLength[CurQueue]];
     System.arraycopy(EventQueue[CurQueue].array(), 0, cur_array, 0, QueueLength[CurQueue]);
     Send sender = new Send(cur_array, QueueLength[CurQueue]);
     Thread senderThread = new Thread(sender);
     senderThread.start();
     EventQueue[CurQueue].rewind();
     QueueLength[CurQueue] = 0;
     CurQueue = 1 - CurQueue;
  }
  
  private class Send implements Runnable{
    private byte [] buffer_to_send;
    private int buffer_length;
    
    public Send(byte [] buffer, int length){
      buffer_to_send = buffer;
      buffer_length = length;
    }
    
    @Override
    public void run(){
      try{
          Socket senderSocket = new Socket("localhost", COLLECTOR_PORT);
          BufferedOutputStream out = new BufferedOutputStream(senderSocket.getOutputStream(),1024);
	  out.write(Integer.toString(buffer_length).getBytes());
          out.write(0);
          out.write(buffer_to_send); 
          out.flush();
          senderSocket.close();       
        }catch(IOException e){
          e.printStackTrace();
        }            
  
    }
  }

  private native long getTime();
  private native int getMyTid();
}
 
