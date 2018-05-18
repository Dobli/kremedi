/*
 
 */

// digital pins used:
int signalAPin = 2;
int signalBPin = 4;

// control variables
unsigned long timer;
int reset;

//variables used by intterupt
volatile long rotValue = 0;
volatile int oldA = -1;
volatile int oldB = -1;
volatile int signalStateA = -1;
volatile int signalStateB = -1;
volatile int newValue;
volatile int oldValue;

volatile int test;
/**
 * function to be used by interrupt to determine rotation
 */
void checkRot () 
 {
     // read the input pin:
	 signalStateA = digitalRead(signalAPin);
	 signalStateB = digitalRead(signalBPin);
	 
	  //CW : 00 (0), 10 (2), 11 (3), 01 (1) -->
	  //CCW: 00 (0), 10 (2), 11 (3), 01 (1) <--
	  oldValue = oldA*2+oldB;
	  newValue = signalStateA*2+signalStateB;
	  
	  oldB=signalStateB;
	  oldA=signalStateA;
	 
	  if(newValue==0){
		  if(oldValue==1){
			  rotValue--;
		  }else if (oldValue==2){
			  rotValue++;
		  }
	  } else if(newValue==2){
		  if(oldValue==0){
			  rotValue--;
		  }else if (oldValue==3){
			  rotValue++;
		  }
	  } else if(newValue==3){
		  if(oldValue==2){
			  rotValue--;
		  }else if (oldValue==1){
			  rotValue++;
		  }
	  } else if(newValue==1){
		  if(oldValue==3){
			  rotValue--;
		  }else if (oldValue==0){
			  rotValue++;
		  }
	  }

	
	  
 } 

// the setup routine runs once when you press reset:
void setup() {
  // initialize serial communication at 9600 bits per second:
  Serial.begin(115200);
  // make the pushbutton's pin an input:
  pinMode(signalAPin, INPUT);
  pinMode(signalBPin, INPUT);
	
  attachInterrupt(digitalPinToInterrupt(signalAPin), checkRot, CHANGE);
  attachInterrupt(digitalPinToInterrupt(signalBPin), checkRot, CHANGE);
  timer = millis();
}

// the loop routine runs over and over again forever:
void loop() {
	
  //checkRot();
	
  // old print
  static long oldRot = -1;
	
  // read the input pin:
  int signalStateA = digitalRead(signalAPin);
  int signalStateB = digitalRead(signalBPin);
  
	
  // check every 32ms if new value should be displayed
  if (millis() - timer >= 32UL){
	  timer = millis();
	  
	  if(reset){
			rotValue = 0;
			reset = 0;
  			Serial.println(rotValue);
	  }
		
	  // print if changes happen
	  if (rotValue!=oldRot){
			Serial.println(rotValue);
		    oldRot=rotValue;
	  }
  }

  oldB=signalStateB;
  oldA=signalStateA;
}

void serialEvent() {
  while (Serial.available()) {
    // get the new byte:
    char inChar = (char)Serial.read();
	  
    if (inChar == 'R') {
      reset = 1;
    }
  }
}




