/*
 *                     CEN5501C Project2
 * This is the program starting remote processes.
 * This program was only tested on CISE SunOS environment.
 * If you use another environment, for example, linux environment in CISE 
 * or other environments not in CISE, it is not guaranteed to work properly.
 * It is your responsibility to aegins remote peer processes. 
 * It reads configuration filedapt this program to your running environment.
 */

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.net.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * The StartRemotePeers class b PeerInfo.cfg and starts remote peer processes.
 * You must modify this program a little bit if your peer processes are written in C or C++.
 * Please look at the lines below the comment saying IMPORTANT.
 */
public class peerProcess {
    /*My own Info*/
    int peerId; //my peerID
    int port;  //my port number
    int numberOfPiece;
    int numberOfPeer;

    int numberOfPieceIhave = 0;

    int filesize;
    boolean finish = false;

    byte [] requestedBitfield;
    Map<Integer,Boolean> neighborIChoke = new HashMap<Integer, Boolean>();  //the neighbor send me choke message
                                                                     // Integer: peerID || Boolean: true(choke)

    Map<Integer,Boolean> neighborIInterested = new HashMap<Integer, Boolean>();  //the neighbor send me choke message
                                                                     // Integer: peerID || Boolean: true(choke)

    List<Integer> preferred_neighbors = new ArrayList<Integer>();

    int optimistically_neighbor = -1;

    /*neighbor Info*/
    List<RemotePeerInfo> NeighborPeerInfo = new ArrayList<RemotePeerInfo>();  //store all its neighbor info
                                                                              //RemotePeerInfo has variable
                                                                              //peerID, peerAddress, peerPort, haveFile

    Map<Integer,Boolean> isChoke = new HashMap<Integer, Boolean>();  //the neighbor send me choke message
                                                                     // Integer: peerID || Boolean: true(choke)    


    Map<Integer,Boolean> isInterested = new HashMap<Integer, Boolean>(); //neighbor who is interested in my pieces (send me a interesting message)
                                                                        // Integer: peerID || Boolean: true(interested)

    Map<Integer, Double> downloadRate = new HashMap<Integer, Double>(); //My downloading rate for neighbors

    Map<Integer, Socket> neighborSocket = new HashMap<Integer, Socket>();//neighbor's socket
    																	 //Integer: peerID || Socket: their socket

    /*Everyone's bitfield (including myself)*/
    Map<Integer,byte[]> bitfieldMap = new HashMap<Integer, byte[]>(); // Everyone's bitfield
                                                                      //Integer：peerID || byte[]: bitfield

    public peerProcess(int peerId){
    	this.peerId = peerId;
      
    	fileInfo fileInfo = new fileInfo();
      this.numberOfPeer = fileInfo.peerInfoArray.size();
      this.filesize = fileInfo.FileSize;
        /*Calculate the number of piece*/
    	if((fileInfo.FileSize % fileInfo.PieceSize ) != 0 ) 
    		numberOfPiece = (fileInfo.FileSize / fileInfo.PieceSize) + 1;  
    	else
    		numberOfPiece = fileInfo.FileSize / fileInfo.PieceSize;

        /*fileInfo has all peer Info array*/
    	List<RemotePeerInfo> peerInfoArray = fileInfo.peerInfoArray;

        /*store all my neighbor Info into isChoke and isInterested except myself*/
        /*the default vallue for isChoke is true*/
        /*the default value for isInterested is false*/
        /*default value for downloadRate is 0*/

    	for(int i = 0; i < peerInfoArray.size(); i++){
    		int id = Integer.parseInt(peerInfoArray.get(i).peerId);
    		if(this.peerId == id){ 
    			port = Integer.parseInt(peerInfoArray.get(i).peerPort);
    			continue;
    		}
    		else{
    			NeighborPeerInfo.add(peerInfoArray.get(i));  /*add new value into NeighborPeerInfo*/
    			neighborIChoke.put(id, true);
          neighborIInterested.put(id, false);
    			isChoke.put(id, true); /*add new value into isChoke*/
    			isInterested.put(id, false); /*add new value into isInterested*/
    			downloadRate.put(id, 0.0); /*set initial download rate to 0*/
    		}
    	}

        /*convert number of bit to number of Byte to store the bitfield*/
        int sizeOfbitfield = ((numberOfPiece%8) == 0? numberOfPiece/8: numberOfPiece/8 + 1);

        byte [] empty = new byte[sizeOfbitfield];

        for(int j = 0; j < sizeOfbitfield; j++){
           empty[j] = (byte)0x00; /*empty[] = 0000 0000*/
        }

        byte [] full = new byte[sizeOfbitfield];
           /*set bitfield to all one*/
        for(int j = 0; j < numberOfPiece; j++){
            Utilities.setBitInBitfield(full, j);  /*all 1 for bitfield*/
         }

        requestedBitfield = empty; /*set requested bitfield to all 0*/

        /*set the bitfield to all one if the peer has complete file*/
        /*set the bitfield to all zero if the peer has not file*/
        /*After I set up the bitfield of my own, I break out the loop, because I dont want to set up someone else bitfield*/
        /*If I want to set up someone's bitfield, I need to receive bitfield message*/
    	for(int i = 0; i < peerInfoArray.size(); i++){
          int id = Integer.parseInt(peerInfoArray.get(i).peerId);
          if(peerInfoArray.get(i).haveFile == true && id == peerId){
            bitfieldMap.put(id, full);
            break;
          }  
          else if(peerInfoArray.get(i).haveFile == false && id == peerId){
            bitfieldMap.put(id, empty);
            break;
          }
    	}

    }

