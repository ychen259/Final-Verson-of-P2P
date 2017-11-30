import java.io.*;
import java.nio.*;
import java.util.*;
import java.text.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
public class Utilities {  
  private static Lock lock = new ReentrantLock();

  /*result[0] store left most byte of number*/
  /*For example if i = 256 = 2^8*/
  /*result[0] = 0000 0000*/
  /*result[1] = 0000 0000*/
  /*result[2] = 0000 0001*/
  /*result[3] = 0000 0000*/
  static public byte[] intToByteArray(int i){
    byte[] result = new byte[4];
    result[0] = (byte)(i >>> 24);
    result[1] = (byte)(i >>> 16);
    result[2] = (byte)(i >>> 8);
    result[3] = (byte)(i);

    return result;
  }

  /*transfer byte array into integer*/
  /*For example*/
  /*value[0] = 0000 0000*/
  /*value[1] = 0000 0000*/
  /*value[2] = 0000 0001*/
  /*value[3] = 0000 0000*/
  /*result = 256*/
  static public int ByteArrayToint(byte [] value){
  	int result = 0;
    ByteBuffer wrapped = ByteBuffer.wrap(value);
    result = wrapped.getInt();
    return result;
  }

  /*if bitfield is 0010 1001
                   1100 0101
    and pieceIndex = 10

    Output: bitfield is 0010 1001
                        1110 0101*/
  static public void setBitInBitfield(byte[] bitfield, int pieceIndex){
    int row = pieceIndex/8;
    int column = pieceIndex %8 ;
    int position = 7 - column;
    byte flag = (byte)(1 << position);
    bitfield[row] |= flag;
  }

  /*if bitfield is 0010 1001
                   1100 0101
    and pieceIndex = 10

    Output:  false (because bitfield[10] != 1)*/
  static public boolean isSetBitInBitfield(byte[] bitfield, int pieceIndex){
    boolean result;
    int row = pieceIndex/8;
    int column = pieceIndex %8 ;
    int position = 7 - column;
    byte flag = (byte)(1 << position);
    result = ((bitfield[row] & flag) != 0);
    return result;
  }

  /*combine two array together*/
  /*Ex. byte [] a = new byte[]{'a', 'b'};
        byte [] b = new byte[]{'c', 'd'}
    Output: combined = new byte[]{'a', 'b', 'c', 'd'};*/
  static public byte[] combineByteArray(byte[] a, byte[] b){
    byte[] combined = new byte[a.length + b.length];
    System.arraycopy(a, 0, combined, 0         ,a.length);
    System.arraycopy(b, 0, combined, a.length, b.length);
    return combined;
  }

  /*Read byte from (pieceSize*indexOfPiecce) to [(pieceSize*indexOfPiecce) + pieceSize] from file*/
  /*(pieceSize*indexOfPiecce) to [(pieceSize*indexOfPiecce) + pieceSize] == read a piece from file*/
  static synchronized public byte[] readPieceFromFile(String filename, int pieceSize, int indexOfPiece, int numberOfPiece){

    int length;
    byte[] result = new byte[pieceSize];
    try{
      RandomAccessFile rf = new RandomAccessFile(filename, "r");

      /*If it is last piece, change the byte array*/
      if(indexOfPiece == (numberOfPiece-1)){
        int fileSize = (int)rf.length(); /*size of file*/

        if(fileSize%pieceSize == 0){
          length = pieceSize;
        }
        else{
          length = fileSize%pieceSize;
        }
      }
      else{
        length = pieceSize;
      }

      rf.seek(pieceSize * indexOfPiece);
      rf.readFully(result, 0, length);
      rf.close();
    }catch(Exception e){
        System.out.println("read piece from file error");
    }
    
    return result;
  } 


  /*Write data[] into file in particular position (start from pieceSize*indexOfPiece) */
  static synchronized public void writePieceToFile(String filename, int pieceSize, int indexOfPiece, byte[] data, int numberOfPiece, int fileSize){
    try{
      RandomAccessFile rf = new RandomAccessFile(filename, "rw");
      int filesize = (int)rf.length();
      
      int length;

      /*If it is last piece, change the byte array*/
      if(indexOfPiece == (numberOfPiece-1)){
        if(fileSize%pieceSize == 0){
          length = pieceSize;
        }
        else{
          length = fileSize%pieceSize;
        }

      }
      else{
        length = pieceSize;
      }

      rf.seek(pieceSize * indexOfPiece);
      rf.write(data, 0, length);
      rf.close();
    }catch(Exception e){
      System.out.println("write piece to file error");
    }
  }  

  /*return false if do not have complete file*/
  /*return true if has complete file*/
  static synchronized public boolean checkForCompelteFile(byte [] bifield, int numberOfPiece){

    for(int i = 0; i< numberOfPiece; i++){
      if(isSetBitInBitfield(bifield, i) == false){
         return false;
      }
    }
    
    return true;
  }

  static public void threadSleep(int s){
    try{
      Thread.sleep(s);
    }
    catch(Exception e){
      System.out.println(e);
    }
  }

static public void writeToFile(String filename, String context){
      BufferedWriter bw = null;
      FileWriter fw = null;
      DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
      Date dateobj = new Date();

      try {

          String data = context;

          File file = new File(filename);

          // if file doesnt exists, then create it
          if (!file.exists()) {
             file.createNewFile();
          }
 
          // true = append file
          fw = new FileWriter(file.getAbsoluteFile(), true);
          bw = new BufferedWriter(fw);
          bw.write("[" + df.format(dateobj) + "] ");
          bw.write(data);
          bw.write(System.getProperty("line.separator"));

      }catch (IOException e) {

          e.printStackTrace();

      }finally {

          try {

              if (bw != null)
                bw.close();

              if (fw != null)
                fw.close();
  
          } catch (IOException ex) {

              ex.printStackTrace();

          }
      }
  }

  static public boolean equalLists(List<Integer> one, List<Integer> two){     
    if (one == null && two == null){
        return true;
    }

    if((one == null && two != null) 
      || one != null && two == null
      || one.size() != two.size()){
        return false;
    }

    //to avoid messing the order of the lists we will use a copy
    //as noted in comments by A. R. S.
    one = new ArrayList<Integer>(one); 
    two = new ArrayList<Integer>(two);   

    Collections.sort(one);
    Collections.sort(two);      
    return one.equals(two);
  }

  /*For testing*/
  static public void printByteArray(byte[] value){
  	if(value == null){
  		System.out.print("Msg payload: null\n");
  		return;
  	}
    int length = value.length;
    int i=0;
    System.out.print("Msg payload:  ");
    for(i=0; i < length; i++){
      System.out.print((byte)value[i] + " ");
    }
    System.out.println();
  }

  public void sendMessage(DataOutputStream outstream, byte[] msg){
    //synchronized(Utilities.class){
      
        System.out.println("Start to send .........");
        //stream write the message
        Thread sendMsg = new Thread(new send(outstream, msg));
        sendMsg.start();
        System.out.println("Finish to send .........");
   // }
  }

 class send implements Runnable{
     DataOutputStream outstream;
     byte[] msg;

    send(DataOutputStream outstream, byte[] msg){
      this.outstream = outstream;
      this.msg = msg;
    }

    public void run(){
        try{
        outstream.write(msg);
        outstream.flush();
      }
      catch(IOException ioException){
        ioException.printStackTrace();
      }
    }
  }
  

}  