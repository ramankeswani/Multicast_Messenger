package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {

    static final String[] REMOTE_PORTS = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    static Socket[] remoteSockets = new Socket[4];
    static String[] remotePorts;
    int[] messageCount = new int[5];
    int myPriority = 0;
    int myAVDIndex, messageID = 0, largestProposed = 0, largestAgreed = 0;
    String myPort, messageIDPrefix;
    Object lockforPSeqUpdate = new Object();
    Object lockForOutStream = new Object();
    private PriorityQueue<QueueItem> priQueue = new PriorityQueue<QueueItem>(100, queueItemComparator);
    ProcessQueue processQueue;
    String nameOfDeviceFailed;

    // Part 2
    int failedDevice = -1;
    boolean[] alreadyCreated = new boolean[4];
    int agreedCounter;
    // Part 2

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        agreedCounter = 0;
        nameOfDeviceFailed = "";
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        processQueue = new ProcessQueue(getContentResolver(), myPort);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        final EditText editText = (EditText) findViewById(R.id.editText1);

        Button sendButton = (Button) findViewById(R.id.button4);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = editText.getText().toString() + "\n";
                editText.setText("");
                TextView textView = (TextView) findViewById(R.id.textView1);
                textView.append("SENT: " + message);
                Log.i("xyz", "USER entered: " + message);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, myPort);
                Log.i("xyz", "client created");
            }
        });

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        //  new CreateSockets().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,myPort);

        try {
            Log.i("xyz", "Creating server socket");
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e("xyz", "Exception during server socket creation");
        }

        remotePorts = new String[4];
        int counter = 0;
        for (int i = 0; i < REMOTE_PORTS.length; i++) {
            if (!myPort.equals(REMOTE_PORTS[i])) {
                remotePorts[counter++] = REMOTE_PORTS[i];
            }
        }

        switch (Integer.parseInt(myPort)) {
            case 11108:
                myAVDIndex = 0;
                messageIDPrefix = "A";
                break;
            case 11112:
                myAVDIndex = 1;
                messageIDPrefix = "B";
                break;
            case 11116:
                myAVDIndex = 2;
                messageIDPrefix = "C";
                break;
            case 11120:
                myAVDIndex = 3;
                messageIDPrefix = "D";
                break;
            case 11124:
                myAVDIndex = 4;
                messageIDPrefix = "E";
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ReceiveTask extends AsyncTask<String, String, Void> {

        @Override
        protected Void doInBackground(String... strings) {

            Log.i("xyz", "connection established jus fine");
            String messageReceived = strings[1];
            String processIndexRec = strings[0];
            if (!messageReceived.isEmpty()) {
                String[] strArr = {processIndexRec + messageReceived};
                publishProgress(strArr);
            }
            return null;
        }

        protected void onProgressUpdate(String... messageReceived) {
            Log.i("xyz", "Server onProgressUpdate Begins");
            TextView textView = (TextView) findViewById(R.id.textView1);
            String message = messageReceived[0];
            textView.append("RECEIVED: " + message + "\n");
            Log.i("xyz", "Server onProgressUpdate ends");
        }
    }

    private class MessageProcess extends Thread {
        Socket messageSocket;
        int counterAwaitingAgreement;
        int threadNum;
        MessageProcess(Socket socket, int threadNum) {
            this.messageSocket = socket;
            counterAwaitingAgreement = 1;
            this.threadNum = threadNum;
        }

        @Override
        public void run() {
            boolean remoteHappy = true;
            int remoteAVDIndex = 0;
            int agreedSeqNum = 0;
            String mesIDRec = "";
           // ArrayList<String> waitingAgreee = new ArrayList<String>();

            Object lockForCounter = new Object();
            while (remoteHappy) {
                try {
                    Log.i("xyz", "Message Process Starts");
                    int seqToPropose = 0;
                    String messageReceived = "";
                    ObjectInputStream objectInputStream = new ObjectInputStream(messageSocket.getInputStream());
                    Log.i("xyz", "reading message  type");
                    String messageType = objectInputStream.readUTF();
                    Log.i("xyz", "messagetype is: " + messageType);
                    if (messageType.equals(GroupMessengerConstants.messageType.NEW.toString())) {
                       // messageSocket.setSoTimeout(7000);



                        remoteAVDIndex = objectInputStream.readInt();
                        mesIDRec = objectInputStream.readUTF();
                        Log.i("xyz", "IF Thread ID: " + threadNum +" MID R: "+mesIDRec);

                        messageReceived = objectInputStream.readUTF();
                        synchronized (lockforPSeqUpdate) {
                            seqToPropose = Math.max(largestAgreed, largestProposed) + 1;
                            largestProposed = seqToPropose;
                            QueueItem queueItem = new QueueItem(mesIDRec, seqToPropose, messageReceived);
                            Log.i("ERR", "from server remote failed: " + failedDevice);
                            Log.i("ERR", "from server remoteAVDIndex: " + remoteAVDIndex);
                          /*  if(failedDevice == remoteAVDIndex){
                                queueItem.isDeliverable = true;
                            }*/
                            if(!nameOfDeviceFailed.isEmpty() &&
                                    mesIDRec.charAt(0) == processQueue.getAVDName(nameOfDeviceFailed)){
                            }
                            priQueue = processQueue.workOnQueueAsServer(priQueue, queueItem);
                            synchronized (lockForCounter){
                                //counterAwaitingAgreement++;
                                //  waitingAgreee.add(mesIDRec);
                                agreedCounter++;
                            }
                            Log.i("ERR","if added waitingAgreee " + mesIDRec);
                            Log.i("ERR","if agreedCounter " + agreedCounter);
                            Log.i("xyz", "Sending Proposal: " + seqToPropose + " mes: " + mesIDRec);
                        }

                        Log.i("xyz", "received data, now sending");
                        //Testing only
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(messageSocket.getOutputStream());
                        objectOutputStream.writeUTF(mesIDRec);
                        objectOutputStream.writeInt(seqToPropose);
                        objectOutputStream.writeInt(myAVDIndex);
                        objectOutputStream.flush();
                        Log.i("xyz", "sent");
                    } else {




                        Log.i("xyz", "Reading agreement");
                        remoteAVDIndex = objectInputStream.readInt();
                        mesIDRec = objectInputStream.readUTF();
                        Log.i("xyz", "ELSE Thread ID: " + threadNum +" MID R: "+mesIDRec);
                        agreedSeqNum = objectInputStream.readInt();
                        largestAgreed = Math.max(largestAgreed, agreedSeqNum);
                        synchronized (lockforPSeqUpdate) {
                            Log.i("xyz", "Rec Agree: " + agreedSeqNum + " mes: " + mesIDRec);
                            QueueItem queueItem = new QueueItem(mesIDRec, agreedSeqNum);
                            priQueue = processQueue.updateQueueAsServer(priQueue, queueItem);
                        }
                        synchronized (lockForCounter){
                            //counterAwaitingAgreement--;
                            //waitingAgreee.remove(mesIDRec);
                            agreedCounter--;
                        }
                        Log.i("ERR","if remove waitingAgreee " + mesIDRec);
                        Log.i("ERR"," else agreedCounter: " + agreedCounter);
                        if(agreedCounter == 0) {
                         //   messageSocket.setSoTimeout(0);
                        } else {
                         //   messageSocket.setSoTimeout(7000);
                        }
                        Log.i("xyz", "finished reading agreement");
                    }

                    new ReceiveTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, Integer.toString(remoteAVDIndex), messageReceived);
                } catch (SocketTimeoutException e){
                    Log.i("ERR", "SocketTimeoutException SERVER mid: " + mesIDRec);
                    Log.i("ERR", "agreedCounter: " + agreedCounter );
                    Log.i("ERR", "waitingAgreee: avd: " + remoteAVDIndex );
                    if(agreedCounter != 0) {
                        synchronized (lockforPSeqUpdate) {
                            Log.i("xyz", "Rec Agree: " + agreedSeqNum + " mes: " + mesIDRec);
                            QueueItem queueItem = new QueueItem(mesIDRec, -1);
                            priQueue = processQueue.updateQueueAsServer(priQueue, queueItem);
                            //remoteHappy = false;
                        }
                    }
                    remoteHappy = false;
                }
                catch (IOException e) {
                    Log.i("ERR", "agreedCounter: " + agreedCounter );
                    Log.e("ERR", "not happy exception FOR avd: "+ remoteAVDIndex);
                    /*if(waitingAgreee.size() != 0){
                        QueueItem queueItem = new QueueItem(mesIDRec, -1);
                        priQueue = processQueue.updateQueueAsServer(priQueue, queueItem);
                    }*/
                    if(agreedCounter != 0) {
                        synchronized (lockforPSeqUpdate) {
                            Log.i("xyz", "Rec Agree: " + agreedSeqNum + " mes: " + mesIDRec);
                            QueueItem queueItem = new QueueItem(mesIDRec, -1);
                            priQueue = processQueue.updateQueueAsServer(priQueue, queueItem);
                            //remoteHappy = false;


                        }
                    }


                    remoteHappy = false;
                }
            }
        }
    }

    private class ServerTask extends AsyncTask<ServerSocket, Void, Void> {

        @Override
        protected Void doInBackground(ServerSocket... serverSockets) {
            Log.i("xyz", "ServerTask doInBackground starts");
            ServerSocket serverSocket = serverSockets[0];
            int counter = 0;
            while (counter < 4) {
                try {
                    Socket socket = serverSocket.accept();
                    counter++;
                    MessageProcess messageProcess = new MessageProcess(socket, counter);
                    messageProcess.start();
                } catch (IOException e) {
                    Log.e("xyz", "exception during socket accept");
                }
            }
            Log.i("xyz", "ServerTask doInBackground ends");
            return null;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            Log.i("xyz", "ClientTask doInBackground starts");
            boolean inserted = false;
            for (int i = 0; i < 4; i++) {
               // if (i != failedDevice) {
                    try {
                        if (null == remoteSockets || null == remoteSockets[i]) {
                            if (!alreadyCreated[i]) {
                                alreadyCreated[i] = true;
                                remoteSockets[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(remotePorts[i]));
                            }
                        } else if (remoteSockets[i].isClosed()) {
                            Log.i("ERR", "device failed is: " + remoteSockets[i] + "i= " + i);
                            synchronized (lockforPSeqUpdate){
                                failedDevice = i;
                            }
                            continue;
                        }

                        if (!inserted) {
                            inserted = true;
                            synchronized (lockforPSeqUpdate) {
                                ++messageID;
                                largestProposed = Math.max(largestAgreed, largestProposed) + 1;
                                QueueItem queueItem = new QueueItem(messageIDPrefix + Integer.toString(messageID),
                                        largestProposed, strings[0]);
                                if(!nameOfDeviceFailed.isEmpty()){
                                    // Raman queueItem.pRec++;
                                    //queueItem.propRec.add(remotePorts[i]);
                                    queueItem.propRec.add(nameOfDeviceFailed);
                                    // Raman Log.i("ERR"," queueItem.pRec = " +  queueItem.pRec + " mes: " + messageIDPrefix + Integer.toString(messageID) + " message: " + strings[0]);
                                  //  Log.i("ERR"," queueItem.pRec = " +  queueItem.propRec.size() + " mes: " + messageIDPrefix + Integer.toString(messageID) + " message: " + strings[0]);

                                }
                                priQueue = processQueue.workOnQueueAsClient(priQueue, queueItem);
                                Log.i("xyz", "1st Entered, SEQ: " + largestProposed + " mes: " + messageIDPrefix + Integer.toString(messageID) + " message: " + strings[0]);
                            }
                        }
                        String mid = "";
                        synchronized (lockForOutStream) {
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(remoteSockets[i].getOutputStream());
                            objectOutputStream.writeUTF(GroupMessengerConstants.messageType.NEW.toString());
                            objectOutputStream.writeInt(myAVDIndex);
                            mid = messageIDPrefix + Integer.toString(messageID);
                            objectOutputStream.writeUTF(mid);
                            objectOutputStream.writeUTF(strings[0]);
                            objectOutputStream.flush();
                        }
                        Log.i("xyz", "Client sent: " + strings[0]);
                        GetProposedSeq getProposedSeq = new GetProposedSeq(remoteSockets[i], mid, remotePorts[i]);
                        getProposedSeq.start();
                    } catch (UnknownHostException e) {
                        Log.e("xyz", "Client UnknownHostException");
                    } catch (IOException e) {
                        Log.e("xyz", "Exception during getOutputStream");
                    } catch (Exception e) {
                        Log.e("xyz", "Exception in client" + e.toString());
                    }
                //}
                Log.i("xyz", "ClientTask doInBackground ends");
            }
            return null;
        }

        private class GetProposedSeq extends Thread {

            Socket getPSeqSocket;
            String mid;
            String avd;

            GetProposedSeq(Socket socket, String mid, String avd) {
                this.getPSeqSocket = socket;
                this.mid = mid;
                this.avd = avd;
            }

            @Override
            public void run() {
                try {
                    Log.i("xyz", "GetProposedSeq Starts");
                    String mid1 = "";
                    int propMsgSeq = 0;
                    //getPSeqSocket.setSoTimeout(1000);
                    try {
                        ObjectInputStream objectInputStream = new ObjectInputStream(getPSeqSocket.getInputStream());
                        mid1 = objectInputStream.readUTF();
                        propMsgSeq = objectInputStream.readInt();
                        int remoteAVDIndex = objectInputStream.readInt();
                    } catch (SocketTimeoutException e) {
                        mid1 = mid;
                        propMsgSeq = 0;
                        getPSeqSocket.close();
                        Log.e("ERR", "Socket time out during proposal await, mid= " + mid1);
                        nameOfDeviceFailed = avd;
                        Log.i("ERR","nameOfDeviceFailed: "+ nameOfDeviceFailed);
                        synchronized (lockforPSeqUpdate){
                            priQueue = processQueue.handleMessagesFailedAVD(priQueue,mid1, avd);
                        }
                    } catch (IOException e) {
                        mid1 = mid;
                        propMsgSeq = 0;
                        getPSeqSocket.close();
                        Log.e("ERR", "IO Excp out during proposal await, mid= " + mid1);
                        nameOfDeviceFailed = avd;
                        Log.i("ERR","nameOfDeviceFailed: "+ nameOfDeviceFailed);
                        synchronized (lockforPSeqUpdate){
                            priQueue = processQueue.handleMessagesFailedAVD(priQueue,mid1, avd);
                        }
                    } catch (Exception e){
                        Log.e("ERR","last excep");
                    }

                    int agreedSeqNum = 0;
                    boolean sendAgreement = false;
                    synchronized (lockforPSeqUpdate) {
                        Log.i("xyz", "received proposal : " +
                                Integer.toString(propMsgSeq) + " Message ID " + mid +
                                " from: " + avd);
                        //Log.i("xyz", "Rec Prop: " + propMsgSeq + " mes: " + mid1);
                        QueueItem queueItem = new QueueItem(mid1, propMsgSeq);
                        //queueItem.propRec.add(avd);
                        ContainerPSeq containerPSeq = processQueue.workOnQueue_UpdateProposed(priQueue, queueItem, avd);
                        priQueue = containerPSeq.priorityQueue;
                        agreedSeqNum = containerPSeq.largestAgreed;
                        sendAgreement = containerPSeq.sendAgreement;
                        Log.i("ERR","sendAgreement: " + sendAgreement+ " for mid " + mid1);
                        largestAgreed = Math.max(largestAgreed, agreedSeqNum);
                    }
                    if (sendAgreement) {
                        Log.i("xyz", "sending agreement");
                   /* try {
                        Thread.sleep(6000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/
                        synchronized (lockForOutStream) {
                            Log.i("xyz", "Send Agree: " + agreedSeqNum + " mes: " + messageID);
                            for (int i = 0; i < 4; i++) {
                                if(!remoteSockets[i].isClosed()) {
                                    ObjectOutputStream objectOutputStream =
                                            new ObjectOutputStream(remoteSockets[i].getOutputStream());
                                    objectOutputStream.writeUTF(GroupMessengerConstants.messageType.AGREEMENT.toString());
                                    objectOutputStream.writeInt(myAVDIndex);
                                    objectOutputStream.writeUTF(mid1);
                                    objectOutputStream.writeInt(agreedSeqNum);
                                    objectOutputStream.flush();
                                }
                            }
                        }
                        Log.i("xyz", "Agreement Sent");
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    Log.i("xyz", "finally");
                }
            }
        }
    }

    public static Comparator<QueueItem> queueItemComparator = new Comparator<QueueItem>() {
        @Override
        public int compare(QueueItem lhs, QueueItem rhs) {
            return lhs.seqNum - rhs.seqNum;
        }
    };

}
