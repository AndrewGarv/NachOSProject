package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator{

    private Lock lock;
    private boolean messageRead = false;
    private LinkedList<Integer> messages;
    private Condition speaker, listener, reader;
    private int speakersReady=0, listenersReady=0; 
    
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        lock = new Lock();
        speaker = new Condition(lock);
        listener = new Condition(lock);
        reader = new Condition(lock);
        //buffer to hold messages due to the fast processing time
        messages = new LinkedList<Integer>();
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
        lock.acquire();
        speakersReady++;
        while(!messages.isEmpty()) 
            speaker.sleep();
        messages.add(word);
        listener.wake();
        while(!messageRead)
            reader.sleep();
        messageRead = false;
        listenersReady--;
        speaker.wake();
        lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        lock.acquire();
        listenersReady++;
        while(messages.isEmpty())
            listener.sleep();
        int message = (int)messages.removeFirst();
        messageRead = true;
        speakersReady--;
        reader.wake();
        lock.release();
	    return message;
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

    public static void selfTest() {
        System.out.println("\n***************From Communicator*********************");
        test1();
        test2();
        test3();
    }

    /**
     * Tests if the 32-bit word is communicated
     */
    private static void test1() {
        System.out.println("***Test 1***");
        Communicator tester = new Communicator();
        KThread t1 = new KThread(new Runnable() {
            public void run() {
                System.out.println("Speaking: 32");
                tester.speak(32);
            }
        });
        t1.fork();

        System.out.println("Listened: " + tester.listen());
    }

    /**
     *  Tests to see if the speak and listen methoids wait for eachother
     */
    private static void test2() {
        System.out.println("***Test 2***");
        Communicator tester = new Communicator();
        
        MustReachAssertion assertionT1 = new MustReachAssertion("t1");
        MustReachAssertion assertionT2 = new MustReachAssertion("t2");
        MustReachAssertion assertionT3 = new MustReachAssertion("t3");

        KThread t1 = new KThread(new Runnable() {
            public void run() {
                tester.speak(1);
                assertionT1.reached();
            }
        });
        
        KThread t2 = new KThread(new Runnable() {
            public void run() {
                tester.listen();
                assertionT2.reached();
            }
        });
        
        KThread t3 = new KThread(new Runnable() {
            public void run() {
                boolean isSpeakerWaiting = false, isListenerWaiting = false;
                while(!assertionT1.hasReached() ||
                    !assertionT2.hasReached()){  
                    if (tester.listenersReady != 0)
                        isListenerWaiting = true;
                    if (tester.speakersReady != 0)
                        isSpeakerWaiting = true;
                    KThread.yield(); 
                }
                System.out.println("Speaker waiting: " + isSpeakerWaiting);
                System.out.println("Listener waiting: " + isListenerWaiting);
                assertionT3.reached();
            }
        });
        t3.fork();
        t1.fork();tester.listen(); 
        t2.fork();tester.speak(1); 

        while(!assertionT3.hasReached())
            KThread.yield(); 
        
    }

    /**
     * Tests multiple threads speaking and listening at the same time.
     * All threads should be paired off with another thread once, there can
     * be multiple speakers or listeners waiting but never multiple of both.
     */
    private static void test3() {
        System.out.println("***Test 3***");

        //Tests with speaking first

        MustReachAssertion assertionT1 = new MustReachAssertion("t1");
        MustReachAssertion assertionT2 = new MustReachAssertion("t2");
        MustReachAssertion assertionT3 = new MustReachAssertion("t3");
        MustReachAssertion assertionT4 = new MustReachAssertion("t4");
        MustReachAssertion assertionT5 = new MustReachAssertion("t5");
        MustReachAssertion assertionT6 = new MustReachAssertion("t6");

        Communicator tester = new Communicator();

        KThread t1 = new KThread(new Runnable() {
            public void run() {
                tester.speak(1);
                assertionT1.reached();
            }
        });
        KThread t2 = new KThread(new Runnable() {
            public void run() {
                tester.speak(2);  
                assertionT2.reached();
            }
        });
        KThread t3 = new KThread(new Runnable() {
            public void run() {
                tester.speak(3);
                assertionT3.reached();
            }
        });
        KThread t4 = new KThread(new Runnable() {
            public void run() {
                tester.speak(4);
                assertionT4.reached();
            }
        });
        KThread t5 = new KThread(new Runnable() {
            public void run() {
                tester.speak(5);
                assertionT5.reached();
            }
        });
        KThread t6 = new KThread(new Runnable() {
            public void run() {
                System.out.println("5 speaking threads called");
                System.out.println(tester.speakersReady + " speaking threads waiting");
                System.out.println("Listener 1 returned: "+ tester.listen());
                System.out.println("Listener 2 returned: "+ tester.listen());
                System.out.println("Listener 3 returned: "+ tester.listen());
                System.out.println("Listener 4 returned: "+ tester.listen());
                System.out.println("Listener 5 returned: "+ tester.listen());
                assertionT6.reached();
            }
        });
        
        t1.fork();
        t2.fork();
        t3.fork();
        t4.fork();
        t5.fork();
        t6.fork();

        while(!assertionT1.hasReached() || 
        !assertionT2.hasReached()|| 
        !assertionT5.hasReached()||
        !assertionT6.hasReached()||
        !assertionT3.hasReached()|| 
        !assertionT4.hasReached())
            KThread.yield(); 
        
        //Tests with listening first   

        MustReachAssertion assertionT7 = new MustReachAssertion("t7");
        MustReachAssertion assertionT8 = new MustReachAssertion("t8");
        MustReachAssertion assertionT9 = new MustReachAssertion("t9");
        MustReachAssertion assertionT10 = new MustReachAssertion("t10");
        MustReachAssertion assertionT11 = new MustReachAssertion("t11");
        MustReachAssertion assertionT12 = new MustReachAssertion("t12");

        Communicator tester2 = new Communicator();

        KThread t7 = new KThread(new Runnable() {
            public void run() {
                System.out.println("Listener 6 returned: "+ tester2.listen());
                assertionT7.reached();
            }
        });
        KThread t8 = new KThread(new Runnable() {
            public void run() {
                System.out.println("Listener 7 returned: "+ tester2.listen());  
                assertionT8.reached();
            }
        });
        KThread t9 = new KThread(new Runnable() {
            public void run() {
                System.out.println("Listener 8 returned: "+ tester2.listen());
                assertionT9.reached();
            }
        });
        KThread t10 = new KThread(new Runnable() {
            public void run() {
                System.out.println("Listener 9 returned: "+ tester2.listen());
                assertionT10.reached();
            }
        });
        KThread t11 = new KThread(new Runnable() {
            public void run() {
                System.out.println("Listener 10 returned: "+ tester2.listen());
                assertionT11.reached();
            }
        });
        KThread t12 = new KThread(new Runnable() {
            public void run() {
                System.out.println("5 listening threads called");
                System.out.println(tester2.listenersReady + " listening threads waiting");
                tester2.speak(6);
                tester2.speak(7);
                tester2.speak(8);
                tester2.speak(9);
                tester2.speak(10);
                assertionT12.reached();
                
            }
        });
        
        t7.fork();
        t8.fork();
        t9.fork();
        t10.fork();
        t11.fork();
        t12.fork();

        while(!assertionT7.hasReached() || 
        !assertionT8.hasReached()|| 
        !assertionT9.hasReached()||
        !assertionT10.hasReached()||
        !assertionT11.hasReached()|| 
        !assertionT12.hasReached())
            KThread.yield(); 
    }




}

