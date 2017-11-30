
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
public class ReceiveHandler implements Runnable {
  peerProcess peer;
  int neighborId;
  DataOutputStream out;         //stream write to the socket
  DataInputStream in;

  Map<Integer,DataOutputStream> allOutStream = new HashMap<Integer, DataOutputStream>(); /*store all output stream*/
                                                                                              /*I need this to send have message to all neighbors*/
                                                                                              /*Integer: neighbor peer ID*/
                                                                                              /*ObjectOutputStream: their output stream*/
  Map<Integer,DataInputStream> allInputStream = new HashMap<Integer, DataInputStream>();

  long startDownloadTime;
  long stopDownloadTime;
  private static Lock lock = new ReentrantLock();
  public ReceiveHandler(peerProcess peer, int neighborId, DataInputStream in, DataOutputStream out, Map<Integer,DataOutputStream> allOutStream,Map<Integer,DataInputStream> allInputStream){
    this.peer = peer;
    this.neighborId = neighborId;
    this.out = out;
    this.in = in;
    this.allOutStream = allOutStream;
    this.allInputStream = allInputStream;
  }

  /*static public void sendMessage(DataOutputStream outstream, byte[] msg){
    try{
        //stream write the message
        synchronized(ReceiveHandler.class){
        outstream.write(msg);
        outstream.flush();
      }
    }
    catch(IOException ioException){
      ioException.printStackTrace();
    }
  }*/

  public synchronized void sendMessageToAll(byte[] msg){

        for(Map.Entry<Integer,DataOutputStream> entry : allOutStream.entrySet()){
            DataOutputStream outstream = entry.getValue();
             Utilities util = new Utilities();
             util.sendMessage(outstream, msg);

        }
  }

  public void run() {
    byte[] lengthOfMessage = new byte [4]; /*Store the message length*/
    int length;                     /*Convert lenghtOfMessage to int and store it in lenght*/
    byte[] msgType = new byte[1];

    while(true){
        try{
            /*Handle all kinds of message*/
            /*length == length of message (first 4 bytes)*/
            /*msgType[0] == type of message (byte 5)*/
            /*playload == rest of message (all byte after byte 5)*/
            in.readFully(lengthOfMessage, 0, lengthOfMessage.length);

            length = Utilities.ByteArrayToint(lengthOfMessage);

            /*Read one byte from socket*/
            in.readFully(msgType, 0, msgType.length);
     
       
            /*If it is handshake message, then the fifth char is I*/
            byte flagForHandshake = 'I';

            /*Handle handshake message*/
            if(msgType[0] == flagForHandshake){
          	  handleHandshakeMessage(lengthOfMessage, msgType);    
            }
            /*Handle Actual message*/
            else{
                /*choke = 0*/
                /*unchoke = 1*/
                /*interested = 2*
                /*notInterested = 3*/
                /*have = 4*/
                /*bitfield = 5*/
                /*request = 6*/
                /*piece = 7*/
                if(msgType[0] == 0){
                  handleChokeMessage();
                }
                else if(msgType[0] == 1){
                  handleUnchokeMessage();
                }
                else if(msgType[0] == 2){
                  handleInterestedMessage();
                }
                else if(msgType[0] == 3){
                  handleNotInterestedMessage();    
                }
                else if(msgType[0] == 4){
                  byte [] playload = new byte[length-1];
                  in.readFully(playload, 0, playload.length);

                  handleHaveMessage(playload);
                }
                else if(msgType[0] == 5){
                  byte [] playload = new byte[length-1];
                  in.readFully(playload, 0, playload.length);

                  handleBitfieldMessage(playload);
                }
                else if(msgType[0] == 6){
                  byte [] playload = new byte[length-1];
                  in.readFully(playload, 0, playload.length);

                  handleRequestMessage(playload);
                }
                else if(msgType[0] == 7){
                  byte [] playload = new byte[length-1];
                  in.readFully(playload, 0, playload.length);

                  handlePieceMessage(playload);
                }
            }


          }
          catch(Exception e){
             System.out.println(e);
          }

          int numberOfPiece = peer.numberOfPiece;
          int numOfPeerHaveCompleteFile = 0;
          for(Map.Entry<Integer,byte[]> entry : peer.bitfieldMap.entrySet()){
            if(Utilities.checkForCompelteFile(entry.getValue(), numberOfPiece))
            numOfPeerHaveCompleteFile++;
          }

          int numberOfPeer = peer.numberOfPeer;

          /*When everyone has complete file, and not input from inputstream, stop the system*/
         if(numOfPeerHaveCompleteFile == numberOfPeer){
            Utilities.threadSleep(4000);      
          try{
              if(in.available() == 0) {
                          System.out.println("end of for loop~~~~~!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                for(Map.Entry<Integer,DataOutputStream> entry : allOutStream.entrySet()){
                  DataOutputStream outputStream = entry.getValue();
                  outputStream.close();
                }

                for(Map.Entry<Integer,DataInputStream> entry : allInputStream.entrySet()){
                  DataInputStream inputStream = entry.getValue();
                  inputStream.close();
                }

                for(Map.Entry<Integer,Socket> entry : peer.neighborSocket.entrySet()){
                  Socket socket = entry.getValue();
                  socket.close();
                }

                System.exit(0);
              }
          }catch(IOException e){
            System.out.println(e);
          }
          }
        
    }
  }

