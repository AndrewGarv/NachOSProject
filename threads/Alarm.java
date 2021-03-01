package nachos.threads;

import java.util.PriorityQueue;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

    //This is the queue for holding threads that are waiting.
    private PriorityQueue<AlarmThreadWaiter> waitUntilQueue = new PriorityQueue<AlarmThreadWaiter>();

    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p>
     * <b>Note</b>: Nachos will not function correctly with more than one alarm.
     */
    public Alarm() {
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current thread
     * to yield, forcing a context switch if there is another thread that should be
     * run.
     */
    public void timerInterrupt() {
        long currentTime = Machine.timer().getTime();
        AlarmThreadWaiter nextWaiter = waitUntilQueue.peek();

        while ((nextWaiter != null) && (nextWaiter.getWakeTime() <= currentTime)) {
            nextWaiter.getKThread().ready();
            waitUntilQueue.remove();
            nextWaiter = waitUntilQueue.peek();
        }

        KThread.yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks, waking it up in
     * the timer interrupt handler. The thread must be woken up (placed in the
     * scheduler ready set) during the first timer interrupt where
     *
     * <p>
     * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
     *
     * @param x the minimum number of clock ticks to wait.
     *
     * @see nachos.machine.Timer#getTime()
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
     * The alarm class create a new AlarmThreadWaiter object for each thread that
     * wants to wait.
     */
    public class AlarmThreadWaiter implements Comparable<AlarmThreadWaiter> {

        // this is the KThread thats wants to wait.
        private KThread myThread;
        // This is the time at which the Kthread wants to wake up at.
        private long wakeTime;

        /**
         * Constructor for the AlarmThreadWaiter object.
         * 
         * @param thread The Thread that wants to wait.
         * @param time   The time at which the thread wants to wake up at.
         */
        public AlarmThreadWaiter(KThread thread, long time) {
            myThread = thread;
            wakeTime = time;
        }

        public int compareTo(AlarmThreadWaiter waiter) {
            long waiterTime = waiter.getWakeTime();

            if (this.wakeTime > waiterTime) {
                return 1;
            } else if (this.wakeTime == waiterTime) {
                return 0;
            } else {
                return -1;
            }
        }

        /**
         * Get the value of the wakeTime of the AlarmThreadWaiteer object.
         * 
         * @return the time at which the thread should wake at as a long.
         */
        public long getWakeTime() {
            return wakeTime;
        }

        /**
         * Get the thread of the AlarmThreadWaiteer object.
         * 
         * @return the thread as a KThread.
         */
        public KThread getKThread() {
            return myThread;
        }

    }

    /**
     * Object used in test to wait until the tests are complete to continue.
     */
    public static class MustReachAssertion {
        private boolean hasReached = false;
        private String name;
        public MustReachAssertion(String name) {
            this.name = name;
        }
        public void reached() {
            hasReached = true;
        }
        public boolean hasReached() {
            return hasReached;
        }
        public String getTitle() { return name; }
        
    }

    /**
     * Tests for the Alarm class.
     */
    public static void selfTest() {
        System.out.println("\n*****************From Alarm***************************");

        System.out.println("Start of test1");
        test1();
        System.out.println("End of test1");

        System.out.println("Start of test2");
        test2();
        System.out.println("End of test2");

        System.out.println("Start of test3");
        test3();
        System.out.println("End of test3");

        System.out.println("Start of test4");
        test4();
        System.out.println("End of test4");
    }

    /**
     * Tests the case where a single thread sleeps for 2500 ticks.
     * do we sleep for 2500 ticks or more?
     */
    private static void test1() {
        MustReachAssertion assertionT1 = new MustReachAssertion("t1");

        long startTime = Machine.timer().getTime();
        long sleepTime = 2500;
        ThreadedKernel.alarm.waitUntil(sleepTime);
        long wakeTime = Machine.timer().getTime();
        System.out.println("Wait set to " + sleepTime + " ticks, actual wait: " + (wakeTime - startTime) + " ticks");
        assertionT1.reached();
    }

    /**
     * Tests the case where two threads sleep for 2500 ticks.
     * do both sleep for 2500 ticks or more?
     */
    private static void test2() {
        MustReachAssertion assertionT1 = new MustReachAssertion("t1");
        MustReachAssertion assertionT2 = new MustReachAssertion("t2");

        KThread t1 = new KThread(new Runnable(){
            public void run(){
                long startTime = Machine.timer().getTime();
                long sleepTime = 2500;
                ThreadedKernel.alarm.waitUntil(sleepTime);
                long wakeTime = Machine.timer().getTime();
                System.out.println("Wait set to " + sleepTime + " ticks, actual wait: " + (wakeTime - startTime) + " ticks");
                assertionT1.reached();
            }
        });

        KThread t2 = new KThread(new Runnable(){
            public void run(){
                long startTime = Machine.timer().getTime();
                long sleepTime = 2500;
                ThreadedKernel.alarm.waitUntil(sleepTime);
                long wakeTime = Machine.timer().getTime();
                System.out.println("Wait set to " + sleepTime + " ticks, actual wait: " + (wakeTime - startTime) + " ticks");
                assertionT2.reached();
            }
        });

        t1.fork();
        t2.fork();

        while(!assertionT1.hasReached() || !assertionT2.hasReached()){
            KThread.yield();
        }
    }

    /**
     * Tests the case where multiple threads sleep for diffrent accending time.
     * do all threads sleep for the asigned time or more? 
     */
    private static void test3() {
        MustReachAssertion assertionT1 = new MustReachAssertion("t1");
        MustReachAssertion assertionT2 = new MustReachAssertion("t2");
        MustReachAssertion assertionT3 = new MustReachAssertion("t3");

        KThread t1 = new KThread(new Runnable(){
            public void run(){
                long startTime = Machine.timer().getTime();
                long sleepTime = 1500;
                ThreadedKernel.alarm.waitUntil(sleepTime);
                long wakeTime = Machine.timer().getTime();
                System.out.println("Wait set to " + sleepTime + " ticks, actual wait: " + (wakeTime - startTime) + " ticks");
                assertionT1.reached();
            }
        });

        KThread t2 = new KThread(new Runnable(){
            public void run(){
                long startTime = Machine.timer().getTime();
                long sleepTime = 2500;
                ThreadedKernel.alarm.waitUntil(sleepTime);
                long wakeTime = Machine.timer().getTime();
                System.out.println("Wait set to " + sleepTime + " ticks, actual wait: " + (wakeTime - startTime) + " ticks");
                assertionT2.reached();
            }
        });

        KThread t3 = new KThread(new Runnable(){
            public void run(){
                long startTime = Machine.timer().getTime();
                long sleepTime = 3500;
                ThreadedKernel.alarm.waitUntil(sleepTime);
                long wakeTime = Machine.timer().getTime();
                System.out.println("Wait set to " + sleepTime + " ticks, actual wait: " + (wakeTime - startTime) + " ticks");
                assertionT3.reached();
            }
        });

        t1.fork();
        t2.fork();
        t3.fork();

        while(!assertionT1.hasReached() || !assertionT2.hasReached() || !assertionT3.hasReached()){
            KThread.yield();
        }
    }

    /**
     * Tests the case where multiple threads sleep for diffrent time in mixed order.
     * do all threads sleep for the asigned time or more? 
     */
    private static void test4() {
        MustReachAssertion assertionT1 = new MustReachAssertion("t1");
        MustReachAssertion assertionT2 = new MustReachAssertion("t2");
        MustReachAssertion assertionT3 = new MustReachAssertion("t3");

        KThread t1 = new KThread(new Runnable(){
            public void run(){
                long startTime = Machine.timer().getTime();
                long sleepTime = 3500;
                ThreadedKernel.alarm.waitUntil(sleepTime);
                long wakeTime = Machine.timer().getTime();
                System.out.println("Wait set to " + sleepTime + " ticks, actual wait: " + (wakeTime - startTime) + " ticks");
                assertionT1.reached();
            }
        });

        KThread t2 = new KThread(new Runnable(){
            public void run(){
                long startTime = Machine.timer().getTime();
                long sleepTime = 500;
                ThreadedKernel.alarm.waitUntil(sleepTime);
                long wakeTime = Machine.timer().getTime();
                System.out.println("Wait set to " + sleepTime + " ticks, actual wait: " + (wakeTime - startTime) + " ticks");
                assertionT2.reached();
            }
        });

        KThread t3 = new KThread(new Runnable(){
            public void run(){
                long startTime = Machine.timer().getTime();
                long sleepTime = 1500;
                ThreadedKernel.alarm.waitUntil(sleepTime);
                long wakeTime = Machine.timer().getTime();
                System.out.println("Wait set to " + sleepTime + " ticks, actual wait: " + (wakeTime - startTime) + " ticks");
                assertionT3.reached();
            }
        });

        t1.fork();
        t2.fork();
        t3.fork();

        while(!assertionT1.hasReached() || !assertionT2.hasReached() || !assertionT3.hasReached()){
            KThread.yield();
        }
    }
}