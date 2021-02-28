package nachos.threads;

import java.util.PriorityQueue;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

    PriorityQueue<AlarmThreadWaiter> waitUntilQueue = new PriorityQueue<AlarmThreadWaiter>();

    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	    Machine.timer().setInterruptHandler(new Runnable() {
		    public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        long currentTime = Machine.timer().getTime();
        AlarmThreadWaiter nextWaiter = waitUntilQueue.peek();

        while((nextWaiter != null) && (nextWaiter.getWakeTime() <= currentTime)){
            nextWaiter.getKThread().ready();
            waitUntilQueue.remove();
            nextWaiter = waitUntilQueue.peek();
        }

	    KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        boolean intStatus = Machine.interrupt().disable();

        long wakeTime = Machine.timer().getTime() + x;
        AlarmThreadWaiter wrapper = new AlarmThreadWaiter(KThread.currentThread(), wakeTime);
        waitUntilQueue.add(wrapper);
        KThread.sleep();
        Machine.interrupt().restore(intStatus);
    }

    /**
     * The alarm class create a new AlarmThreadWaiter object for each thread that wants to wait.
     */
    public class AlarmThreadWaiter implements Comparable<AlarmThreadWaiter>{

        //this is the KThread thats wants to wait.
        private KThread myThread;
        //This is the time at which the Kthread wants to wake up at.
        private long wakeTime;

        /**
         * Constructor for the AlarmThreadWaiter object.
         * @param thread The Thread that wants to wait.
         * @param time The time at which the thread wants to wake up at.
         */
        public AlarmThreadWaiter(KThread thread, long time){
            myThread = thread;
            wakeTime = time;
        }

        public int compareTo(AlarmThreadWaiter waiter){
            long waiterTime = waiter.getWakeTime();

            if (this.wakeTime > waiterTime){
                return 1;
            }
            else if (this.wakeTime == waiterTime){
                return 0;
            }
            else {
                return -1;
            }
        }

        /**
         * Get the value of the wakeTime of the AlarmThreadWaiteer object.
         * @return the time at which the thread should wake at as a long.
         */
        public long getWakeTime(){
            return wakeTime;
        }

        /**
         * Get the thread of the AlarmThreadWaiteer object.
         * @return the thread as a KThread.
         */
        public KThread getKThread(){
            return myThread;
        }

    }

    /**
     * Tests for the Alarm class.
     */
    public static void selfTest(){
        
    }
}