package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import java.util.Queue;

/**
 * Created by Keswani on 2/27/2018.
 */

public class ProcessQueue {

    ArrayList<String> seenMessages = new ArrayList<String>();
    ContentResolver contentResolver;
    int seqNumForStore = 0;
    int activeDevices = 4;
    boolean avdFailed = false;
    String nameAVDFailed;
    String myName;


    ProcessQueue(ContentResolver contentResolver, String myPort) {
        this.contentResolver = contentResolver;
        this.myName = myPort;

    }

    /*
    This method adds the message to buffer for first time ,i.e. when it is first sent
     */
    PriorityQueue<QueueItem> workOnQueueAsClient(PriorityQueue<QueueItem> priorityQueue, QueueItem newQueueItem) {

        if (!checkDuplicate(priorityQueue, newQueueItem)) {
            /* Raman if(avdFailed && newQueueItem.pRec==0){
                newQueueItem.pRec = 1;
                Log.i("ERR","When pRec not updated");
            }*/
            Log.i("xyz", "avdFailed: " + avdFailed);
            Log.i("xyz", "workOnQueueAsClient name avd Fail: " + nameAVDFailed);
            if (avdFailed && newQueueItem.propRec.size() == 0) {
                newQueueItem.propRec.add(nameAVDFailed);
            }
            printArrList(newQueueItem.propRec);
            priorityQueue.add(newQueueItem);
        }
        ProcessQueue.printQ(priorityQueue);
        Log.i("xyz", "workOnQueueAsClient ENDS");
        return priorityQueue;
    }

    /*
    This method updates priority queue as per proposed sequence number received from other emulators
     */
    ContainerPSeq workOnQueue_UpdateProposed(PriorityQueue<QueueItem> priorityQueue, QueueItem newQueueItem, String avd) {
        Log.i("xyz", "workOnQueue_UpdateProposed STARTS");
        ProcessQueue.printQ(priorityQueue);
        ContainerPSeq containerPSeq = new ContainerPSeq();
        boolean found = false;
        Iterator<QueueItem> itemIterator = priorityQueue.iterator();
        while (!found && itemIterator.hasNext()) {
            QueueItem queueItem = itemIterator.next();
            if (queueItem.messageID.equals(newQueueItem.messageID)) {
                found = true;
                //Raman queueItem.pRec++;
                queueItem.propRec.add(avd);
                if (queueItem.seqNum < newQueueItem.seqNum) {
                    queueItem.seqNum = newQueueItem.seqNum;
                }
                priorityQueue.remove(queueItem);
                priorityQueue.add(queueItem);
                printArrList(queueItem.propRec);
                // Raman Log.i("ERR","From ProcessQueue prec: " + queueItem.pRec + " mid= "+ queueItem.messageID);
                Log.i("ERR", "From ProcessQueue prec: " + queueItem.propRec.size() + " mid= " + queueItem.messageID);
                if (queueItem.propRec.size() >=4 && checkIfDeliverable(queueItem.propRec)) {
                    queueItem.isDeliverable = true;
                    containerPSeq.sendAgreement = true;
                    containerPSeq.largestAgreed = queueItem.seqNum;
                    /*queueItem.agreedSeqNum = queueItem.seqNum;*/
                }
            }
        }
        ProcessQueue.printQ(priorityQueue);
        PriorityQueue<QueueItem> priorityQueue1 = deliverMessage(priorityQueue);
        containerPSeq.priorityQueue = priorityQueue1;
        Log.i("xyz", "workOnQueue_UpdateProposed ENDS:: CHECK " + (priorityQueue == null));
        return containerPSeq;
    }

    PriorityQueue<QueueItem> workOnQueueAsServer(PriorityQueue<QueueItem> priorityQueue, QueueItem newQueueItem) {
        Log.i("xyz", "workOnQueueAsServer STARTS");
        if (!checkDuplicate(priorityQueue, newQueueItem)) {
            char nameFailedAvdTemp;
            Log.i("ERR", "workOnQueueAsServer nameAVDFailed: " + nameAVDFailed);
            Log.i("ERR", "calling printArrList from workOnQueueAsServer start");
            printArrList(newQueueItem.propRec);
            if(null !=  nameAVDFailed) {
                nameFailedAvdTemp = getAVDName(nameAVDFailed);
                if(avdFailed && newQueueItem.messageID.charAt(0) == nameFailedAvdTemp
                        && !newQueueItem.propRec.contains(nameAVDFailed)){
                    newQueueItem.propRec.add(nameAVDFailed);
                }
            }
            Log.i("ERR", "calling printArrList from workOnQueueAsServer end");
            printArrList(newQueueItem.propRec);

            priorityQueue.add(newQueueItem);
        }
        ProcessQueue.printQ(priorityQueue);
        return priorityQueue;
    }