  public void handleHandshakeMessage(byte[] lengthOfMessage, byte[] msgType){
    try{
        System.out.println("Peer " + peer.peerId + ": receive handshake message from " + neighborId); 
        byte [] restByte = new byte[27];

        /*Read the another 27 byte from socket*/
        in.readFully(restByte, 0, restByte.length);

        /*Handshake message is 32 bytes*/
        /*Combine first 4 bytes + 1 + 27 bytes into 32 bytes*/
        byte[] message = Utilities.combineByteArray(lengthOfMessage, msgType);
        message = Utilities.combineByteArray(message, restByte);      

        byte[] msgHeader = Arrays.copyOfRange(message, 0, 18); /* copy index from 0 to 18 (not include 18)*/
        String msgHeaderInString = new String(msgHeader); /*convert byte array to String*/
  
        byte[] msgId = Arrays.copyOfRange(message, 28, 32); /* copy index from 28 to 32 (not include 32)*/
        int msgIdInInt = Utilities.ByteArrayToint(msgId); /*convert byte array to int*/
                
        //System.out.println("Msg ID: " +  msgIdInInt + "  Except value is : " + neighborId);
                
        /*check whether the handshake header is right and the peer ID is the expected one. */
        if("P2PFILESHARINGPROJ".equals(msgHeaderInString) && msgIdInInt == neighborId){
            System.out.println("peer " + peer.peerId + " correctly receive handshake message from " + neighborId);
        }else{
            System.out.println("peer " + peer.peerId + " does not correctly receive handshake message from " + neighborId);
        }

        /*******************************************Send Bitfield ******************************************************/
        byte[] bitfield = peer.bitfieldMap.get(peer.peerId); 

        message bitfieldMsg = (new message()).bitfield(bitfield);

        /*conver object message to byte array*/
   //     byte[] bitfieldMsgByteArray = Utilities.combineByteArray(bitfieldMsg.msgLen, bitfieldMsg.msgType);
     //   bitfieldMsgByteArray = Utilities.combineByteArray(bitfieldMsgByteArray, bitfieldMsg.payload);
        Utilities util = new Utilities();
        util.sendMessage(out, bitfieldMsg.message);
          
        System.out.println("Peer " + peer.peerId + ": Bitfield message is sent to " + neighborId);
    }catch(Exception e){
      System.out.println("Error on reciving handshake message");
    }
  }

   public synchronized void handleChokeMessage(){

      System.out.println("Peer " + peer.peerId + ": receive choke message from " + neighborId);
      peer.isChoke.put(neighborId, true);
      peer.downloadRate.put(neighborId, 0.0); // set the download rate from this neighbor to 0
                                                          // Because it sends me a choke message
      String filename = "./peer_" + peer.peerId + "/log_peer_" + peer.peerId + ".log";
      String context = "Peer " + peer.peerId + " is choked by Peer " + neighborId;
      Utilities.writeToFile(filename, context);
  }

