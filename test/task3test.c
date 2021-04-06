#include <syscall.h>
#include <time.h>
#include <stdlib.h>
#include <stdio.h>

void nex() {
	srand(time(NULL));
	int r = rand() % 99;
	char randFile[10];
	sprintf(randFile, "%i", r);
	
	exec(randFile, 0, "");
	halt();
}

void narg() {
	char filename[20];
	printf("Enter the name of a program that DOES exist.\n");
	scanf("%s", filename);
	exec(filename, 1, NULL);
	halt();
}

void exerr() {
	printf("I'm not actually sure how to cause an execution error. Sorry.\n");
	halt();
}

void ncp() {
	int status;
	srand(time(NULL));
	int r = rand() % 256;
	printf("I'm hoping that the rand method will return the PID of a non-child process.");
	join(r, status);
	halt();
}

void exprog() {
	char filename[20];
	printf("Enter the name of a program that DOES exist.\n");
	scanf("%s", filename);
	exec(filename, 0, "");
	halt();
}

void jcp() {
	int pid;
	int status;
	printf("Enter the pid of a program that IS a parent.");
	scanf("%i", &pid);
	join(pid, status);
	halt();
}

void exprog() {
	int pid;
	int status;
	printf("Enter the pid of a program that exists.");
	scanf("%i", &pid);
	exit(pid, status);
	halt();
}

int main(int argc, char** argv) {
	int selMethod;
	printf("1.  Attempt to open non-existent file\n");
	printf("2.  Attempt to open file with null argument\n");
	printf("3.  Execution error\n");
	printf("4.  Attempt to join a non-child process\n");
	printf("5.  Execute process\n");
	printf("6.  Join child process\n");
	printf("7.  Exit process\n");
	scanf("%i", &selMethod);

	switch(selMethod) {
    	case 1:
      		nex();
    	case 2:
      		narg();
    	case 3:
      		exerr();
    	case 4:
      		ncp();
    	case 5:
      		exprog();
    	case 6:
      		jcp();
    	case 7:
      		exprog();
	}
}

	
