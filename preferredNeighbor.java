import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.net.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class preferredNeighbor implements Runnable {
   peerProcess peer;

   Map<Integer, DataOutputStream> allOutStream = new HashMap<Integer, DataOutputStream>(); /*store all output stream*/
                                                                                              /*I need this to send have message to all neighbors*/
                                                                                              /*Integer: neighbor peer ID*/
                                                                                              /*ObjectOutputStream: their output stream*/
  SocketHandler socket;
  int NumberOfPreferredNeighbors;
 // ScheduledExecutorService  executor;

   public preferredNeighbor(SocketHandler socket, peerProcess peer, Map<Integer,DataOutputStream> allOutStream){
     this.socket = socket;
     this.peer = peer;
     this.allOutStream = allOutStream;
     //this.executor = executor;
     fileInfo info = new fileInfo();
     NumberOfPreferredNeighbors = info.NumberOfPreferredNeighbors;
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

    public void run(){
      synchronized(StartRemotePeers.class){
        if(peer.finish){
          System.exit(0);
        }

        /*sort the download map*/
        /*sort the download rate from high to low*/
        List<Entry<Integer, Double>> list = new LinkedList<>(peer.downloadRate.entrySet());
        Collections.sort(list, new Comparator<Object>() {
          @SuppressWarnings("unchecked")
            public int compare(Object o1, Object o2) {
                return -((Comparable<Double>) ((Map.Entry<Integer, Double>) (o1)).getValue()).compareTo(((Map.Entry<Integer, Double>) (o2)).getValue());
            }
        });

        Map<Integer, Double> sorted_downloadRate = new LinkedHashMap<>();
        for (Iterator<Entry<Integer, Double>> it = list.iterator(); it.hasNext();) {
            Map.Entry<Integer, Double> entry = (Map.Entry<Integer, Double>) it.next();
            sorted_downloadRate.put(entry.getKey(), entry.getValue());
        }
        /*sort the download map end here*/
    

        int size;
        if(sorted_downloadRate.size() > NumberOfPreferredNeighbors){
          int numberOfNeighborIsPick = 0;

          List<Integer> new_preferred_neighbors = new ArrayList<Integer>();

          /*send unchoke message to neighbor who is interested in me and has fast download rate*/
          for(int i = 0; i < sorted_downloadRate.size(); i++){
                int neighborId = (int)(sorted_downloadRate.keySet().toArray()[i]);
                boolean neighborIsChoke = peer.neighborIChoke.get(neighborId);
                boolean neighborIsInterested = peer.isInterested.get(neighborId);
                DataOutputStream out = allOutStream.get(neighborId);

                if(neighborIsInterested == false && neighborIsChoke == false){
                    peer.neighborIChoke.put(neighborId, true);
                    
                    message chokeMsg = (new message()).choke();

                    /*send a unchoke message*/
                    Utilities util = new Utilities();
                    util.sendMessage(out, chokeMsg.message);

                    System.out.println("Peer " + peer.peerId + ": choke message send to " + neighborId);

                    continue;
                }

                if(neighborIsInterested == false) continue;

                numberOfNeighborIsPick++;
                if(numberOfNeighborIsPick > NumberOfPreferredNeighbors){
                    if(neighborIsChoke == true) continue;
                    if(neighborId == peer.optimistically_neighbor) continue;
                    
                    peer.neighborIChoke.put(neighborId, true);

                    message chokeMsg = (new message()).choke();

                    /*send a unchoke message*/
                    Utilities util = new Utilities();
                    util.sendMessage(out, chokeMsg.message);

                    System.out.println("Peer " + peer.peerId + ": choke message send to " + neighborId);

                }
                else{
                  new_preferred_neighbors.add(neighborId);
                  if(neighborId == peer.optimistically_neighbor) continue;
                  /*If neighbor is unchoke already, we do not have to send unchoke message*/
                  /*We just need to send request message*/
                  if(neighborIsChoke == false){
                      //peer.neighborIChoke.put(neighborId, false);
                      continue;
                  } 

                  peer.neighborIChoke.put(neighborId, false);
                  message unchokeMsg = (new message()).unchoke();

                  /*send a unchoke message*/
                  Utilities util = new Utilities();
                  util.sendMessage(out, unchokeMsg.message);

                  System.out.println("Peer " + peer.peerId + ": unchoke message send to " + neighborId);
                }
          }

          if(Utilities.equalLists(new_preferred_neighbors, peer.preferred_neighbors) == false){
              String filename = "./peer_" + peer.peerId + "/log_peer_" + peer.peerId + ".log";
              String context = "Peer " + peer.peerId + ": has the preferred neighbors";
              for(int i = 0; i < new_preferred_neighbors.size(); i++){
                 context = context + " " + new_preferred_neighbors.get(i);

                 if(i != (new_preferred_neighbors.size()-1)){
                  context += ",";
                 }
              }
              peer.preferred_neighbors = new_preferred_neighbors;
              Utilities.writeToFile(filename, context);
          }
        }

        /*If number of preferred neighbor is greater than number of neighbor*/
        /*all neighbor are optimistic neighbor*/
        else{
          size = sorted_downloadRate.size();
          List<Integer> new_preferred_neighbors = new ArrayList<Integer>();

          /*send unchoke message to neighbor with fast download rate*/
          for(int i = 0; i < size; i++){
              int neighborId = (int)(sorted_downloadRate.keySet().toArray()[i]);

                boolean neighborIsChoke = peer.neighborIChoke.get(neighborId);
                boolean neighborIsInterested = peer.isInterested.get(neighborId);

                DataOutputStream out = allOutStream.get(neighborId);

                if(neighborIsInterested == false && neighborIsChoke == false){
                    peer.neighborIChoke.put(neighborId, true);

                    message chokeMsg = (new message()).choke();

                    /*send a unchoke message*/
                    Utilities util = new Utilities();
                    util.sendMessage(out, chokeMsg.message);

                    System.out.println("Peer " + peer.peerId + ": choke message send to " + neighborId + "!!!!!!!!!!!!!!!!!!!!");
                    continue;
                }

                
                if(neighborIsInterested == false) continue;

                new_preferred_neighbors.add(neighborId);

                /*If neighbor is unchoke already, we do not have to send unchoke message*/
                if(neighborIsChoke == false){
                   continue;
                }

                message unchokeMsg = (new message()).unchoke();

                /*conver object message to byte array*/
                //byte[] unchokeMsgByteArray = Utilities.combineByteArray(unchokeMsg.msgLen, unchokeMsg.msgType);

                /*send a unchoke message*/
                Utilities util = new Utilities();
                util.sendMessage(out, unchokeMsg.message);

                /*I did not choke neighbor*/
                peer.neighborIChoke.put(neighborId, false);

                System.out.println("Peer " + peer.peerId + ": unchoke message send to " + neighborId);
          }  

          if(Utilities.equalLists(new_preferred_neighbors, peer.preferred_neighbors) == false){
              String filename = "./peer_" + peer.peerId + "/log_peer_" + peer.peerId + ".log";
              String context = "Peer " + peer.peerId + ": has the preferred neighbors";

              for(int i = 0; i < new_preferred_neighbors.size(); i++){
                 context = context + " " + new_preferred_neighbors.get(i);

                 if(i != (new_preferred_neighbors.size()-1)){
                    context += ",";
                 }
              }

              peer.preferred_neighbors = new_preferred_neighbors;
              Utilities.writeToFile(filename, context);
          }       

        }
      }

    }
}