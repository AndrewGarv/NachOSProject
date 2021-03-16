package nachos.threads;

import nachos.machine.*;
import java.util.concurrent.*; 
import java.util.concurrent.locks.ReentrantLock;
public class ReactWater{
	private int Hydro;
	private int Oxy;
	Semaphore water = new Semaphore(2);//Semaphores don't seem to work in NachOS so this is a remnant of old code
    

    /** 
     *   Constructor of ReactWater
     **/
    public ReactWater() {
		Hydro = 0;
		Oxy = 0;

    } // end of ReactWater()

    /** 
     *   When H element comes, if there already exist another H element 
     *   and an O element, then call the method of Makewater(). Or let 
     *   H element wait in line. 
     * @throws InterruptedException 
     **/ 
    public void hReady() throws InterruptedException {
		synchronized(this)
		{
			Hydro++;
			System.out.println("First Hydrogen made, initiating wait");
			Thread.sleep(5000);
			System.out.println("Making second Hydrogen atom");
			Hydro++;
			Makewater();
		}
	

		
		
    
    } // end of hReady()
 
    /** 
     *   When O element comes, if there already exist another two H
     *   elements, then call the method of Makewater(). Or let O element
     *   wait in line. 
     **/ 
    public void oReady() throws InterruptedException {
		synchronized(this)
		{
			Oxy++;
			System.out.println("Oxygen made");
		}
		

    } // end of oReady()
    
    /** 
     *   Print out the message of "water was made!".
     **/
    public void Makewater() { 
		System.out.println("Water has been made");

    } // end of Makewater()
	
	public static void selfTest() 
	{ 
	System.out.println("---------------ReactWater Test-----------");
		ReactWater WaterTest = new ReactWater();
		KThread Hyd = new KThread(new Runnable()
		{
			public void run() 
			{
				System.out.println("First test");
				try
				{
				WaterTest.hReady();	
				}catch(InterruptedException e){}
				
			}
		}).setName(" ");
		
		KThread Ox = new KThread(new Runnable() 
		{
			public void run()
			{
				try
				{
				WaterTest.oReady();	
				}catch(InterruptedException e){}
				
			}
		}).setName("   ");
		Hyd.fork();
		Ox.fork();
		}
		
		
	}
 // end of class ReactWater





