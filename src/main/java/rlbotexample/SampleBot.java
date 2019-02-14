package rlbotexample;

import com.sun.jna.platform.unix.Resource;
import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.*;
import rlbot.manager.BotLoopRenderer;
import rlbot.render.Renderer;
import rlbotexample.boost.BoostManager;
import rlbotexample.boost.BoostPad;
import rlbotexample.dropshot.DropshotTile;
import rlbotexample.dropshot.DropshotTileManager;
import rlbotexample.dropshot.DropshotTileState;
import rlbotexample.goals.Goal;
import rlbotexample.input.BallData;
import rlbotexample.input.CarData;
import rlbotexample.input.DataPacket;
import rlbotexample.output.ControlsOutput;
import rlbotexample.vector.Vector2;
import rlbotexample.vector.Vector3;

import java.awt.*;
import java.awt.Color;
import java.io.IOException;

public class SampleBot implements Bot {
    Plan plan;
    private final int playerIndex;
    private static final float BALL_RADIUS = 92.75F;
    public SampleBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    /**
     * This is where we keep the actual bot logic. This function shows how to chase the ball.
     * Modify it to make your bot smarter!
     */
    private ControlsOutput processInput(DataPacket input) {

        Vector2 ballPosition = input.ball.position.flatten();

        CarData myCar = input.car;
        Vector2 carPosition = myCar.position.flatten();
        Vector2 carDirection = myCar.orientation.noseVector.flatten();

        // Subtract the two positions to get a vector pointing from the car to the ball.
        Vector2 carToBall = ballPosition.minus(carPosition);

        // How far does the car need to rotate before it's pointing exactly at the ball?
        double steerCorrectionRadians = carDirection.correctionAngle(carToBall);

        boolean goLeft = steerCorrectionRadians > 0;
        boolean goRight = steerCorrectionRadians > 0;
        // This is optional!
        drawDebugLines(input, myCar, goLeft);
        Renderer renderer = BotLoopRenderer.forBotLoop(this);
        Vector3 nose = new Vector3(myCar.orientation.noseVector.x, myCar.orientation.noseVector.y, 0);
        Vector3 ball = new Vector3(input.ball.position.x, input.ball.position.y, 0);




        try {
            if (RLBotDll.getFlatbufferPacket().players(input.car.team).isDemolished()){
                RLBotDll.sendQuickChat(input.car.team, false, QuickChatSelection.Apologies_Cursing);


            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        // This is also optional!

        while(true) {
            try{
            BallPrediction ballPrediction = RLBotDll.getBallPrediction();
            Vector3 ballLine = new Vector3(ballPrediction.slices(ballPrediction.slicesLength() / 2).physics().location());
            renderer.drawLine3d(Color.CYAN, input.ball.position, ballLine); } catch (IOException e){}



            if (plan != null) {
                ControlsOutput planOutput = plan.getOutput(input, this);
                if (planOutput == null) {
                    plan = null;
                } else {
                    return planOutput;
                }
            }

            boolean isKickoff = input.ball.velocity.flatten().magnitude() < 1
                    && input.ball.position.x == 0
                    && input.ball.position.y == 0;
            if (isKickoff) {
                renderer.drawString2d("Kickoff!", Color.white, new Point(10, 200), 2, 2);
                if (input.car.position.magnitude() < 1200) {
                    plan = new Plan()
                            .withStep(new TimedAction(0.1, new ControlsOutput().withJump()))
                            .withStep(new TimedAction(0.05, new ControlsOutput()))
                            .withStep(new TimedAction(0.05, new ControlsOutput().withJump().withPitch(-1)));
                }

                return Steering.steerTowardPosition(input.car, new Vector3())
                        .withBoost().withThrottle(1);
            }


            try {
                BallPrediction ballPrediction = RLBotDll.getBallPrediction();
                Vector3 ballPath = new Vector3(ballPrediction.slices(ballPrediction.slicesLength() / 10).physics().location());
                Vector2 ballPath2 = new Vector2(ballPrediction.slices(ballPrediction.slicesLength() / 10).physics().location().x(), ballPrediction.slices(ballPrediction.slicesLength() / 10).physics().location().y());
                double ballToMyGoal = input.ball.position.flatten().distance(Goal.getDefending(input.team).getCenter());
                double carDistToBallPath = input.car.position.flatten().distance(ballPath2);

                boolean defend = Goal.getDefending(input.team).getCenter().distance(ballPath2) < 3000
                                &&  myCar.orientation.noseVector.angle(ballPath) > 1.6
                                &&  ballToMyGoal< carDistToBallPath
                                ;


                if (defend){

                    RLBotDll.sendQuickChat(input.car.team, false, QuickChatSelection.Information_Defending);
                    renderer.drawString3d("Defending", Color.WHITE, myCar.position, 2, 2);
                    Vector3 goalPos = new Vector3(Goal.getDefending(input.team).getCenter().x, Goal.getDefending(input.team).getCenter().y, 0);

                    if (insideOwnGoal(input) && input.car.orientation.noseVector.angle(ballPath) > 1.6){
                        RLBotDll.sendQuickChat(input.car.team, false, QuickChatSelection.Information_Defending);
                        return Steering.steerTowardPosition(input.car, ballPath).withSlide().withThrottle(1);
                    }
                    else {
                        return Steering.steerTowardPosition(input.car, goalPos);
                    }

                }

            }
            catch (IOException e){

            }
            try {
                BallPrediction ballPrediction = RLBotDll.getBallPrediction();

                boolean airShoot = input.ball.position.z > BALL_RADIUS*3
                        && input.ball.position.z < BALL_RADIUS * 4
                        && input.ball.position.flatten().distance(myCar.position.flatten())< 400
                        && myCar.orientation.noseVector.angle(new Vector3(ballPrediction.slices(5).physics().location())) < 1;

                if (airShoot){
                    renderer.drawString3d("airShoot!", Color.WHITE, myCar.position, 2, 2);
                    RLBotDll.sendQuickChat(input.team, false, QuickChatSelection.Compliments_NiceShot);

                    plan = new Plan()
                            .withStep(new TimedAction(0.3, new ControlsOutput().withJump().withPitch(1)))
                            .withStep(new TimedAction(0.07, new ControlsOutput().withBoost()))
                            .withStep(new TimedAction(0.3, new ControlsOutput().withJump().withPitch(-1)));


                }

            } catch (IOException e) {
                e.printStackTrace();
            }


            try {
            BallPrediction ballPrediction = RLBotDll.getBallPrediction();
            boolean getBigBoost = myCar.boost < 10 && nearestBigBoost(input) == true
                    &&  myCar.orientation.noseVector != new Vector3(ballPrediction.slices(ballPrediction.slicesLength() / 10).physics().location())
                   ;

            if (getBigBoost) {
                try {
                    BoostManager.loadFieldInfo(RLBotDll.getFieldInfo());
                    BoostManager.loadGameTickPacket(RLBotDll.getFlatbufferPacket());
                    BoostManager.getFullBoosts();

                    for (int index = 0; index < 5; index++) {
                        if (myCar.position.flatten().distance(BoostManager.getFullBoosts().get(index).getLocation().flatten()) < 1500) {
                            renderer.drawString3d("Going for BigBoost " +index, Color.WHITE, myCar.position, 2, 2);
                            return Steering.steerTowardPosition(input.car, BoostManager.getFullBoosts().get(index).getLocation());

                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            }
            catch (IOException e){

            }
            try {
                BallPrediction ballPrediction = RLBotDll.getBallPrediction();
                boolean getTinyBoost = myCar.boost < 10 && input.car.position.flatten().distance(input.ball.position.flatten()) > 700
                        && myCar.orientation.noseVector != new Vector3(ballPrediction.slices(ballPrediction.slicesLength() / 10).physics().location());

                if (getTinyBoost) {
                    try {
                        BoostManager.loadFieldInfo(RLBotDll.getFieldInfo());
                        BoostManager.loadGameTickPacket(RLBotDll.getFlatbufferPacket());
                        BoostManager.getSmallBoosts();

                        for (int index = 0; index < 27; index++) {
                            if (myCar.position.flatten().distance(BoostManager.getSmallBoosts().get(index).getLocation().flatten()) < 500 && BoostManager.getSmallBoosts().get(index).isActive()) {
                                renderer.drawString3d("Going for BoostPad " + index, Color.WHITE, myCar.position, 2, 2);
                                return Steering.steerTowardPosition(input.car, BoostManager.getSmallBoosts().get(index).getLocation());

                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                }
            }
            catch (IOException e){

            }
            Vector3 ballPosition2 = input.ball.position;
            boolean canPopBall = ballPosition2.z > BALL_RADIUS * 1.5 &&
                    ballPosition2.z < BALL_RADIUS * 3 &&
                    ballPosition2.flatten().distance(input.car.position.flatten()) < 50;


            if (canPopBall) {
                renderer.drawString3d("Can pop!", Color.WHITE, myCar.position, 2, 2);
                plan = new Plan()
                        .withStep(new TimedAction(0.2, new ControlsOutput().withJump()))
                        .withStep(new TimedAction(0.05, new ControlsOutput()))
                        .withStep(new TimedAction(0.05, new ControlsOutput().withJump().withPitch(1)));
                return plan.getOutput(input, this);
            }

            boolean canFlickBall = (myCar.hasWheelContact && ballPosition2.z > BALL_RADIUS * 1.5
                    && ballPosition2.z < BALL_RADIUS * 3
                    && ballPosition2.flatten().distance(input.car.position.flatten()) < 300
                    && input.car.velocity.flatten().x > input.ball.velocity.flatten().x);
            if (canFlickBall) {
                renderer.drawString3d("Can Flick!", Color.WHITE, myCar.position, 2, 2);
                plan = new Plan()
                        .withStep(new TimedAction(0.1, new ControlsOutput().withJump()))
                        .withStep(new TimedAction(0.05, new ControlsOutput()))
                        .withStep(new TimedAction(0.05, new ControlsOutput().withJump().withPitch(-1)));
                return plan.getOutput(input, this);
            }

            try {
                double ballDistToMyGoal = input.ball.position.flatten().distance(Goal.getDefending(input.team).getCenter());
                double carDistToMyGoal = input.car.position.flatten().distance(Goal.getDefending(input.team).getCenter());

                BallPrediction ballPrediction = RLBotDll.getBallPrediction();
                boolean canShootBall = myCar.orientation.noseVector.angle(new Vector3(ballPrediction.slices(ballPrediction.slicesLength() / 10).physics().location())) < 1
                        && ballDistToMyGoal < carDistToMyGoal
                        ;

                if (canShootBall) {
                    renderer.drawString3d("Can Shoot!", Color.WHITE, myCar.position, 2, 2);
                    if (input.car.isSupersonic && input.car.hasWheelContact) {
                        if (ballPosition2.y > BALL_RADIUS *2) {

                            plan = new Plan()
                                    .withStep(new TimedAction(0.04, new ControlsOutput().withJump().withPitch(1)))
                                    .withStep(new TimedAction(0.04, new ControlsOutput().withBoost().withSteer(goLeft ? -1 : 1).withPitch(-1)));
                            return plan.getOutput(input, this);
                        }
                        else {
                            RLBotDll.sendQuickChat(input.team, false, QuickChatSelection.Custom_Toxic_404NoSkill);
                            plan = new Plan()
                                    .withStep(new TimedAction(0.1, new ControlsOutput().withJump().withPitch(1).withBoost()))
                                    .withStep(new TimedAction(0.04, new ControlsOutput().withBoost().withJump().withPitch(-1)));
                            return plan.getOutput(input, this);
                        }
                    } else {
                        plan = new Plan()
                                .withStep(new TimedAction(0.2, new ControlsOutput().withJump()))
                                .withStep(new TimedAction(0.02, new ControlsOutput()))
                                .withStep(new TimedAction(0.04, new ControlsOutput().withJump().withPitch(-1)));
                        return plan.getOutput(input, this);
                    }
                }
            }
            catch (IOException e){
                e.printStackTrace();
            }


            boolean toHigh = input.ball.position.z > 700 && ballPosition2.flatten().distance(input.car.position.flatten()) < 300;

            if (toHigh) {
                renderer.drawString3d("Ball to high!", Color.WHITE, myCar.position, 2, 2);
                plan = new Plan()
                        .withStep(new TimedAction(1, new ControlsOutput().withSlide().withThrottle(1).withSteer(goLeft ? -1 : 1)));
            }

            else {

                try {

                    BallPrediction ballPrediction = RLBotDll.getBallPrediction();
                    renderer.drawLine3d(Color.CYAN, input.ball.position, myCar.position);
                    Vector3 location = new Vector3(ballPrediction.slices(ballPrediction.slicesLength() / 10).physics().location());
                    renderer.drawString2d("Nose to ball angle" +input.car.orientation.noseVector.angle(new Vector3(ballPrediction.slices(ballPrediction.slicesLength() / 10).physics().location())), Color.white, new Point(10, 200), 2, 2);
                    renderer.drawString3d("BallChasing!", Color.WHITE, myCar.position, 2, 2);

                            return Steering.steerTowardPosition(input.car, location);

                } catch (IOException e) {

                }
            }

        }

    }
    public boolean towardsMyGoal(DataPacket input){
        try {
            BallPrediction ballPrediction = RLBotDll.getBallPrediction();
            Vector2 towardsGoal = new Vector2(ballPrediction.slices(ballPrediction.slicesLength() / 2).physics().location().x(), ballPrediction.slices(ballPrediction.slicesLength() / 2).physics().location().y());
            Goal myGoal = Goal.getDefending(input.team);
            Vector2 myGoalLocation = myGoal.getCenter().scaled(1000);
            if (towardsGoal == myGoalLocation) {
                return true;
            }
            else {return false;}

        }
        catch(IOException e){return false;}



    }

    public boolean insideOwnGoal(DataPacket input){
        if (input.car.position.flatten().distance(Goal.getDefending(input.team).getCenter()) < 300){
            return true;
        }
        else return false;
    }
    public boolean nearestBigBoost(DataPacket input){

        try {
            BoostManager.loadFieldInfo(RLBotDll.getFieldInfo());
            BoostManager.loadGameTickPacket(RLBotDll.getFlatbufferPacket());
            BoostManager.getFullBoosts();

            for (int index = 0; index < 5; index++) {
                if (input.car.position.flatten().distance(BoostManager.getFullBoosts().get(index).getLocation().flatten()) <
                    input.car.position.flatten().distance(input.ball.position.flatten())
                    && BoostManager.getFullBoosts().get(index).isActive()) {
                    return true;

                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }



    /**
     * This is a nice example of using the rendering feature.
     */
    private void drawDebugLines(DataPacket input, CarData myCar, boolean goLeft) {
        // Here's an example of rendering debug data on the screen.
        Renderer renderer = BotLoopRenderer.forBotLoop(this);

        // Draw a line from the car to the ball
        renderer.drawLine3d(Color.LIGHT_GRAY, myCar.position, input.ball.position);

        // Draw a line that points out from the nose of the car.
        renderer.drawLine3d(goLeft ? Color.BLUE : Color.RED,
                myCar.position.plus(myCar.orientation.noseVector.scaled(150)),
                myCar.position.plus(myCar.orientation.noseVector.scaled(300)));



        for (DropshotTile tile: DropshotTileManager.getTiles()) {
            if (tile.getState() == DropshotTileState.DAMAGED) {
                renderer.drawCenteredRectangle3d(Color.YELLOW, tile.getLocation(), 4, 4, true);
            } else if (tile.getState() == DropshotTileState.DESTROYED) {
                renderer.drawCenteredRectangle3d(Color.RED, tile.getLocation(), 4, 4, true);
            }
        }

        // Draw a rectangle on the tile that the car is on
        DropshotTile tile = DropshotTileManager.pointToTile(myCar.position.flatten());
        if (tile != null) renderer.drawCenteredRectangle3d(Color.green, tile.getLocation(), 8, 8, false);
    }


    @Override
    public int getIndex() {
        return this.playerIndex;
    }

    /**
     * This is the most important function. It will automatically get called by the framework with fresh data
     * every frame. Respond with appropriate controls!
     */
    @Override
    public ControllerState processInput(GameTickPacket packet) {

        if (packet.playersLength() <= playerIndex || packet.ball() == null || !packet.gameInfo().isRoundActive()) {
            // Just return immediately if something looks wrong with the data. This helps us avoid stack traces.
            return new ControlsOutput();
        }

        // Update the boost manager and tile manager with the latest data
        BoostManager.loadGameTickPacket(packet);
        BoostManager.getSmallBoosts();
        DropshotTileManager.loadGameTickPacket(packet);

        // Translate the raw packet data (which is in an unpleasant format) into our custom DataPacket class.
        // The DataPacket might not include everything from GameTickPacket, so improve it if you need to!
        DataPacket dataPacket = new DataPacket(packet, playerIndex);

        // Do the actual logic using our dataPacket.
        ControlsOutput controlsOutput = processInput(dataPacket);

        return controlsOutput;
    }

    public void retire() {
        System.out.println("Retiring sample bot " + playerIndex);
    }
}
