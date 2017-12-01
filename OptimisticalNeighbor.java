import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.net.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

public class OptimisticalNeighbor implements Runnable {
   peerProcess peer;
 


   Map<Integer, DataOutputStream> allOutStream = new HashMap<Integer, DataOutputStream>(); /*store all output stream*/
                                                                                              /*I need this to send have message to all neighbors*/
                                                                                              /*Integer: neighbor peer ID*/
                                                                                              /*ObjectOutputStream: their output stream*/
  SocketHandler socket;
  //ScheduledExecutorService  executor;
   public OptimisticalNeighbor(SocketHandler socket, peerProcess peer, Map<Integer,DataOutputStream> allOutStream){
     this.socket = socket;
     this.peer = peer;
     this.allOutStream = allOutStream;
     //this.executor = executor;
   }

  /*public synchronized void sendMessage(DataOutputStream out, byte[] msg){
    try{
      //stream write the message
      out.write(msg);
      out.flush();
    }
    catch(IOException ioException){
      ioException.printStackTrace();
    }
  }*/

  public int generateARandomOptimisticalNeighbor(peerProcess peer){
    int peerId;

    boolean flag = true; /*flag to check if there is a neighbor is choke and interestd in me*/

    /*if there is not choke neighbor, then return -1*/
    for(int i = 0; i < peer.neighborIChoke.size(); i++){
      int id = (int)peer.neighborIChoke.keySet().toArray()[i];
      /*If there is a choke neighbor, set the flag*/
      if((boolean)peer.neighborIChoke.get(id) == true &&
        (boolean)peer.isInterested.get(id) == true){
        flag = false;
        break;
      }
    }

    if(flag) return -1;

    while(true){
      Random generator = new Random();
      int size = peer.neighborIChoke.size();
      int index = generator.nextInt(size); /*generate a number from 0 to size-1*/  
      peerId = (int)(peer.neighborIChoke.keySet().toArray()[index]);

      boolean isChokeByMe = peer.neighborIChoke.get(peerId);
      boolean isInterestedMe = peer.isInterested.get(peerId);

      /*pick the choke neighbor and interested neighbor*/
      if(isChokeByMe == true && isInterestedMe == true){
        break;
      }
    }

    return peerId;
  }

  public void run(){

    if(peer.finish){
      System.exit(0);
    }

    int optimisticalId = generateARandomOptimisticalNeighbor(peer);
    if(optimisticalId == -1) return;

    if(peer.optimistically_neighbor != optimisticalId){
        String filename = "./peer_" + peer.peerId + "/log_peer_" + peer.peerId + ".log";
        String context = "Peer " + peer.peerId + " has the optimistically unchoke neighbor " + optimisticalId;
        Utilities.writeToFile(filename, context);
        peer.optimistically_neighbor = optimisticalId;
    }

    DataOutputStream out = allOutStream.get(optimisticalId);

    peer.neighborIChoke.put(optimisticalId, false);

    message unchokeMsg = (new message()).unchoke();

    /*send a unchoke message*/
    Utilities util = new Utilities();
    util.sendMessage(out, unchokeMsg.message);

    System.out.println("Peer " + peer.peerId + ": unchoke message send to " + optimisticalId + "in optimistical!!!!!!!!!!!!");

  }
}