  public  synchronized void handleUnchokeMessage(){
    System.out.println("Peer " + peer.peerId + ": receive unchoke message from " + neighborId);
    peer.isChoke.put(neighborId, false);

    String filename = "./peer_" + peer.peerId + "/log_peer_" + peer.peerId + ".log";
    String context = "Peer " + peer.peerId + " is unchoked by Peer " + neighborId;
    Utilities.writeToFile(filename, context);

    byte [] neighborBitfieldMap = peer.bitfieldMap.get(neighborId);
    byte [] myBitfieldMap = peer.bitfieldMap.get(peer.peerId);
    int numberOfPiece = peer.numberOfPiece;

    //boolean completeFile = Utilities.checkForCompelteFile(myBitfieldMap, numberOfPiece);
    if(peer.numberOfPieceIhave == numberOfPiece){
      System.out.println("COMPLETE FILE");
    }
    else{
        int desiredIndex = getDesiredIndex(myBitfieldMap, neighborBitfieldMap);

        if(desiredIndex == -1) return; 
        /***send the request message to neighbor***/
        message requestMsg = (new message()).request(desiredIndex); /*create a message object*/
        //byte[] requestMsgByteArray = Utilities.combineByteArray(requestMsg.msgLen, requestMsg.msgType);//conver object message to byte array
       // requestMsgByteArray = Utilities.combineByteArray(requestMsgByteArray, requestMsg.payload); //conver object message to byte array

                /*set a start time before send data*/
        startDownloadTime = System.currentTimeMillis();
        Utilities util = new Utilities();
        util.sendMessage(out, requestMsg.message);

        Utilities.threadSleep(10);
        /*set requestedBitfield after send request message to advoid request same piece from different neighbor*/
       /*synchronized(this){
          Utilities.setBitInBitfield(peer.requestedBitfield, desiredIndex);
        }*/

        System.out.println("Peer:" + peer.peerId + ": send request message to " + neighborId);
    }
  }

  public synchronized int getDesiredIndex(byte [] myBitfieldMap, byte [] neighborBitfieldMap){

    lock.lock();
    /****Get random interesting piece from neighbor ****/
    int desiredIndex;
    Random rand = new Random();

    boolean noDesiredIndex = Arrays.equals(myBitfieldMap, neighborBitfieldMap);
  
    if(noDesiredIndex) return -1;

    while(true){
      desiredIndex = rand.nextInt(peer.numberOfPiece); /*generate random number from 0 to (numberOfPiece-1)*/

      /*Break out the loop until find a valid index*/
      if(Utilities.isSetBitInBitfield(myBitfieldMap, desiredIndex) == false && 
        Utilities.isSetBitInBitfield(neighborBitfieldMap, desiredIndex) == true && 
        Utilities.isSetBitInBitfield(peer.requestedBitfield, desiredIndex) == false){
           // synchronized(this){
              Utilities.setBitInBitfield(peer.requestedBitfield, desiredIndex);
              break;
            //}
      }
    }
    lock.unlock();
    return desiredIndex;
  }

  public void handleInterestedMessage(){
    System.out.println("Peer " + peer.peerId + ": receive interested message from " + neighborId);

    String filename = "./peer_" + peer.peerId + "/log_peer_" + peer.peerId + ".log";
    String context = "Peer " + peer.peerId + " received the 'interested' message from " + neighborId;
    Utilities.writeToFile(filename, context);

    /*If receive interested message, then change the isInterested talbe*/
    synchronized(this){
      peer.isInterested.put(neighborId, true);
    }
  }

  public void handleNotInterestedMessage(){
    System.out.println("Peer " + peer.peerId + ": receive not Interested message from " + neighborId);

    String filename = "./peer_" + peer.peerId + "/log_peer_" + peer.peerId + ".log";
    String context = "Peer " + peer.peerId + " received the 'not interested' message from " + neighborId;
    Utilities.writeToFile(filename, context);

    /*If receive interested message, then change the isInterested talbe*/
    synchronized(this){
      peer.isInterested.put(neighborId, false);  
    }
  }  

