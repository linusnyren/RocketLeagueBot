package rlbotexample;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.BallPrediction;
import rlbot.flat.QuickChatSelection;
import rlbot.manager.BotLoopRenderer;
import rlbot.render.Renderer;
import rlbotexample.input.CarData;
import rlbotexample.output.ControlsOutput;
import rlbotexample.vector.Vector2;
import rlbotexample.vector.Vector3;

import java.awt.*;
import java.io.IOException;

public class Steering {


    public static ControlsOutput steerTowardPosition(CarData car, Vector3 position) {

        Vector2 carPosition = car.position.flatten();
        Vector2 carDirection = car.orientation.noseVector.flatten();
        Vector2 targetPosition = position.flatten();

        // Subtract the two positions to get a vector pointing from the car to the ball.
        Vector2 carToTarget = targetPosition.minus(carPosition);

        // How far does the car need to rotate before it's pointing exactly at the ball?
        double steerCorrectionRadians = carDirection.correctionAngle(carToTarget);



        boolean facingPostition = car.orientation.noseVector.angle(position) < 1.6;
        if (facingPostition){

            return new ControlsOutput()

                    .withSteer((float) (-steerCorrectionRadians * 2))
                    .withThrottle(1)
                    .withBoost();
        }
        else {
            return new ControlsOutput()
                    .withSteer((float) (-steerCorrectionRadians * 2))
                    .withThrottle(1)
                    ;
        }
    }

    public static ControlsOutput steerTowardAppointment(CarData car, Vector3 position, double arrivalTime) {

        Vector2 carPosition = car.position.flatten();
        Vector2 carDirection = car.orientation.noseVector.flatten();
        Vector2 targetPosition = position.flatten();

        // Subtract the two positions to get a vector pointing from the car to the ball.
        Vector2 carToTarget = targetPosition.minus(carPosition);

        // How far does the car need to rotate before it's pointing exactly at the ball?
        double steerCorrectionRadians = carDirection.correctionAngle(carToTarget);

        double distanceToTarget = carToTarget.magnitude();

        double averageSpeedNeeded = distanceToTarget / (arrivalTime - car.elapsedSeconds);
        double currentSpeed = car.velocity.magnitude();
        double throttle = currentSpeed > averageSpeedNeeded ? 0.0 : 1.0;

            boolean facingPosition = car.orientation.noseVector.flatten() == position.flatten();

            if (facingPosition) {
                return new ControlsOutput()
                        .withSteer((float) (-steerCorrectionRadians * 2))
                        .withThrottle((float) throttle)
                        .withBoost();
            } else {
                return new ControlsOutput()
                        .withSteer((float) (-steerCorrectionRadians * 2))
                        .withThrottle((float) throttle);
            }


    }


}
