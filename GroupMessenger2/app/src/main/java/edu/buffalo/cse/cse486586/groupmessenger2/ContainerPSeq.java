package edu.buffalo.cse.cse486586.groupmessenger2;

import java.util.PriorityQueue;

/**
 * Created by Keswani on 2/27/2018.
 */

public class ContainerPSeq {
    PriorityQueue<QueueItem> priorityQueue;
    int largestAgreed;
    boolean sendAgreement;

    ContainerPSeq(){
        sendAgreement = false;
    }

   /* ContainerPSeq(PriorityQueue<QueueItem> priorityQueue, int largestObserved){
        this.priorityQueue = priorityQueue;
        this.largestAgreed = largestObserved;
    }*/
}