  public synchronized void handleHaveMessage(byte [] playload){
    try{
       System.out.println("Peer " + peer.peerId + ": receive have message from " + neighborId);
       int indexOfPiece = Utilities.ByteArrayToint(playload);
  
       String filename = "./peer_" + peer.peerId + "/log_peer_" + peer.peerId + ".log";
       String context = "Peer " + peer.peerId + " received the 'have' message from Peer " + neighborId + " for the piece " + indexOfPiece;
       Utilities.writeToFile(filename, context);

       /*update the bitfield for neighbor*/
       //byte [] neighborBitfieldMap = peer.bitfieldMap.get(neighborId); /*get bitfield from hash table*/
       //Utilities.setBitInBitfield(neighborBitfieldMap, indexOfPiece); /*update bitfield*/
       //peer.bitfieldMap.put(neighborId, neighborBitfieldMap); /*Store the bitfield back to hashmap*/
       updateBitfield(neighborId, indexOfPiece);

       /*Check if I have that piece or not. If I do not have that piece, send interested message to neighbor*/
       byte [] myBitfieldMap = peer.bitfieldMap.get(peer.peerId);
       if(Utilities.isSetBitInBitfield(myBitfieldMap, indexOfPiece) == false){
         message interestedMsg = (new message()).interested();

         /*conver object message to byte array*/
         //byte[] interestedMsgByteArray = Utilities.combineByteArray(interestedMsg.msgLen, interestedMsg.msgType);
         Utilities util = new Utilities();
         util.sendMessage(out, interestedMsg.message);


         System.out.println("Peer " + peer.peerId + " : send interested message to " + neighborId);

         peer.neighborIInterested.put(neighborId, true);
       }
    }catch(Exception e){
      System.out.println("Error on receiving Have message");
    }
  }

  public synchronized void handleBitfieldMessage(byte [] playload){
    try{
        System.out.println("Peer " + peer.peerId + ": receive bitfield message from " + neighborId); 

        /*receive bitfield and update the bitfield of my neighbor*/
        /*playload inside of message is bitmap of its neighbor*/
        synchronized(this){
          peer.bitfieldMap.put(neighborId, playload);
        }

        /*When it receives bitfield, it also can upldate its isInterested hashmap and decide to send interested message to neighbor or not*/
        int numberOfPiece = peer.numberOfPiece;
        int myId = peer.peerId;
        byte [] myBitfieldMap = peer.bitfieldMap.get(myId);
        byte [] neighborBitfieldMap = peer.bitfieldMap.get(neighborId);
        boolean hasInterestingPiece = false; /*flag to check*/

        /*Try to find interesting piece from neighbor == compare bitmap*/
        /*send a interested message if neighbor has some pieces (al least one) I want*/
        /*send a not interestedm message if neighbor does not have any interesting piece*/
       for(int i = 0 ; i < numberOfPiece; i++){
          if(Utilities.isSetBitInBitfield(myBitfieldMap, i) == false && Utilities.isSetBitInBitfield(neighborBitfieldMap, i) == true){

            hasInterestingPiece = true;
            /*send an interesting message and break*/
            message interestedMsg = (new message()).interested();

            /*conver object message to byte array*/
           // byte[] interestedMsgByteArray = Utilities.combineByteArray(interestedMsg.msgLen, interestedMsg.msgType);
            Utilities util = new Utilities();
            util.sendMessage(out, interestedMsg.message);

            System.out.println("Peer " + myId + ": Interested message is send to " + neighborId);

            peer.neighborIInterested.put(neighborId, true);
            break;
          }
        }

        if(hasInterestingPiece == false){
          /*send an not interesting message*/
          message notInterestedMsg = (new message()).notInterested();

          /*conver object message to byte array*/
          //byte[] notInterestedMsgByteArray = Utilities.combineByteArray(notInterestedMsg.msgLen, notInterestedMsg.msgType);
          Utilities util = new Utilities();
          util.sendMessage(out, notInterestedMsg.message);
          System.out.println("Peer " + myId + ": not Interested message is send to " + neighborId);

          peer.neighborIInterested.put(neighborId, false);
        }
    }
    catch(Exception e){
      System.out.println("Error on receiving bitfield message");
    }
  }