    static void printQ(PriorityQueue<QueueItem> priorityQueue) {
        Log.i("xyz", "print start");
        Iterator<QueueItem> itemIterator = priorityQueue.iterator();
        while (itemIterator.hasNext()) {
            QueueItem item = itemIterator.next();
            // Raman  Log.i("xyz", "queue  message id: " + item.messageID + " seq num: " + item.seqNum
            //  + "isDeliverable: " + item.isDeliverable + " prec: " +item.pRec);
            Log.i("xyz", "queue  message id: " + item.messageID + " seq num: " + item.seqNum
                    + "isDeliverable: " + item.isDeliverable + " prec: " + item.propRec.size());
        }
        Log.i("xyz", "print ends");
    }

    PriorityQueue<QueueItem> deliverMessage(PriorityQueue<QueueItem> priorityQueue) {
        Log.i("xyz", "deliverMessage STARTS");
        ProcessQueue.printQ(priorityQueue);
        if (priorityQueue.size() != 0) {
            QueueItem head = priorityQueue.peek();
            PriorityQueue<QueueItem> inCaseOfSameSeq = new PriorityQueue<QueueItem>(100, queueItemComparator);
            if (head.isDeliverable) {
                if (!ProcessQueue.anyLowerSeqPresent(priorityQueue)) {
                    Iterator<QueueItem> itemIterator = priorityQueue.iterator();
                    while (itemIterator.hasNext()) {
                        QueueItem item = itemIterator.next();
                        if (item.seqNum == head.seqNum) {
                            inCaseOfSameSeq.add(item);
                        }
                    }
                    if (inCaseOfSameSeq.size() == 1) {
                        Log.i("xyz", "Head has unique and smallest sequence; to deliver is: " + head.messageID + " seq num is: " + head.seqNum);
                        deliverToStorage(head.message, head.messageID);
                       /* Looper.prepare();
                        GroupMessengerActivity groupMessengerActivity = new GroupMessengerActivity();
                        groupMessengerActivity.printOnScreen(head.message);*/
                        priorityQueue.remove(head);
                        deliverMessage(priorityQueue);
                    } else {
                        QueueItem toDelete = inCaseOfSameSeq.peek();
                        if (toDelete.isDeliverable) {
                            deliverToStorage(toDelete.message, toDelete.messageID);
                        /*GroupMessengerActivity groupMessengerActivity = new GroupMessengerActivity();
                        groupMessengerActivity.printOnScreen(toDelete.message);*/
                            priorityQueue.remove(toDelete);
                            Log.i("xyz", "Case 2 to deliver is: " + toDelete.messageID + " seq num is: " + toDelete.seqNum);
                            deliverMessage(priorityQueue);
                        }
                    }
                    Log.i("xyz", "deliverMessage ITR END:: CHECK " + (priorityQueue == null));
                    //deliverMessage(priorityQueue);
                }
            }
        }
        Log.i("xyz", "deliverMessage ENDS FINAL:: CHECK " + (priorityQueue == null));
        return priorityQueue;
    }

    PriorityQueue<QueueItem> updateQueueAsServer(PriorityQueue<QueueItem> priorityQueue, QueueItem agreedItem) {
        Log.i("xyz", "updateQueueAsServer STARTS:: CHECK ");
        Iterator<QueueItem> itemIterator = priorityQueue.iterator();
        while (itemIterator.hasNext()) {
            QueueItem item = itemIterator.next();
            if (!item.isDeliverable && item.messageID.equals(agreedItem.messageID)) {
                item.isDeliverable = true;
                if (agreedItem.seqNum != -1) {
                    item.seqNum = agreedItem.seqNum;
                }
                priorityQueue.remove(item);
                priorityQueue.add(item);
                break;
            }
        }
        Log.i("xyz", "case 3");
        deliverMessage(priorityQueue);
        ProcessQueue.printQ(priorityQueue);
        Log.i("xyz", "updateQueueAsServer ENDS");

        return priorityQueue;
    }

    static QueueItem getItemToBeDeleted(PriorityQueue<QueueItem> priorityQueue, QueueItem headOfSameSeqQ) {
        Iterator<QueueItem> itemIterator = priorityQueue.iterator();
        while (itemIterator.hasNext()) {
            QueueItem queueItem = itemIterator.next();
            if (queueItem.messageID == headOfSameSeqQ.messageID) {
                priorityQueue.remove(queueItem);
                return queueItem;
            }
        }
        return null;
    }