    /*build up socket between peers
    Ex. peer 1000 need to build socket with all its neighbor socket*/
    public void buildSocket()  {
      try{
        ServerSocket serverSocket = new ServerSocket(port);

        for(int i = 0; i < NeighborPeerInfo.size(); i++){
        	int neighborId = Integer.parseInt(NeighborPeerInfo.get(i).peerId);
      	  String neighborHost = NeighborPeerInfo.get(i).peerAddress;
      	  int neighborPort = Integer.parseInt(NeighborPeerInfo.get(i).peerPort);
   
          /*the peer process with peer ID 1003 in the above example should 
            make TCP connections to the peer processes with peer ID 1001 and peer ID 1002. In 
            the same way, the peer process with peer ID 1004 should make TCP connections to the 
            peer processes with peer ID 1001, 1002, and 1003.*/

          /*If my Id greater than neighbor ID, then I send TCP connection to them;*/
          /*else I will wait to them to send a TCP connection to me*/
          if(peerId > neighborId){
        	  try{
                 Socket socket = new Socket(neighborHost, neighborPort);
                 neighborSocket.put(neighborId, socket);
                 //System.out.println(socket);

                 String filename = "./peer_" + peerId + "/log_peer_" + peerId + ".log";
                 String context = "Peer " + peerId + " makes a connection to Peer " + neighborId;
                 Utilities.writeToFile(filename, context);
                 System.out.println("Peer " + peerId + " makes a connection to Peer " + neighborId);
            }
            catch(Exception e){
            	System.out.println(e);
            }
          }
          else{
              //System.out.println("peer ID: " + peerId + " listen to " + port);
        	  try{
                Socket socket = serverSocket.accept();

                 String filename = "./peer_" + peerId + "/log_peer_" + peerId + ".log";
                 String context = "Peer " + peerId + " is connected from Peer " + neighborId;
                 Utilities.writeToFile(filename, context);

                neighborSocket.put(neighborId, socket);
                //System.out.println(socket);
              }
            catch(Exception e){
            	System.out.println(e);
            }            
          }  
        }
      }
      catch(Exception e){
        System.out.println(e);
      }
      
    }

    /*test My constructor*/
	public static void main(String[] args) throws IOException{
		/**************************Test Code**********************************************************************/
        /*int id =1000;
        peerProcess test = new peerProcess(id);
       // test.buildSocket();

        int size = test.NeighborPeerInfo.size();

        System.out.println("\nNeighbor info");
        for(int i=0; i<size; i++){
         System.out.println(test.NeighborPeerInfo.get(i).peerId + " " + 
            test.NeighborPeerInfo.get(i).peerAddress + " " + 
            test.NeighborPeerInfo.get(i).peerPort + " " + 
            test.NeighborPeerInfo.get(i).haveFile);
        }

        System.out.println("\nisChoke info");
        for(Map.Entry<Integer,Boolean> entry : test.isChoke.entrySet()){
          System.out.println("My Id: " + test.peerId + "  is Choked for " + entry.getKey() + ": " + entry.getValue());

        }

        System.out.println("\nisInterested info");
        for(Map.Entry<Integer,Boolean> entry : test.isInterested.entrySet()){
          System.out.println("My Id: " + test.peerId + "  is Interested in " + entry.getKey() + ": " + entry.getValue());
        }

        System.out.println("\nDownload info");
        for(Map.Entry<Integer,Integer> entry : test.downloadRate.entrySet()){
          System.out.println("My Id: " + test.peerId + "  download Rate from peer " + entry.getKey() + ": " + entry.getValue());
        }

        System.out.println("\nneighborSocket info");
        for(Map.Entry<Integer,Socket> entry : test.neighborSocket.entrySet()){
          System.out.println("My Id: " + test.peerId + "  neighbor Socket from peer " + entry.getKey() + ": " + entry.getValue());
        }
        

        int numberOfPiece = test.numberOfPiece;
        int peerId = test.peerId;
        int port = test.port;
        int sizeOfBitfield = ((test.numberOfPiece % 8) == 0? numberOfPiece/8: numberOfPiece/8 + 1 );
        
        System.out.println("\nnumber of piece = " + numberOfPiece);
        System.out.println("peer ID = " + peerId);
        System.out.println("port = " + port);
        System.out.println("\nBitfield info");
        for(Map.Entry<Integer,byte[]> entry : test.bitfieldMap.entrySet()){
          System.out.print("Bitfield for " + entry.getKey() + ": ");
          for(int i = 0; i < sizeOfBitfield; i++){
            System.out.print(entry.getValue()[i] + " ");
          }
          System.out.println("\n");
        }

        byte[] value = test.bitfieldMap.get(1000);
        for(int i = 0; i < numberOfPiece; i++){
          boolean result = Utilities.isSetBitInBitfield(value, i);
          System.out.println("piece " + i + " is set: " + result);
        }
        
        System.out.println("Do I hava Complete file: " + Utilities.checkForCompelteFile(value, test.numberOfPiece));

        System.out.println("requested bitfield: ");
        value = test.requestedBitfield;
        for(int i = 0; i < numberOfPiece; i++){
          boolean result = Utilities.isSetBitInBitfield(value, i);
          System.out.println("piece " + i + " is set: " + result);
        }

       System.out.println("Do I hava Complete file: " + Utilities.checkForCompelteFile(value, test.numberOfPiece));*/
     /*Thread peer1000 = new Thread(new SocketHandler(1000));
     Thread peer1001 = new Thread(new SocketHandler(1001));
     Thread peer1002 = new Thread(new SocketHandler(1002));
     Thread peer1003 = new Thread(new SocketHandler(1003));
     peer1000.start();
     peer1001.start();
     peer1002.start();
     peer1003.start();*/
     int id = Integer.parseInt(args[0]);
     Thread peer = new Thread(new SocketHandler(id));
     peer.start();
		/**************************Test Code End Here**********************************************************************/

    }
}