  public synchronized void handleRequestMessage(byte [] playload){
    try{
        System.out.println("Peer " + peer.peerId + ": receive request message from " + neighborId);

        /*******Get piece of data from file********/
        String filename = "./peer_" + peer.peerId + "/" + (new fileInfo().FileName);
        int pieceSize = new fileInfo().PieceSize;
        int indexOfPiece = Utilities.ByteArrayToint(playload);

        int numberOfPiece = peer.numberOfPiece;
        byte [] piece = Utilities.readPieceFromFile(filename, pieceSize, indexOfPiece, numberOfPiece);

        /***send the piece of data to neighbor***/
        message pieceMsg = (new message()).piece(indexOfPiece, piece); /*create a message object*/

        //byte[] pieceMsgByteArray = Utilities.combineByteArray(pieceMsg.msgLen, pieceMsg.msgType);//conver object message to byte array
        //pieceMsgByteArray = Utilities.combineByteArray(pieceMsgByteArray, pieceMsg.payload); //conver object message to byte array
       synchronized(ReceiveHandler.class){
        Utilities util = new Utilities();
        util.sendMessage(out, pieceMsg.message);
       }
        System.out.println("Peer " + peer.peerId + ": Piece message is send to " + neighborId);  
    }
    catch(Exception e){
      System.out.println("Error on receiving request message");
    }
  }

  public byte[] updateBitfield(int peerId, int indexOfPiece){

       /*if(peerId == peer.peerId){
       String filename = "./peer_" + peer.peerId + "/log_peer_" + peer.peerId + ".log";
       String context = ("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
       Utilities.writeToFile(filename, context);
       }*/
       Utilities.threadSleep(50);

        byte [] myBitfieldMap = peer.bitfieldMap.get(peerId); /*get bitfield from hash table*/
        Utilities.setBitInBitfield(myBitfieldMap, indexOfPiece); /*update bitfield*/
        peer.bitfieldMap.put(peerId, myBitfieldMap); /*Store the bitfield back to hashmap*/

    
        return  myBitfieldMap;
  }

