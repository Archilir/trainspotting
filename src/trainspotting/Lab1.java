package trainspotting;
import TSim.*;

import java.util.concurrent.Semaphore;



public class Lab1 {
	Semaphore[] semaphore = new Semaphore[6];
	public static final int SWITCH_LEFT = 0x01;
	public static final int SWITCH_RIGHT = 0x02;
	
	/*
	 *  The critical sections are as we discussed:
	 *	1-2. Northern and Southern stations: the semaphore's to control the switch as to prevent the trains from entering a lane that's already occupied by a train.
	 *	3-4. Northern and Southern single lanes that go from the station switches, 'till the middle fast lane switch: these sections are critical, because trains can't enter and pass them simultaneously without causing a collision. One train would have to wait at either side of this 'path' (either waiting on the station lane next to the switch, or in the middle lane before the crossing train passes.
	 *	5. Middle "fast" lane: if one's occupied, then the second train needs to be redirected to the other lane. Interestingly enough, if one train passes into the 'one lane' section after crossing the switch, then the previous behavior (3-4) will be the one governing the first train's stop.
	 *	6. Cross road between northern train station lanes: one waits, while the other one passes.
	 */			
			
  public Lab1(Integer speed1, Integer speed2){
    TSimInterface tsi = TSimInterface.getInstance();
    for (int i = 0; i < 6; i++) {
    	semaphore[i] = new Semaphore(1);
    }
    try {
    Train train1 = new Train(1, speed1, tsi);
	Train train2 = new Train(2, speed2, tsi);
	train1.start();
	train2.start();
	tsi.setSwitch(17, 7, SWITCH_RIGHT);
	tsi.setSwitch(4, 9, SWITCH_RIGHT);
    } catch (CommandException e) {
    	e.printStackTrace();
    	System.exit(0);
    }
  }
  class Train extends Thread {
	  private int maxSpeed = 15;
	  private boolean traveling = true;
	  private int trainId;
	  private int speed;
	  private TSimInterface tsi;
	  public Train(int id, int speed, TSimInterface tsi) throws CommandException{
		  this.trainId = id;
		  this.tsi = tsi;
		  maxSpeed = speed;
		  setSpeed(speed);
	  }
	  
	  public void setSpeed(int speed) throws CommandException {
		  this.speed = speed;
		  tsi.setSpeed(trainId, speed);
		
	  }
	  
	  private int getStatus() {
		  if (speed > 0) { 
			  return  1; } // If train's moving forward
		  else if (speed < 0) { 
			  return -1; } // If train's moving backwards
		  else {
			  return 0;    // If train's stopped
		  }
	  }
	  
	  //rewrite the lock to: when locked, when the acquire happens the train should stop or check another available resource
	
	  private void changeSwitch(int x, int y, int direction) throws CommandException{
		  this.tsi.setSwitch(x, y, direction);
	  }
	  
	  
	  public void run() {
		  while (true) {
			  try {
				SensorEvent sensor = tsi.getSensor(trainId);
				switch(Integer.toString(sensor.getXpos()) + "," +
				       Integer.toString(sensor.getYpos())) {
				
				// Train stops, waits, and reverses movement. Falls through for all conditions.
				case "16,3": case "16,5": case "16,11": case "16,13":
					if (sensor.getStatus() == 1 && traveling == true) {
						int status = getStatus() * -1;
						setSpeed(0);
						System.out.println("STATION STOPS");
						Thread.sleep(1000 + (20 * speed));
						traveling = false;
						setSpeed(maxSpeed * status);
					} else if (sensor.getStatus() == 2 && traveling == false) {
						traveling = true;
					}
					break;
				// Cross road cases
				case "6,5":case "10,7":case "8,5":case "10,8":
					if (sensor.getStatus() == 1 && traveling == true){
						switch (Integer.toString(sensor.getXpos()) + "," +
							       Integer.toString(sensor.getYpos())) {
						case "6,5":case "10,7":case "8,5":case "10,8":
							int tmpSpeed = speed;
							setSpeed(0);
							System.out.println("CROSS STOPS");
							semaphore[5].acquire();
							setSpeed(tmpSpeed);
							semaphore[1].release();
							break;
						default:
							semaphore[5].release();
							traveling = true;
						}
					}else {
						semaphore[5].release();
						
					}
				//the next 2 group of cases are the three roads meet: SSL& NSL
					//SSL
				case "3,13":case "5,11": // SSL & OL2
					if (sensor.getStatus() == 1) {
						int tmpspeed = this.speed;
						setSpeed(0);
						System.out.println("SSL STOPS");
						semaphore[3].acquire();
						setSpeed(tmpspeed);
						semaphore[1].release();
						changeSwitch(4, 9, semaphore[4].tryAcquire()?
							SWITCH_LEFT:SWITCH_RIGHT);//if the middle line is not acquire,switch it left to go straight line  
						changeSwitch(3, 11,(sensor.getXpos()==5&&sensor.getYpos()==11)?
							SWITCH_LEFT:SWITCH_RIGHT);//GO straight IN THE BOTTOM LINE
					}else {
						semaphore[3].release();
					}
		
					break;
				//NSL, almost same as SSL
				case "15,7": case "15,8": 
					if (sensor.getStatus() == 1){
						int tmpspeed = this.speed;
						setSpeed(0);
						System.out.println("NSL STOPS");
						semaphore[2].acquire();
						setSpeed(tmpspeed);
						semaphore[0].release();
						changeSwitch(17, 7, semaphore[4].tryAcquire()?
								SWITCH_RIGHT:SWITCH_LEFT);
						changeSwitch(15, 9, (sensor.getXpos()==14&&sensor.getYpos()==9)?
								SWITCH_LEFT:SWITCH_RIGHT);
					}else {
						semaphore[2].release();
					}
					break;
					
				case "5,9":case "5,10": // OL2 & Middle,LEFT,WEST
					if (sensor.getStatus() == 1) {
						int tmpspeed = this.speed;
						setSpeed(0);
						System.out.println("OL2 WEST MIDDLE LEFT STOP");
						semaphore[3].acquire();
						setSpeed(tmpspeed);
						semaphore[4].release();
						changeSwitch(3, 11, semaphore[1].tryAcquire()?
								SWITCH_LEFT:SWITCH_RIGHT);
						changeSwitch(4, 9, (sensor.getXpos()==5 &&sensor.getYpos()==9)?
								SWITCH_LEFT:SWITCH_RIGHT);
						
					}else {
						semaphore[3].release();
					}
					break;
				
				case "14,9": case "14,10":// OL1 & Middle,EAST, RIGHT
					if (sensor.getStatus()==1) {
						int tmpspeed = this.speed;
						setSpeed(0);
						System.out.println("ol1 middle east right stop");
						semaphore[2].acquire();
						setSpeed(tmpspeed);
						semaphore[4].release();
						changeSwitch(17, 7, semaphore[0].tryAcquire()?
								SWITCH_LEFT:SWITCH_RIGHT);
						changeSwitch(15, 9, (sensor.getXpos()== 14&&sensor.getYpos()==9?
								SWITCH_LEFT:SWITCH_RIGHT));
					}else {
						semaphore[2].release();
					}
					break;
				}
							
			} catch (CommandException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			   
		  }
	  }  
  }
}
