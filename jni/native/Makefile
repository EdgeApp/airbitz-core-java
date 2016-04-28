
CFLAGS   += -D_GNU_SOURCE -DDEBUG -g -Wall -fPIC -std=c99
CXXFLAGS += -D_GNU_SOURCE -DDEBUG -g -Wall -fPIC -std=c++11
INCLUDES=-I.. \
		 -I/Library/Java/JavaVirtualMachines/jdk1.7.0_75.jdk/Contents/Home/include/ \
		 -I/Library/Java/JavaVirtualMachines/jdk1.7.0_75.jdk/Contents/Home/include/darwin
LFLAGS=-L.
LIBS=-labc


native: ../ABC_android_util.c ../ABC_wrap.c 
	$(CC) $(INCLUDES) -c -o ABC_wrap.o ../ABC_wrap.c 
	$(CC) $(INCLUDES) -c -o ABC_android_util.o ../ABC_android_util.c 
	$(CC) $(LFLAGS) $(LIBS) --shared -o libairbitz.so ABC_android_util.o ABC_wrap.o 

clean:
	rm -rf libairbitz.a ABC_wrap.o ABC_android_util.o