  public synchronized void handlePieceMessage(byte [] playload){
    try{
        System.out.println("Peer " + peer.peerId + ": receive piece message from " + neighborId);

        int length = playload.length;

        /*first 4 byte in playload is piece index, rest is actual piece*/ 
        byte [] indexOfPieceByteArray = Arrays.copyOfRange(playload, 0, 4);// read first 4 byte from pllayload

        byte [] piece = Arrays.copyOfRange(playload, 4, length); // Copy rest byte to piece
                    
        int indexOfPiece = Utilities.ByteArrayToint(indexOfPieceByteArray);

        /*store piece into myfile*/
        String filename = "./peer_" + peer.peerId + "/" + (new fileInfo().FileName);
        int pieceSize = new fileInfo().PieceSize;
        int numberOfPiece = peer.numberOfPiece;
        int filesize = peer.filesize;
        Utilities.writePieceToFile(filename, pieceSize, indexOfPiece, piece, numberOfPiece, filesize);

        /*record the download stop time, calculate the current download rate*/
        stopDownloadTime = System.currentTimeMillis();
        double downloadRate = piece.length / (double)(stopDownloadTime - startDownloadTime);

        synchronized(this){
          peer.downloadRate.put(neighborId, downloadRate);
        }

        filename = "./peer_" + peer.peerId + "/log_peer_" + peer.peerId + ".log";
        String context = "Peer " + peer.peerId + " has downloaded the piece " + indexOfPiece + " from " + neighborId;
        Utilities.writeToFile(filename, context);

        byte [] myBitfieldMap;
        boolean completeFile = false;

        synchronized(this){
          myBitfieldMap = updateBitfield(peer.peerId, indexOfPiece);

          //completeFile = Utilities.checkForCompelteFile(myBitfieldMap, numberOfPiece);
          peer.numberOfPieceIhave++;

          if(peer.numberOfPieceIhave == peer.numberOfPiece)
            completeFile = true;

        }
        /*send a have message to all my neighbor*/
        message haveMsg = (new message()).have(indexOfPiece); /*create a message object*/

        sendMessageToAll(haveMsg.message);
        System.out.println("Peer" + peer.peerId + " : Send have message to all neighbors");

        /*check do I need to send an not interested message or not*/ 
        /*If neighbor not longer has any interesting piece, send an not interested message*/
        boolean hasInterestingPiece = false;
        byte [] neighborBitfieldMap = peer.bitfieldMap.get(neighborId);
        for(int i = 0 ; i < numberOfPiece; i++){
          if(Utilities.isSetBitInBitfield(myBitfieldMap, i) == false && Utilities.isSetBitInBitfield(neighborBitfieldMap, i) == true){
              hasInterestingPiece = true;
              break;
          }
        }

        if(hasInterestingPiece == false){

            /*send an not interesting message*/
          message notInterestedMsg = (new message()).notInterested();

          /*conver object message to byte array*/
          //byte[] notInterestedMsgByteArray = Utilities.combineByteArray(notInterestedMsg.msgLen, notInterestedMsg.msgType);
          Utilities util = new Utilities();
          util.sendMessage(out, notInterestedMsg.message);

          peer.neighborIInterested.put(neighborId, false);
          System.out.println("Peer " + peer.peerId + ": not Interested message is send to " + neighborId + "!!!!!!!!!!");
        }
        else{

          /*If I am not choked by neighbor, request more pieces, send a request message*/
          boolean isChokeByNeighbor = peer.isChoke.get(neighborId);

          if(isChokeByNeighbor == false){
              /****Get random interesting piece from neighbor ****/
              int desiredIndex = getDesiredIndex(myBitfieldMap, neighborBitfieldMap);
              if(desiredIndex == -1) return;

              /*set requestedBitfield after send request message to advoid request same piece from different neighbor*/
              //Utilities.setBitInBitfield(peer.requestedBitfield, desiredIndex);

              /***send the request message to neighbor***/
              message requestMsg = (new message()).request(desiredIndex); /*create a message object*/
              //byte[] requestMsgByteArray = Utilities.combineByteArray(requestMsg.msgLen, requestMsg.msgType);//conver object message to byte array
              //requestMsgByteArray = Utilities.combineByteArray(requestMsgByteArray, requestMsg.payload); //conver object message to byte array

                    /*set a start time before send data*/
              startDownloadTime = System.currentTimeMillis();
              Utilities util = new Utilities();
              util.sendMessage(out, requestMsg.message);

              Utilities.threadSleep(50);
              System.out.println("neighborId: " + neighborId + ": desireed index: " + desiredIndex);
              System.out.println("Peer:" + peer.peerId + ": send request message to " + neighborId);
          }
        }

        /*If all piece have been download, then report I receive whole file*/

        if(completeFile){
          context = "Peer " + peer.peerId + " has downloaded the complete file";
          Utilities.writeToFile(filename, context);
          System.out.println("Peer " + peer.peerId + " : I have complete file");

          /*If I have complete file, send not interested message to the one I am interested before*/
          for(Map.Entry<Integer,Boolean> entry : peer.neighborIInterested.entrySet()){
            int id = entry.getKey();
            boolean neighborIInterested = entry.getValue();

            if(neighborIInterested){
            	message notInterestedMsg = (new message()).notInterested();

            	try{
                  DataOutputStream out = allOutStream.get(id);
                  out.write(notInterestedMsg.message);
                  out.flush();
            	}
            	catch(Exception e){
            		System.out.println(e);
            	}

            }
          }
        }

    }catch(Exception e){
        System.out.println("Error on receiving Piece message");    
    }
  }
}