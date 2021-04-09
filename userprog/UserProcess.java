package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.Hashtable;



/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
	
	protected OpenFile[] fd;
  	protected int pid;
  	protected UserProcess parent;
  	protected Semaphore procMutex = new Semaphore(1);
  	protected Hashtable<Integer, UserProcess> children = new Hashtable<Integer, UserProcess>();
  	protected Integer exitStatus;
  	protected Lock statusLock;
	  protected Condition joinCondition;
	
    /**
     * Allocate a new process.
     */
    public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		LSLock = new Lock();
		for (int i=0; i<numPhysPages; i++){
			pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
		}
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	threader = new UThread(this);
	threader.setName(name);
	threader.fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		
		//check if any value are invalid, if true return 0 as no data was transferred.
		if((vaddr < 0) || (data == null) || (offset < 0) || (length < 0) || (offset+length > data.length)){
			return 0;
		}
		byte[] memory = Machine.processor().getMemory();
		int vpn = Processor.pageFromAddress(vaddr);
		int off = Processor.offsetFromAddress(vaddr);
		int ppn = -1;
		int amount = 0;
		int copyAmount = 0;

		while(length > 0){
			//check if vpn is valid
			if(vpn > pageTable.length || vpn < 0){
				break;
			}
			pageTable[vpn].used = true;
			ppn = pageTable[vpn].ppn;
			//System.out.println(ppn);
			int paddr = Processor.makeAddress(ppn, off);
			copyAmount = Math.min(pageSize - off, length);
			System.arraycopy(memory, paddr, data, offset + amount, copyAmount);
			off = 0;
			amount += copyAmount;
			length -= copyAmount;
			vpn++;
		}

		return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		
		//check if any value are invalid, if true return 0 as no data was transferred.
		if((vaddr < 0) || (data == null) || (offset < 0) || (length < 0) || (offset+length > data.length)){
			return 0;
		}
		byte[] memory = Machine.processor().getMemory();
		int vpn = Processor.pageFromAddress(vaddr);
		int off = Processor.offsetFromAddress(vaddr);
		int ppn = -1;
		int amount = 0;
		int copyAmount = 0;
		
		while(length > 0){
			//check if vpn is valid
			if(vpn > pageTable.length || vpn < 0){
				break;
			}
			ppn = pageTable[vpn].ppn;
			//check if vpn is read only
			if(pageTable[vpn].readOnly){
				break;
			}
			pageTable[vpn].dirty = true;
			pageTable[vpn].used = true;
			int paddr = Processor.makeAddress(ppn, off);
			copyAmount = Math.min(pageSize - off, length);
			System.arraycopy(data, offset + amount, memory, paddr, copyAmount);
			off = 0;
			amount += copyAmount;
			length -= copyAmount;
			vpn++;
		}

		return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
		LSLock.acquire();
		if(numPages > UserKernel.freePageList.size()){
			coff.close();
			Lib.debug(dbgProcess, "insufficient physical memory");
			LSLock.release();
			return false;
		}
		//initialize page table
		//pageTable = new TranslationEntry[numPages];

		int vpn = 0;
		int ppn = -1;
		for(int s = 0; s < coff.getNumSections(); s++){
			CoffSection section = coff.getSection(s);
			Lib.debug(dbgProcess, "initializing " + section.getName() + " section(" + section.getLength() + " pages).");
			for(int i = 0; i < section.getLength(); i++){
				vpn = section.getFirstVPN() + i;
				ppn = UserKernel.getNextAvailablePage();
				//System.out.println(i +"->"+ ppn);
				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);
				if(ppn < 0)
					break;
				section.loadPage(i, ppn);
			}
		}

		for(int i = 0; i < stackPages; i++){
			vpn++;
			ppn = UserKernel.getNextAvailablePage();
			pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, false, false);
		}

		LSLock.release();
		
		return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
		if(pageTable == null){
			return;
		}

		for(int i = 0; i < pageTable.length; i++){
			UserKernel.returnAvailablePage(pageTable[i].ppn);
		}
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

	
	
	
    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {

	Machine.halt();
	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }
	    /**
     * This method exists to set the first two spaces of the fileList to the input and output stream
     */
	public void setter()
	{
		fileList[0] = UserKernel.console.openForReading();
		fileList[1] = UserKernel.console.openForWriting();
	}
	
	public int creat(String name)
	{
		boolean a = false;
		
			FileSystem systa = Machine.stubFileSystem();
			OpenFile creator = systa.open(name, true);
			OpenFile truecreator = new OpenFile(systa, name);
			int value = fileDescript;
			fileList[value] = truecreator;
			System.out.print("File created in position: "); System.out.print(value);
			System.out.println(" ");
			fileDescript++;
			return value;
	}
	
	public int open(String name)
	{
		int value = 0;
		FileSystem systa = Machine.stubFileSystem();
		OpenFile creator = systa.open(name, true);
		OpenFile temp = new OpenFile(systa, name);
		for(int a = 2; a < 20; a++)
		{
			if(fileList[a] != null)
			{
				if(fileList[a].getName() == name)
				{
					System.out.println("File found");
					value = a;
				}
			}
		}
		
		if(value == 0)
		{
			System.out.println("File does not exist");
			return -1;
		}
		else
		{
			systa.open(temp.getName(), true);
			OpenFile replace = new OpenFile(systa, name);
			fileList[value] = replace;
			System.out.println(name + " has been opened");
			return value;
		}
	}
	
	public int read(int fileDescriptor, int count)
	{
		byte[] bcount = new byte[count];
		int value = 0;
		if(fileList[fileDescriptor] == null)
		{
			return -1;
		}
		else
		{
			OpenFile readFile = fileList[fileDescriptor];
			value = readFile.read(bcount, 0, count);
			fileList[fileDescriptor + value] = readFile;
			return value;
		}
	}
	
	public int write(int fileDescriptor, int count, int buffer)
	{
		byte[] bcount = new byte[buffer];
		int value = 0;
		if(fileList[fileDescriptor] == null)
		{
			System.out.println("No");
			return -1;
		}
		else
		{
			OpenFile writeFile = fileList[fileDescriptor];
			int guess = writeVirtualMemory(buffer, bcount);
			value = fileList[fileDescriptor].write(bcount, buffer, count);
			if(value < count)
			{
				System.out.println("Error");
				System.out.println(value);
				return -1;
			}
			fileList[fileDescriptor + value] = writeFile;
			return value;
		}
	}
	
	public int close(int fileDescriptor)
	{
		if(fileList[fileDescriptor] == null)
		{
			return -1;
		}
		else
		{
			fileList[fileDescriptor].close();
			fileList[fileDescriptor] = null;
			fileDescript--;
			return 0;
		}
	}
	
	public int unlink(String name)
	{
		FileSystem systa = Machine.stubFileSystem();
		OpenFile temp = systa.open(name, true);
		int value = 0;
		for(int a = 2; a < 20; a++)
		{
			if(fileList[a] != null)
			{
				if(fileList[a].getName() == name)
				{
					System.out.println("File needs to be closed before it can be deleted");
					value = 1;
				}
			}
		}
		if(value == 1)
		{
			return -1;
		}
		else 
		{
			systa.remove(name);
			System.out.println("File deleted");
			return 0;
		}
	}
	    /**
     * Issues getting docker and cross compile for C programs so testing was done in Java
     */
	public static void selfTest2()
	{
		System.out.println("*************Task 1 test cases***********");
		UserProcess tester = new UserProcess();
		tester.setter();
		System.out.println("Creating File");
		tester.creat("Test File");
		System.out.println("----------------Test 2--------------");
		System.out.println("Opening nonexistent file called No File");
		tester.open("No File");
		System.out.println("----------------Test 3--------------");
		System.out.println("Opening file Test File");
		tester.open("Test File");
		System.out.println("----------------Test 4--------------");
		System.out.println("Writing 15 bytes to test file");
		System.out.println(tester.write(2,15,2));
		System.out.println("----------------Test 5--------------");
		System.out.println("Performing read");
		System.out.println(tester.read(2,5));
		System.out.println("----------------Test 6--------------");
		System.out.println("Performing Unlink. Cannot be done as the file has not been closed yet");
		tester.unlink("Test File");
		System.out.println("----------------Test 7--------------");
		System.out.println("Performing close and then unlink");
		tester.close(2);
		tester.unlink("Test File");
		
		
	}

	private int handleExec(int file, int argc, int argv) {
		String filename = null;
		filename = readVirtualMemoryString(file, 256);
		if(filename == null) {
			System.err.println("UNREADABLE_FILENAME_EXCEPTION");
			return -1;
		}
		String[] args = new String[argc];
		byte[] buffer = new byte[4];
		for(int i = 0; i < argc; i++) {
			args[i] = readVirtualMemoryString(Lib.bytesToInt(buffer, 0), 256);
			if(args[i] == null) {
				System.err.println("UNREADABLE_ARGUMENT_EXCEPTION");
				return -1;
			}
		}
		UserProcess child = newUserProcess();
		this.children.put(child.pid, child);
		child.parent = this;
		boolean insProg = child.execute(filename, args);
		if(insProg) {
			return child.pid;
		}
		return -1;
	}
	
	private int handleJoin(int procid, int status) {
		if(!this.children.containsKey(procid)) {
			System.err.println("NON_CHILD_EXCEPTION");
			return -1;
		}
		UserProcess child = this.children.get(procid);
		child.statusLock.acquire();
		Integer childStatus = child.exitStatus;
		if(childStatus == null) {
			this.statusLock.acquire();
			child.statusLock.release();
			this.joinCondition.sleep();
			this.statusLock.release();
			child.statusLock.acquire();
			childStatus = child.exitStatus;
		}
		child.statusLock.release();
		this.children.remove(procid);
		byte[] statuses = Lib.bytesFromInt(childStatus.intValue());
		writeVirtualMemory(status, statuses);
		if(childStatus.intValue() == 0) {
			return 1;
		} else {
			return 0;
		}
	}
	
	private int handleExit(int status) {
		unloadSections();

		for(int i = 2; i < this.fd.length; i++) {
			if(this.fd[i] != null) {
				this.fd[i].close();
			}
		}

		this.statusLock.acquire();
		this.exitStatus = status;
		this.statusLock.release();
		this.procMutex.P();

		if(this.parent != null) {
			this.parent.statusLock.acquire();
			this.parent.joinCondition.wakeAll();
			this.parent.statusLock.release();
		}

		this.procMutex.V();

		for(UserProcess childproc : this.children.values()) {
			childproc.procMutex.P();
			childproc.parent = null;
			childproc.procMutex.V();
		}
		return status;
	}
	
    private static final int
    syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;
	static OpenFile[] fileList = new OpenFile[20];
	static int[] omg = new int[20];
	static int fileDescript = 2;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();
	case syscallExec:
	    return handleExec(a0, a1, a2);
	case syscallJoin:
	    return handleJoin(a0, a1);
	case syscallExit:
	    return handleExit(a0);
	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }




	private static void task2Test() {

		String[] dummyArgs = {"0"};

		System.out.println("************ Task 2 Test **************");
		System.out.println("Number of pages in all of memory: " + Machine.processor().getNumPhysPages());

		UserProcess dummy1 = UserProcess.newUserProcess();
		System.out.println("Dummy1's numPages before load is called:" + dummy1.numPages);
		dummy1.load("sort.coff", dummyArgs);
		System.out.println("Dummy1's numPages after load is called:" + dummy1.numPages);
		dummy1.loadSections();
		for(int i = 0; i < dummy1.numPages; i++){
			System.out.println("\t-> VPN: " + i +" ppn: "+dummy1.pageTable[i].ppn);
		}

		//Reading
		byte[] memory = Machine.processor().getMemory();
        byte[] data = new byte[pageSize];
		int vaddr = 1;
		dummy1.readVirtualMemory(vaddr, data, 0, 1024);
		System.out.println("dummy1 ReadingVM: " + " " + data[121] + " " + data[122] + " " + data[123]);

		//Writing
		int slots = 4;
		vaddr = 1024 * slots - 1;
        data = new byte[pageSize];
        data[0] = 6;
		data[1] = 9;
		int numWritten = dummy1.writeVirtualMemory(vaddr, data, 0, 2);
        int paddr = Processor.makeAddress(dummy1.pageTable[slots].ppn, 0);
		System.out.println("dummy1: Number of bytes written: " + numWritten);
        System.out.println("dummy1: Writing in VM: " + memory[paddr-1] + " " + memory[paddr] + " " + memory[paddr+1] + " " + memory[paddr+2]);

		//Unloading
		int temp = UserKernel.freePageList.size();
		System.out.println("dummy1: The list size before return ppn: " + temp);
		dummy1.unloadSections();
		System.out.println("dummy1: Checking to see if all ppn are returned: " + (UserKernel.freePageList.size() - temp));
        System.out.println("dummy1: Verifying the last ppn added into the freePageList:");
		for (int i = 0; i < dummy1.numPages; i++) {
            int tempPPN = UserKernel.freePageList.get(temp + i);
            System.out.println("\t-> ppn added at: " + (temp + i) + ", position: " + tempPPN);
            UserKernel.freePageList.add(temp + i, tempPPN);
        }

		//dummy2
		UserProcess dummy2 = UserProcess.newUserProcess();
		System.out.println("dummy2: numPages variable before load is called: " + dummy2.numPages);
		dummy2.load("matmult.coff", dummyArgs);
		System.out.println("dummy2: numPages variable after load is called: " + dummy2.numPages);
		dummy2.loadSections();
		System.out.println("dummy2: Checking number of ppn:" + dummy2.numPages);
		for(int i = 0; i < dummy2.numPages; i++){
			System.out.println("\t-> VPN: " + i + ", ppn: " + dummy2.pageTable[i].ppn);
		}
		int temp2 = UserKernel.freePageList.size();
		System.out.println("dummy2: Number of ppns: " + temp2);
		dummy2.unloadSections();
		System.out.println("dummy2: Checking if ppns where returned: " + (UserKernel.freePageList.size() - temp2));
		System.out.println("dummy2: Checking last ppn added into freePageList: ");
		for(int i = 0; i < dummy2.numPages; i++){
			
			System.out.println("\t-> ppn added at: " + (i + temp2) + ", Position: " + UserKernel.freePageList.get(temp2 + i));
			UserKernel.freePageList.add(i + temp2, UserKernel.freePageList.get(temp2 + i));
		}

		System.out.println("************ End of Task 2 Test **************");
	}

	private static void task3Test() {
		System.out.println("Howdy, I'm UserProcess!");
		System.out.println("The C test program is called task3test.c. Here are the test cases:");
		System.out.println("1. Attempt to open non-existent file");
		System.out.println("2. Attempt to open a file with null argument");
		System.out.println("3. Execution Error");
		System.out.println("4. Attempt to join a non-child process");
		System.out.println("5. Execute process");
		System.out.println("6. Join child process");
		System.out.println("7. Exit process");
	}

	public void selfTest() {
		System.out.println("************ Phase 2 **************");
		task2Test();
		//task3Test();
	}



	


    /** The program being run by this process. */
    protected Coff coff;

	private Lock LSLock;
	private UThread threader = null;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
}