    static boolean anyLowerSeqPresent(PriorityQueue<QueueItem> priorityQueue) {
        boolean anyLowerSeqPresent = false;
        QueueItem head = priorityQueue.peek();
        Iterator<QueueItem> itemIterator = priorityQueue.iterator();
        while (itemIterator.hasNext()) {
            QueueItem item = itemIterator.next();
            if (item.seqNum < head.seqNum) {
                anyLowerSeqPresent = true;
                break;
            }
        }
        return anyLowerSeqPresent;
    }

    public static Comparator<QueueItem> queueItemComparator = new Comparator<QueueItem>() {
        @Override
        public int compare(QueueItem lhs, QueueItem rhs) {
            return lhs.messageID.compareTo(rhs.messageID);
        }
    };

    void deliverToStorage(String message, String mid) {
        Uri uri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
        ContentValues contentValues = new ContentValues();
        contentValues.put("key", seqNumForStore++);
        contentValues.put("value", message);
        try {
            contentResolver.insert(uri, contentValues);
            Log.i("xyz", "Inserted: " + mid + " MS" + Integer.toString(seqNumForStore - 1) + " String: " + message);
        } catch (Exception e) {
            seqNumForStore--;
            Log.e("xyz", "exception during writing in storage");
        }
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    PriorityQueue<QueueItem> handleMessagesFailedAVD(PriorityQueue<QueueItem> priorityQueue, String mid, String avd) {
        Log.i("xyz", "handleMessagesFailedAVD start");
        avdFailed = true;
        nameAVDFailed = avd;
        //activeDevices--;
        char avdFailedName = getAVDName(avd);
        Log.i("ERR","handleMessagesFailedAVD nameAVDFULL: "+ avd);
        Log.i("xyz", "handleMessagesFailedAVD avdFailed: " + avdFailedName);
        Iterator<QueueItem> itemIterator = priorityQueue.iterator();
        while (itemIterator.hasNext()) {
            QueueItem queueItem = itemIterator.next();
           /* if(queueItem.messageID != mid){
                queueItem.pRec++;
            }*/

            if (!queueItem.propRec.contains(avd)) {
                queueItem.propRec.add(avd);
            }
            Log.i("xyz", "queueItem messageID: " + queueItem.messageID.charAt(0));
            if(avdFailedName == queueItem.messageID.charAt(0)){
                queueItem.isDeliverable = true;
                //queueItem.propRec.add(avd);
               // queueItem.pRec++;
                priorityQueue.remove(queueItem);
                priorityQueue.add(queueItem);
            }
        }
        Log.i("xyz", "handleMessagesFailedAVD end");
        deliverMessage(priorityQueue);
        return priorityQueue;
    }

  /*  ContainerPSeq handleItemsOfFaliedAVD(PriorityQueue<QueueItem> priorityQueue, int failedID){
        char avdName = getAVDName(failedID);
        Iterator<QueueItem> itemIterator = priorityQueue.iterator();
        while (itemIterator.hasNext()){
            QueueItem queueItem = itemIterator.next();
        }
        return null;
    }*/

    public char getAVDName(String failedID) {
        char avdName = 'Z';
        switch (Integer.parseInt(failedID)) {
            case 11108:
                avdName = 'A';
                break;
            case 11112:
                avdName = 'B';
                break;
            case 11116:
                avdName = 'C';
                break;
            case 11120:
                avdName = 'D';
                break;
            case 11124:
                avdName = 'E';
                break;
        }
        return avdName;
    }

    boolean checkDuplicate(PriorityQueue<QueueItem> priorityQueue, QueueItem queueItem) {
        Iterator<QueueItem> itemIterator = priorityQueue.iterator();
        while (itemIterator.hasNext()) {
            QueueItem item = itemIterator.next();
            if (item.messageID.equals(queueItem.messageID)) {
                return true;
            }
        }

        if (seenMessages.contains(queueItem.messageID)) {
            return true;
        }
        seenMessages.add(queueItem.messageID);

        return false;
    }

    boolean checkIfDeliverable(ArrayList<String> arrayList) {
        if ((arrayList.contains("11108") || myName.equals("11108"))
                && (arrayList.contains("11112") || myName.equals("11112"))
                && (arrayList.contains("11116") || myName.equals("11116"))
                && (arrayList.contains("11120") || myName.equals("11120"))
                && (arrayList.contains("11124") || myName.equals("11124"))) {
            return true;
        } else {
            return  false;
        }
    }

    void printArrList(ArrayList<String> arrList){
        Log.i("ERR","printArrList Start");
        Iterator<String> iterator = arrList.iterator();
        while (iterator.hasNext()){
            Log.i("ERR"," arrList: " + iterator.next());
        }
        Log.i("ERR","printArrList END");
    }
}
